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
import java.util.Objects;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawledPage;
import com.github.greenfinger.core.model.Image;
import com.github.greenfinger.core.model.Resource;
import com.github.greenfinger.core.model.ResourceImage;
import com.github.greenfinger.core.output.FileLayout;
import com.github.greenfinger.core.record.ResourceRecord;
import com.github.greenfinger.core.record.ResourceRecordStore;
import lombok.extern.slf4j.Slf4j;

/**
 * Copies rows to the other nodes, for the databases where a row written here does not exist there.
 * SQLite and H2 in file or memory mode give every process its own; the server databases are one
 * every node dials, where copying would write each row twice. {@code StoreType} decides, read off
 * the jdbc url because that is where the distinction is recorded.
 *
 * <p>
 * Three rows travel, not one: the resource, its images, and the references carrying alt text and
 * surrounding words, because the index and vectors are built from all three.
 *
 * <p>
 * Applied when absent or different, never blindly -- delivery is at least once, but a refresh
 * genuinely changes a row, so "skip if present" would strand peers on the old content. Image rows
 * test existence (the id is the hash of the bytes) and resource rows compare (the id is the url).
 * 
 * @Description: ReplicatedRecordStore
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class ReplicatedRecordStore implements ResourceRecordStore {

    public static final byte OP_RESOURCE = 10;
    public static final byte OP_IMAGE = 11;
    public static final byte OP_RESOURCE_IMAGE = 12;
    public static final byte OP_DELETE_VERSION = 13;
    public static final byte OP_DELETE_CATALOG = 14;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ResourceRecordStore delegate;
    private final ReplicationSink channel;

    public ReplicatedRecordStore(ResourceRecordStore delegate, ReplicationSink channel) {
        this.delegate = delegate;
        this.channel = channel;
    }

    @Override
    public ResourceRecord save(CatalogDetails catalogDetails, CrawledPage page, FileLayout layout)
            throws Exception {
        ResourceRecord record = delegate.save(catalogDetails, page, layout);
        String scope = catalogDetails.getId();
        send(OP_RESOURCE, scope, record.resource().getId(), record.resource());
        for (ResourceRecord.ImageRecord image : record.images()) {
            send(OP_IMAGE, scope, image.image().getId(), image.image());
            send(OP_RESOURCE_IMAGE, scope, image.reference().getId(), image.reference());
        }
        return record;
    }

    @Override
    public long deleteByCatalogAndVersion(String catalogId, int version) {
        long deleted = delegate.deleteByCatalogAndVersion(catalogId, version);
        // a delete that is not replicated leaves the other nodes serving rows whose files are gone
        channel.replicate(ReplicationBatch.Entry.of(OP_DELETE_VERSION, catalogId,
                String.valueOf(version)));
        return deleted;
    }

    @Override
    public long deleteByCatalog(String catalogId) {
        long deleted = delegate.deleteByCatalog(catalogId);
        channel.replicate(ReplicationBatch.Entry.of(OP_DELETE_CATALOG, catalogId, catalogId));
        return deleted;
    }

    private void send(byte op, String scope, String key, Object entity) {
        try {
            channel.replicate(ReplicationBatch.Entry.of(op, scope, key,
                    OBJECT_MAPPER.writeValueAsBytes(entity)));
        } catch (Exception e) {
            log.warn("Could not replicate {} '{}': {}", op, key, e.getMessage());
        }
    }

    /**
     * Applies one row from another node. Given the repositories directly rather than the store,
     * because what arrives is already a row -- running it back through {@code save} would derive
     * the ids all over again and, worse, announce it a second time.
     */
    public static void apply(ReplicationBatch.Entry entry, RowWriter rows) {
        try {
            switch (entry.op()) {
                case OP_RESOURCE -> rows.resource(
                        OBJECT_MAPPER.readValue(entry.value(), Resource.class));
                case OP_IMAGE -> rows.image(OBJECT_MAPPER.readValue(entry.value(), Image.class));
                case OP_RESOURCE_IMAGE -> rows.reference(
                        OBJECT_MAPPER.readValue(entry.value(), ResourceImage.class));
                case OP_DELETE_VERSION -> rows.deleteVersion(entry.scope(),
                        Integer.parseInt(entry.key()));
                case OP_DELETE_CATALOG -> rows.deleteCatalog(entry.scope());
                default -> log.debug("Unknown record op: {}", entry.op());
            }
        } catch (Exception e) {
            log.warn("Could not apply record {} '{}': {}", entry.op(), entry.key(),
                    e.getMessage());
        }
    }

    /**
     * What applying a replicated row needs. An interface so that the check-then-write rule lives
     * in one place and the repositories stay in the module that owns them.
     * 
     * @Description: RowWriter
     * @Author: Fred Feng
     * @Date: 02/09/2026
     * @Version 2.0.0
     */
    public interface RowWriter {

        void resource(Resource resource);

        void image(Image image);

        void reference(ResourceImage reference);

        void deleteVersion(String catalogId, int version);

        void deleteCatalog(String catalogId);
    }

    /** True when the stored row says something different from the one that arrived. */
    public static boolean differs(Resource stored, Resource incoming) {
        return stored == null || !Objects.equals(stored.getContentHash(), incoming.getContentHash())
                || !Objects.equals(stored.getTitle(), incoming.getTitle())
                || !Objects.equals(stored.getEtag(), incoming.getEtag())
                || !Objects.equals(stored.getHttpLastModified(), incoming.getHttpLastModified());
    }

    // ---- everything else is a read, and reads are local -------------------------------------

    @Override
    public Optional<ResourceRecord> load(String resourceId) {
        return delegate.load(resourceId);
    }

    @Override
    public Optional<String> findContentHash(String catalogId, int version, String urlHash) {
        return delegate.findContentHash(catalogId, version, urlHash);
    }

    @Override
    public Optional<PageState> findPageState(String catalogId, int version, String urlHash) {
        return delegate.findPageState(catalogId, version, urlHash);
    }

    @Override
    public List<ResourceRecord> load(String catalogId, int version, int offset, int limit) {
        return delegate.load(catalogId, version, offset, limit);
    }

    @Override
    public Optional<String> getLatestReferencePath(String catalogId, int version) {
        return delegate.getLatestReferencePath(catalogId, version);
    }

    @Override
    public long countByCatalog(String catalogId, int version) {
        return delegate.countByCatalog(catalogId, version);
    }

    @Override
    public long countImagesByCatalog(String catalogId, int version) {
        return delegate.countImagesByCatalog(catalogId, version);
    }

    @Override
    public List<Integer> findVersions(String catalogId) {
        return delegate.findVersions(catalogId);
    }

}
