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

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.github.greenfinger.cluster.support.CapturingSink;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.engine.CrawledPage;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.Image;
import com.github.greenfinger.core.model.Resource;
import com.github.greenfinger.core.model.ResourceImage;
import com.github.greenfinger.core.output.BlobStore;
import com.github.greenfinger.core.output.FileLayout;
import com.github.greenfinger.core.record.ResourceRecord;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.output.blob.LocalBlobStore;

/**
 * The three stores that have to be copied when the database and the files are per node.
 *
 * <p>
 * Two questions run through all of them. What gets announced -- and, more interesting, what does
 * not, because a decorator that announces what it was told produces an echo. And what happens when
 * the same thing arrives twice, which it will: delivery is at least once.
 * 
 * @Description: ReplicatedStoresTest
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
class ReplicatedStoresTest {

    private final CapturingSink sink = new CapturingSink();

    // ---- blobs --------------------------------------------------------------------------------

    @Test
    void aPageWrittenHereIsAnnouncedWithItsBytes(@TempDir Path root) throws Exception {
        BlobStore blobStore = store(root);
        ReplicatedBlobStore replicated = new ReplicatedBlobStore(blobStore, sink);

        replicated.write("books/v0/pages/ab/cd/x.html", new byte[] {1, 2, 3}, "text/html");

        assertThat(sink.keys()).containsExactly("books/v0/pages/ab/cd/x.html");
        assertThat(sink.entries().get(0).op()).isEqualTo(ReplicatedBlobStore.OP_WRITE);
        assertThat(sink.entries().get(0).value()).containsExactly(1, 2, 3);
        // the content type travels in the scope slot, so the receiver writes it back correctly
        assertThat(sink.entries().get(0).scope()).isEqualTo("text/html");
    }

    @Test
    @DisplayName("a file that is already here is not written again")
    void appliedTwiceWritesOnce(@TempDir Path root) throws Exception {
        BlobStore blobStore = store(root);
        ReplicationBatch.Entry entry = ReplicationBatch.Entry.of(ReplicatedBlobStore.OP_WRITE,
                "image/jpeg", "books/v0/images/a.jpg", new byte[] {9, 9});

        ReplicatedBlobStore.apply(entry, blobStore);
        assertThat(blobStore.readBytes("books/v0/images/a.jpg")).contains(new byte[] {9, 9});

        // the same frame again, as a lost acknowledgement would produce
        ReplicatedBlobStore.apply(entry, blobStore);
        assertThat(blobStore.readBytes("books/v0/images/a.jpg")).contains(new byte[] {9, 9});
    }

    @Test
    void deletingAVersionIsAnnouncedAsAPrefix(@TempDir Path root) throws Exception {
        BlobStore blobStore = store(root);
        blobStore.write("books/v0/pages/a.html", new byte[] {1}, "text/html");
        ReplicatedBlobStore replicated = new ReplicatedBlobStore(blobStore, sink);

        replicated.deletePrefix("books/v0");

        assertThat(sink.ops()).containsExactly(ReplicatedBlobStore.OP_DELETE_PREFIX);
        assertThat(sink.keys()).containsExactly("books/v0");

        ReplicatedBlobStore.apply(sink.entries().get(0), blobStore);
        assertThat(blobStore.listPrefix("books/v0")).isEmpty();
    }

    @Test
    void readsAndTextWritesPassThrough(@TempDir Path root) throws Exception {
        ReplicatedBlobStore replicated = new ReplicatedBlobStore(store(root), sink);
        replicated.afterPropertiesSet();

        replicated.writeText("books/v0/settings.json", "{}");

        assertThat(replicated.readText("books/v0/settings.json")).contains("{}");
        assertThat(replicated.exists("books/v0/settings.json")).isTrue();
        assertThat(replicated.listPrefix("books/v0")).hasSize(1);
        assertThat(replicated.sizeOfPrefix("books/v0")).isEqualTo(2);
        assertThat(replicated.getName()).startsWith("replicated:");
        replicated.destroy();
    }

    @Test
    void anUnknownBlobOpIsIgnored(@TempDir Path root) throws Exception {
        BlobStore blobStore = store(root);
        ReplicatedBlobStore.apply(ReplicationBatch.Entry.of((byte) 99, "", "x"), blobStore);
        assertThat(blobStore.listPrefix("")).isEmpty();
    }

    // ---- rows ---------------------------------------------------------------------------------

    @Test
    @DisplayName("a page saved here sends its resource, its images and the references between")
    void savingSendsAllThreeKinds() throws Exception {
        RecordingRecordStore delegate = new RecordingRecordStore();
        ReplicatedRecordStore replicated = new ReplicatedRecordStore(delegate, sink);

        replicated.save(catalogDetails(), new CrawledPage(), layout());

        assertThat(sink.ops()).containsExactly(ReplicatedRecordStore.OP_RESOURCE,
                ReplicatedRecordStore.OP_IMAGE, ReplicatedRecordStore.OP_RESOURCE_IMAGE);
        assertThat(sink.entries().get(0).scope()).isEqualTo("cat-1");
    }

    @Test
    void deletingAVersionIsAnnounced() {
        ReplicatedRecordStore replicated = new ReplicatedRecordStore(new RecordingRecordStore(),
                sink);

        replicated.deleteByCatalogAndVersion("cat-1", 2);

        assertThat(sink.ops()).containsExactly(ReplicatedRecordStore.OP_DELETE_VERSION);
        assertThat(sink.entries().get(0).key()).isEqualTo("2");
    }

    @Test
    @DisplayName("a row that says the same thing is not rewritten; a refreshed one is")
    void differsIsWhatDecidesAnUpdate() {
        Resource stored = new Resource();
        stored.setContentHash("abc");
        stored.setTitle("A");
        Resource same = new Resource();
        same.setContentHash("abc");
        same.setTitle("A");
        Resource changed = new Resource();
        changed.setContentHash("def");
        changed.setTitle("A");

        assertThat(ReplicatedRecordStore.differs(stored, same)).isFalse();
        assertThat(ReplicatedRecordStore.differs(stored, changed)).isTrue();
        assertThat(ReplicatedRecordStore.differs(null, same)).isTrue();
    }

    @Test
    void applyingRoutesEachKindToItsWriter() throws Exception {
        RecordingRows rows = new RecordingRows();
        RecordingRecordStore delegate = new RecordingRecordStore();
        new ReplicatedRecordStore(delegate, sink).save(catalogDetails(), new CrawledPage(),
                layout());

        for (ReplicationBatch.Entry entry : sink.entries()) {
            ReplicatedRecordStore.apply(entry, rows);
        }
        ReplicatedRecordStore.apply(ReplicationBatch.Entry.of(
                ReplicatedRecordStore.OP_DELETE_VERSION, "cat-1", "3"), rows);
        ReplicatedRecordStore.apply(ReplicationBatch.Entry.of((byte) 99, "cat-1", "x"), rows);

        assertThat(rows.resources).hasSize(1);
        assertThat(rows.images).hasSize(1);
        assertThat(rows.references).hasSize(1);
        assertThat(rows.deleted).containsExactly("cat-1:3");
    }

    @Test
    void readsPassThroughToTheDelegate() {
        RecordingRecordStore delegate = new RecordingRecordStore();
        ReplicatedRecordStore replicated = new ReplicatedRecordStore(delegate, sink);

        assertThat(replicated.load("id")).isEmpty();
        assertThat(replicated.findContentHash("c", 0, "h")).isEmpty();
        assertThat(replicated.findPageState("c", 0, "h")).isEmpty();
        assertThat(replicated.load("c", 0, 0, 10)).isEmpty();
        assertThat(replicated.getLatestReferencePath("c", 0)).isEmpty();
        assertThat(replicated.countByCatalog("c", 0)).isZero();
        assertThat(replicated.countImagesByCatalog("c", 0)).isZero();
        assertThat(replicated.findVersions("c")).isEmpty();
    }

    // ---- catalogs -----------------------------------------------------------------------------

    @Test
    @DisplayName("a definition is announced on every change, because a node that has not heard of"
            + " a catalog cannot join its crawl")
    void catalogChangesAreAnnounced() {
        MemoryCatalogStore delegate = new MemoryCatalogStore();
        ReplicatedCatalogStore replicated = new ReplicatedCatalogStore(delegate, sink);

        Catalog catalog = new Catalog();
        catalog.setId("cat-1");
        catalog.setName("books");
        replicated.save(catalog);
        replicated.setRunningState("cat-1", "crawl");
        replicated.publishSearchVersion("cat-1", 0);
        replicated.incrementIndexVersion("cat-1");

        assertThat(sink.ops()).containsOnly(ReplicatedCatalogStore.OP_CATALOG);
        assertThat(sink.entries()).hasSize(4);
    }

    @Test
    void applyingACatalogIsIdempotent() {
        MemoryCatalogStore delegate = new MemoryCatalogStore();
        ReplicatedCatalogStore replicated = new ReplicatedCatalogStore(delegate, sink);
        Catalog catalog = new Catalog();
        catalog.setId("cat-1");
        catalog.setName("books");
        replicated.save(catalog);

        MemoryCatalogStore other = new MemoryCatalogStore();
        ReplicatedCatalogStore.apply(sink.entries().get(0), other);
        int writesAfterFirst = other.writes;
        ReplicatedCatalogStore.apply(sink.entries().get(0), other);

        assertThat(other.findById("cat-1")).isPresent();
        assertThat(other.writes).isEqualTo(writesAfterFirst);
    }

    @Test
    void deletingACatalogIsAnnouncedAndApplied() {
        MemoryCatalogStore delegate = new MemoryCatalogStore();
        Catalog catalog = new Catalog();
        catalog.setId("cat-1");
        delegate.save(catalog);
        ReplicatedCatalogStore replicated = new ReplicatedCatalogStore(delegate, sink);

        assertThat(replicated.deleteById("cat-1")).isTrue();
        assertThat(sink.ops()).containsExactly(ReplicatedCatalogStore.OP_CATALOG_DELETE);

        MemoryCatalogStore other = new MemoryCatalogStore();
        other.save(catalog);
        ReplicatedCatalogStore.apply(sink.entries().get(0), other);
        assertThat(other.findById("cat-1")).isEmpty();
    }

    @Test
    void catalogReadsPassThrough() {
        MemoryCatalogStore delegate = new MemoryCatalogStore();
        Catalog catalog = new Catalog();
        catalog.setId("cat-1");
        catalog.setName("books");
        catalog.setCat("other");
        delegate.save(catalog);
        ReplicatedCatalogStore replicated = new ReplicatedCatalogStore(delegate, sink);

        assertThat(replicated.findByName("books")).isPresent();
        assertThat(replicated.findAll()).hasSize(1);
        assertThat(replicated.findAllCategories()).containsExactly("other");
        assertThat(replicated.findRunning()).isEmpty();
        assertThat(replicated.getName()).startsWith("replicated:");
    }

    // ---- fixtures -----------------------------------------------------------------------------

    private static BlobStore store(Path root) throws Exception {
        LocalBlobStore blobStore = new LocalBlobStore(root);
        blobStore.afterPropertiesSet();
        return blobStore;
    }

    private static FileLayout layout() {
        return new FileLayout("books", 0, 2);
    }

    private static CatalogDetails catalogDetails() {
        Catalog catalog = new Catalog();
        catalog.setId("cat-1");
        catalog.setName("books");
        catalog.setUrl("https://books.toscrape.com");
        catalog.setIndexVersion(0);
        return new com.github.greenfinger.core.catalog.CatalogDetailsImpl(catalog,
                new com.github.greenfinger.core.WebCrawlerProperties());
    }

    /** Hands back one resource with one image, which is the shape the decorator has to fan out. */
    private static class RecordingRecordStore implements ResourceRecordStore {

        @Override
        public ResourceRecord save(CatalogDetails catalogDetails, CrawledPage page,
                FileLayout layout) {
            Resource resource = new Resource();
            resource.setId("res-1");
            Image image = new Image();
            image.setId("img-1");
            ResourceImage reference = new ResourceImage();
            reference.setId("ref-1");
            return new ResourceRecord(resource,
                    List.of(new ResourceRecord.ImageRecord(image, reference)));
        }

        @Override
        public Optional<ResourceRecord> load(String resourceId) {
            return Optional.empty();
        }

        @Override
        public Optional<String> findContentHash(String catalogId, int version, String urlHash) {
            return Optional.empty();
        }

        @Override
        public Optional<PageState> findPageState(String catalogId, int version, String urlHash) {
            return Optional.empty();
        }

        @Override
        public List<ResourceRecord> load(String catalogId, int version, int offset, int limit) {
            return List.of();
        }

        @Override
        public Optional<String> getLatestReferencePath(String catalogId, int version) {
            return Optional.empty();
        }

        @Override
        public long countByCatalog(String catalogId, int version) {
            return 0;
        }

        @Override
        public long countImagesByCatalog(String catalogId, int version) {
            return 0;
        }

        @Override
        public List<Integer> findVersions(String catalogId) {
            return List.of();
        }

        @Override
        public long deleteByCatalog(String catalogId) {
            return 0;
        }

        @Override
        public long deleteByCatalogAndVersion(String catalogId, int version) {
            return 0;
        }
    }

    /**
     * 
     * @Description: RecordingRows
     * @Author: Fred Feng
     * @Date: 02/09/2026
     * @Version 2.0.0
     */
    private static class RecordingRows implements ReplicatedRecordStore.RowWriter {

        private final List<Resource> resources = new ArrayList<>();
        private final List<Image> images = new ArrayList<>();
        private final List<ResourceImage> references = new ArrayList<>();
        private final List<String> deleted = new ArrayList<>();

        @Override
        public void resource(Resource resource) {
            resources.add(resource);
        }

        @Override
        public void image(Image image) {
            images.add(image);
        }

        @Override
        public void reference(ResourceImage reference) {
            references.add(reference);
        }

        @Override
        public void deleteCatalog(String catalogId) {}

        @Override
        public void deleteVersion(String catalogId, int version) {
            deleted.add(catalogId + ":" + version);
        }
    }

    /**
     * 
     * @Description: MemoryCatalogStore
     * @Author: Fred Feng
     * @Date: 02/09/2026
     * @Version 2.0.0
     */
    private static class MemoryCatalogStore implements CatalogStore {

        private final Map<String, Catalog> catalogs = new LinkedHashMap<>();
        private int writes;

        @Override
        public String getName() {
            return "memory";
        }

        @Override
        public Catalog save(Catalog catalog) {
            writes++;
            catalog.setUpdatedAt(new Date());
            catalogs.put(catalog.getId(), catalog);
            return catalog;
        }

        @Override
        public Optional<Catalog> findById(String id) {
            return Optional.ofNullable(catalogs.get(id));
        }

        @Override
        public Optional<Catalog> findByName(String name) {
            return catalogs.values().stream().filter(c -> name.equalsIgnoreCase(c.getName()))
                    .findFirst();
        }

        @Override
        public List<Catalog> findAll() {
            return List.copyOf(catalogs.values());
        }

        @Override
        public List<String> findAllCategories() {
            return catalogs.values().stream().map(Catalog::getCat).filter(java.util.Objects::nonNull)
                    .distinct().sorted().toList();
        }

        @Override
        public boolean deleteById(String id) {
            return catalogs.remove(id) != null;
        }

        @Override
        public int incrementIndexVersion(String id) {
            return 1;
        }

        @Override
        public void publishSearchVersion(String id, int version) {
            // nothing beyond existing, which is what the decorator is asked about
        }

        @Override
        public void resetVersions(String id) {
            Catalog catalog = catalogs.get(id);
            if (catalog != null) {
                catalog.setIndexVersion(0);
                catalog.setSearchVersion(-1);
            }
        }

        @Override
        public void setRunningState(String id, String runningState) {
            findById(id).ifPresent(c -> c.setRunningState(runningState));
        }

        @Override
        public List<Catalog> findRunning() {
            return List.of();
        }
    }

}
