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

package com.github.greenfinger.cluster.replication;

import java.util.List;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.model.Catalog;
import lombok.extern.slf4j.Slf4j;

/**
 * Copies the catalog definitions, which are the one thing every node needs before it can do
 * anything at all.
 *
 * <p>
 * This was the first thing to go wrong in a three node run and it went wrong invisibly: the crawl
 * started on one node, that node told the others, and the others had never heard of the catalog,
 * so they could not open their half of it. The seed was then dispatched round robin to a node with
 * no frontier for it. Nothing failed -- the counters simply read one url dispatched, none handled,
 * for as long as anybody cared to watch.
 *
 * <p>
 * The rows are small and change rarely: a definition edited by hand, a running state, a version
 * being promoted. Unlike a resource row, every field of one can change, so what arrives is applied
 * whenever it differs at all rather than on a comparison of particular columns.
 * 
 * @Description: ReplicatedCatalogStore
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class ReplicatedCatalogStore implements CatalogStore {

    public static final byte OP_CATALOG = 30;
    public static final byte OP_CATALOG_DELETE = 31;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CatalogStore delegate;
    private final ReplicationSink channel;

    public ReplicatedCatalogStore(CatalogStore delegate, ReplicationSink channel) {
        this.delegate = delegate;
        this.channel = channel;
    }

    @Override
    public String getName() {
        return "replicated:" + delegate.getName();
    }

    @Override
    public Catalog save(Catalog catalog) {
        return announce(delegate.save(catalog));
    }

    @Override
    public boolean deleteById(String id) {
        boolean deleted = delegate.deleteById(id);
        if (deleted) {
            channel.replicate(ReplicationBatch.Entry.of(OP_CATALOG_DELETE, id, id));
        }
        return deleted;
    }

    @Override
    public int incrementIndexVersion(String id) {
        int version = delegate.incrementIndexVersion(id);
        delegate.findById(id).ifPresent(this::announce);
        return version;
    }

    @Override
    public void publishSearchVersion(String id, int version) {
        delegate.publishSearchVersion(id, version);
        delegate.findById(id).ifPresent(this::announce);
    }

    @Override
    public void resetVersions(String id) {
        delegate.resetVersions(id);
        // the version numbers are what every node's searches and crawls are addressed by, so a
        // node that missed this would go on serving a version that no longer exists anywhere
        delegate.findById(id).ifPresent(this::announce);
    }

    @Override
    public void setRunningState(String id, String runningState) {
        delegate.setRunningState(id, runningState);
        // every node reads this to answer "is a crawl running", which is how a second one is
        // refused -- so a stale copy would let two crawls start
        delegate.findById(id).ifPresent(this::announce);
    }

    private Catalog announce(Catalog catalog) {
        try {
            channel.replicate(ReplicationBatch.Entry.of(OP_CATALOG, catalog.getId(),
                    catalog.getId(), OBJECT_MAPPER.writeValueAsBytes(catalog)));
        } catch (Exception e) {
            log.warn("Could not replicate catalog '{}': {}", catalog.getName(), e.getMessage());
        }
        return catalog;
    }

    /** Applies a definition from another node, straight to the plain store. */
    public static void apply(ReplicationBatch.Entry entry, CatalogStore plain) {
        try {
            switch (entry.op()) {
                case OP_CATALOG -> {
                    Catalog incoming = OBJECT_MAPPER.readValue(entry.value(), Catalog.class);
                    Catalog stored = plain.findById(incoming.getId()).orElse(null);
                    if (stored == null || !sameAs(stored, incoming)) {
                        plain.save(incoming);
                    }
                }
                case OP_CATALOG_DELETE -> plain.deleteById(entry.key());
                default -> log.debug("Unknown catalog op: {}", entry.op());
            }
        } catch (Exception e) {
            log.warn("Could not apply catalog '{}': {}", entry.key(), e.getMessage());
        }
    }

    /**
     * Compared as json rather than field by field: a catalog has thirty of them and a comparison
     * that forgets one goes wrong quietly, which is the failure this whole class exists to fix.
     *
     * <p>
     * Except {@code createdAt} and {@code updatedAt}, which are when the row was written rather
     * than anything about it. Every save stamps {@code updatedAt} afresh, so including it would
     * make every copy differ from every other and the check would never once say "already have
     * this" -- which is exactly what it is for, since delivery is at least once.
     */
    private static boolean sameAs(Catalog stored, Catalog incoming) {
        try {
            return withoutWriteStamp(stored).equals(withoutWriteStamp(incoming));
        } catch (Exception e) {
            return false;
        }
    }

    private static com.fasterxml.jackson.databind.JsonNode withoutWriteStamp(Catalog catalog) {
        com.fasterxml.jackson.databind.node.ObjectNode node =
                OBJECT_MAPPER.valueToTree(catalog);
        node.remove("createdAt");
        node.remove("updatedAt");
        return node;
    }

    // ---- reads are local ----------------------------------------------------------------------

    @Override
    public Optional<Catalog> findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public Optional<Catalog> findByName(String name) {
        return delegate.findByName(name);
    }

    @Override
    public List<Catalog> findAll() {
        return delegate.findAll();
    }

    @Override
    public List<String> findAllCategories() {
        return delegate.findAllCategories();
    }

    @Override
    public List<Catalog> findRunning() {
        return delegate.findRunning();
    }

}
