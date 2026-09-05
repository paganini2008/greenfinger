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

/**
 * Rebuilds an index or a vector collection using every node, rather than one.
 *
 * <p>
 * This is the one operation in greenfinger with a genuine scatter-gather shape, and it is worth
 * saying why the crawl is not. A crawl is a recursion of unknown depth and unknown breadth whose
 * every step writes to four places; there is nothing to gather and re-running a step is not free.
 * A replay is the opposite on all four counts: the work is a known number of pages, it fans out
 * once, the answer is a sum, and re-running a slice is harmless because every id downstream is a
 * name-based UUID of a natural key -- the second pass lands on top of the first.
 *
 * <p>
 * That last property is what makes the framework's own recovery usable: a slice sent to a node
 * that stops answering is simply done again somewhere else, and the only cost is the time.
 *
 * <h2>Why slices rather than one range per node</h2>
 * Two or three slices per node rather than exactly one. Nodes are not equally fast -- one may be
 * crawling, or on a slower disk -- and with one slice each the whole replay waits for the slowest.
 * With several, the round robin keeps handing work out and a slow node simply gets fewer.
 *
 * <h2>What each node needs to have</h2>
 * The pages themselves, because a replay reads the extracted text back from the blob store rather
 * than re-fetching it. Both arrangements provide that: MinIO because it is shared, and a local
 * directory because its bytes are replicated. Nothing here has to know which.
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
     * The most pages one slice may carry, whatever the catalog's size.
     *
     * <p>
     * This is about the timeout, not the balancing. A slice is one method call and the pool waits
     * a bounded time for its answer, so a slice has to be small enough to finish inside that wait
     * -- and "the total divided by the nodes" does the opposite, growing each slice as the catalog
     * grows until a large enough replay times out every one of them. Two hundred pages is a few
     * tens of seconds of embedding, which is where a replay's time actually goes.
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
    private java.util.function.BiConsumer<String, Integer> announcer;

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

        // The file layer is the one part of a replay that is not shared work, so it is not sliced
        // and handed round: every node keeps its own full copy of the files, and what is missing
        // is a different set on each of them. A node given a slice would look at its own store,
        // find it complete, and report nothing to do -- while the node that actually lost the
        // files was never asked. So each node is told to repair itself, and one that has nothing
        // missing sends no requests at all.
        Set<OutputType> rest = new java.util.LinkedHashSet<>(layers);
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
        // The slices each rebuilt their own part and none of them is in a position to decide the
        // catalog is whole again -- so the node that split the work is the one that says so, once
        // every slice has come back. Without this a replay spread across the cluster left the
        // index complete and search still returning nothing, while the same replay on one node
        // published perfectly well.
        publishReplayed(catalogId, version, layers, replayed);
        return replayed;
    }

    /**
     * One slice's answer, or that slice done here instead.
     *
     * <p>
     * A slice that fails -- the node stopped answering, or took longer than the pool waits -- is
     * not a reason to fail the replay. Doing it locally is always correct because a replay is
     * idempotent: every id downstream is a name-based UUID of a natural key, so if the remote node
     * did in fact finish it after all, this pass lands on top of its work rather than beside it.
     * Slower, and complete, which is the right trade for an operation somebody ran to repair
     * something.
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
     * One slice, wherever it happens to run.
     *
     * <p>
     * {@link MultiProcessingCall} is the whitelist as much as the entry point: without it a node
     * would refuse this request, and with it -- and only it -- a peer may ask this process to run
     * this method. Layers travel as a comma separated string because everything crossing the wire
     * has to serialize, and a name is a more durable thing to send than an enum's ordinal.
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
