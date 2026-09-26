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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.model.Catalog;
import lombok.extern.slf4j.Slf4j;

/**
 * Copies the catalog definitions, which every node needs before it can do anything. Without them a
 * peer cannot open its half of a crawl, and a seed dispatched to it lands on no frontier -- one url
 * dispatched, none handled, and nothing reported as failing.
 *
 * <p>
 * The rows are small and change rarely, and unlike a resource row every field can change, so an
 * arriving copy is applied whenever it differs at all rather than on particular columns.
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
        announceOn(channel, catalog);
        return catalog;
    }

    /**
     * Puts one definition on the wire. Static because the reconciler sends rows it did not write
     * -- it is repairing somebody else's copy, not announcing its own change -- and there is no
     * reason for it to hold a whole store to do that.
     */
    static void announceOn(ReplicationSink channel, Catalog catalog) {
        try {
            channel.replicate(ReplicationBatch.Entry.of(OP_CATALOG, catalog.getId(),
                    catalog.getId(), OBJECT_MAPPER.writeValueAsBytes(catalog)));
        } catch (Exception e) {
            log.warn("Could not replicate catalog '{}': {}", catalog.getName(), e.getMessage());
        }
    }

    /** Applies a definition from the leader, straight to the plain store. */
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
            // Logged and stepped over, which is where this used to end. A row that a node refuses
            // -- a stale one colliding on the unique name index is the case that happened --
            // stayed refused for ever, because nothing came back for it. CatalogCatchUp is what
            // comes back for it now, so this is a delay rather than a loss.
            log.warn("Could not apply catalog '{}': {}. The next catch-up will put it right.",
                    entry.key(), e.getMessage());
        }
    }

    /**
     * Compared as json, not field by field: a catalog has thirty fields and a comparison that
     * forgets one fails quietly. Timestamps are excluded -- every save re-stamps {@code updatedAt},
     * so including it would make every copy differ and never once skip a duplicate delivery.
     */
    public static boolean sameAs(Catalog stored, Catalog incoming) {
        try {
            return withoutWriteStamp(stored).equals(withoutWriteStamp(incoming));
        } catch (Exception e) {
            return false;
        }
    }

    private static JsonNode withoutWriteStamp(Catalog catalog) {
        ObjectNode node = OBJECT_MAPPER.valueToTree(catalog);
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
