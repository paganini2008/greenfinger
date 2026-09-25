/*
 * Copyright 2017-2026 Fred Feng (paganini.fy@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.greenfinger.cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import com.chaconneai.openspreader.pooling.MultiProcessingCall;
import com.chaconneai.openspreader.pooling.ProcessingPool;
import com.github.greenfinger.core.catalog.CatalogDetailsService;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.service.FileRestorer;
import com.github.greenfinger.service.ReplayService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import java.util.LinkedHashSet;
import java.util.function.BiConsumer;

/**
 * Rebuilds an index or a vector collection using every node.
 *
 * <p>
 * The one operation here with a genuine scatter-gather shape: a known number of pages, one
 * fan-out, a sum for an answer, and slices that are harmless to repeat because every id
 * downstream is a name-based UUID -- which is also what makes the framework's own recovery
 * usable. A crawl is the opposite on all four counts.
 *
 * <p>
 * Several slices per node rather than one each, so the replay does not wait for the slowest node.
 * Each node needs the pages, which it has either because MinIO is shared or because a local
 * directory is replicated; nothing here knows which.
 * 
 * @Description: ClusterReplayService
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class ClusterReplayService extends ReplayService {

    /** Below this there is nothing to spread: the round trips would cost more than the work. */
    static final int MIN_PAGES_TO_SPREAD = 50;

    /**
     * The most pages one slice may carry. About the timeout, not the balancing: a slice is one
     * call the pool waits a bounded time for, and "total divided by nodes" grows with the catalog
     * until every slice times out.
     */
    static final int MAX_SLICE_PAGES = 200;

    /** Slices per node. More than one so that a slow node holds up less of the whole. */
    static final int SLICES_PER_NODE = 3;

    private final ResourceRecordStore recordStore;
    private final ProcessingPool pool;
    private final String beanName;

    /**
     * Tells the other nodes to repair their own copies of the files. Set after construction,
     * because the thing that announces is a bean this one is a dependency of.
     */
    @Setter
    private BiConsumer<String, Integer> announcer;

    public ClusterReplayService(OutputFactory outputFactory, ResourceRecordStore recordStore,
            CatalogDetailsService catalogDetailsService, FileRestorer fileRestorer,
            CatalogStore catalogStore, ProcessingPool pool, String beanName) {
        super(outputFactory, recordStore, catalogDetailsService, fileRestorer, catalogStore);
        this.recordStore = recordStore;
        this.pool = pool;
        this.beanName = beanName;
    }

    /**
     * Splits the version's pages and hands the slices out, then adds up what came back.
     *
     * <p>
     * Alone, or with too little to be worth spreading, it does the whole thing here -- which is
     * the same code path the single process has always taken.
     */
    @Override
    public long replay(String catalogId, int version, Set<OutputType> layers) throws Exception {
        long total = recordStore.countByCatalog(catalogId, version);
        int peers = pool.peerCount();

        // Not shared work: each node has its own copy of the files and its own gaps, so a node
        // given a slice would find its store complete while the node that lost files is never
        // asked. Each repairs itself instead.
        Set<OutputType> rest = new LinkedHashSet<>(layers);
        long restored = 0L;
        if (rest.remove(OutputType.FILE)) {
            restored = super.replay(catalogId, version, Set.of(OutputType.FILE));
            if (peers > 0 && announcer != null) {
                announcer.accept(catalogId, version);
            }
            if (rest.isEmpty()) {
                return restored;
            }
            layers = rest;
        }

        if (peers == 0 || total < MIN_PAGES_TO_SPREAD) {
            return super.replay(catalogId, version, layers);
        }

        // between two bounds, and both matter: no bigger than a slice that finishes inside the
        // pool's wait, no smaller than a slice worth a round trip. In between, small enough that
        // every node gets several -- otherwise a catalog like this one would go out as a single
        // slice, correct but with two nodes idle
        int size = (int) Math.ceil((double) total / ((peers + 1) * SLICES_PER_NODE));
        size = Math.min(MAX_SLICE_PAGES, Math.max(MIN_PAGES_TO_SPREAD, size));
        String layerNames = names(layers);
        int slices = (int) Math.ceil((double) total / size);

        log.info("Replaying {} page(s) of catalog {} version {} across {} node(s) in {} slice(s)"
                + " of {}", total, catalogId, version, peers + 1, slices, size);

        List<Integer> offsets = new ArrayList<>(slices);
        List<CompletableFuture<Long>> pending = new ArrayList<>(slices);
        for (int offset = 0; offset < total; offset += size) {
            offsets.add(offset);
            // by bean name and method name: the receiving node resolves its own bean, so nothing
            // of this one travels except five values that are all strings and numbers
            pending.add(pool.submit(beanName, "replayRange", catalogId, version, layerNames,
                    offset, size));
        }

        long replayed = 0L;
        for (int i = 0; i < pending.size(); i++) {
            replayed += collect(pending.get(i), catalogId, version, layers, offsets.get(i), size);
        }
        // No slice can tell the catalog is whole again, so the node that split the work says so
        // once they are all back. Without it a cluster replay left the index complete and search
        // returning nothing.
        publishReplayed(catalogId, version, layers, replayed);
        return replayed;
    }

    /**
     * One slice's answer, or that slice done here instead. A failed slice is not a reason to
     * fail the replay, and redoing it is safe: ids are name-based UUIDs, so a remote node that
     * finished after all is simply written over.
     */
    private long collect(CompletableFuture<Long> slice, String catalogId, int version,
            Set<OutputType> layers, int offset, int size) throws Exception {
        try {
            Long done = slice.join();
            return done == null ? 0L : done;
        } catch (RuntimeException e) {
            log.warn("A slice of catalog {} from offset {} did not come back ({}); doing it here",
                    catalogId, offset, e.getMessage());
            return replaySlice(catalogId, version, layers, offset, size);
        }
    }

    /**
     * One slice, wherever it runs. {@link MultiProcessingCall} is the whitelist as much as the
     * entry point. Layers travel as names rather than ordinals, which survive a reordering.
     */
    @MultiProcessingCall
    public long replayRange(String catalogId, int version, String layers, int offset, int limit)
            throws Exception {
        return replaySlice(catalogId, version, OutputType.parse(layers), offset, limit);
    }

    private static String names(Set<OutputType> layers) {
        return layers.stream().map(OutputType::getRepr).reduce((a, b) -> a + "," + b).orElse("");
    }

}
