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

package com.github.greenfinger.matrix;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsService;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.DeleteLayer;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.output.IndexAdmin;
import com.github.greenfinger.core.output.SearchRequest;
import com.github.greenfinger.core.output.SearchResponse;
import com.github.greenfinger.core.output.Searcher;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.core.utils.BeanLifeCycleUtils;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.vector.VectorStore;
import com.github.greenfinger.service.CatalogAdminService;
import com.github.greenfinger.service.CrawlerLauncher;
import com.github.greenfinger.service.DeletionService;
import com.github.greenfinger.service.LocalSite;

/**
 * The default installation, end to end, once per file-backed database.
 *
 * <p>
 * Everything here is what a fresh clone gets with nothing installed: a database that is a file,
 * a Lucene index, a Lucene vector store, and pages on local disk. That combination is the one
 * most people will ever run, which makes it the one that has to be provably right -- and it is
 * also the one where every store is a copy per process, so it is what the cluster's replication
 * exists to keep in step.
 *
 * <p>
 * Subclassed once for H2 and once for SQLite. The two are the same {@code StoreType} -- a file
 * every process has its own copy of -- and they behave differently enough underneath that testing
 * one proves nothing about the other: SQLite gets no unique constraints at all from its dialect,
 * and its dates and its locking are its own.
 *
 * <h2>The site</h2>
 * A recipe site, shaped like the one this was last run against: an index of recipes linking to
 * pages of ingredients and method. Served from the test rather than fetched, so the assertions
 * are about this application rather than about somebody's uptime.
 *
 * @Description: LocalStoresCrudMatrixTest
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
abstract class LocalStoresCrudMatrixTest {

    @Autowired
    private CrawlerLauncher crawlerLauncher;

    @Autowired
    private CatalogAdminService catalogAdminService;

    @Autowired
    private CatalogDetailsService catalogDetailsService;

    @Autowired
    private DeletionService deletionService;

    @Autowired
    private ResourceRecordStore recordStore;

    @Autowired
    private OutputFactory outputFactory;

    @Autowired
    private OutputProperties outputProperties;

    @Autowired
    private WebCrawlerProperties webCrawlerProperties;

    private LocalSite site;

    @BeforeEach
    void setUp() throws Exception {
        // No wiping of the workspace between tests. The index and the vector store are shared
        // writers held open for the life of the context, and deleting their directories under
        // them leaves the next test writing into files that are no longer the index -- which
        // shows up as a crawl that saves four pages and an index that reports none. Each test
        // uses a catalog of its own and asserts only about that one, so what earlier tests left
        // behind is not in the way.
        site = new LocalSite();
        site.html("/", "<html><head><title>Simple recipes</title></head><body>"
                + "<h1>Simple recipes</h1><p>Weeknight cooking with short ingredient lists.</p>"
                + "<a href='/soup'>Leek and potato soup</a>"
                + "<a href='/bread'>No knead bread</a>"
                + "<a href='/salad'>Winter salad</a></body></html>");
        site.html("/soup", "<html><head><title>Leek and potato soup</title></head><body>"
                + "<p>Leeks, potatoes, stock and butter. Soften the leeks, add the potatoes and"
                + " the stock, simmer until tender, then blend until smooth.</p></body></html>");
        site.html("/bread", "<html><head><title>No knead bread</title></head><body>"
                + "<p>Flour, water, salt and a little yeast. Mix, leave overnight, fold, and bake"
                + " in a hot covered pot until the crust is dark.</p></body></html>");
        site.html("/salad", "<html><head><title>Winter salad</title></head><body>"
                + "<p>Shredded cabbage, apple, toasted walnuts and a mustard dressing. Dress it"
                + " an hour before eating so the cabbage softens.</p></body></html>");
    }

    @AfterEach
    void tearDown() {
        site.close();
    }

    // ---- create ---------------------------------------------------------------------------------

    private Catalog create(String name) {
        Catalog catalog = new Catalog();
        catalog.setName(name);
        catalog.setUrl(site.baseUrl());
        catalog.setStartUrl(site.baseUrl());
        catalog.setPathPattern(site.baseUrl() + "/**");
        catalog.setMaxFetchSize(20);
        catalog.setFetchInterval(0L);
        catalog.setImageEnabled(false);
        // the three that are one copy per process, which is the whole point of this class
        catalog.setOutputTypes(Set.of(OutputType.FILE, OutputType.INDEX, OutputType.VECTOR));
        catalog.setMaxVersions(10);
        return catalogAdminService.save(catalog);
    }

    private CatalogDetails detailsOf(Catalog catalog) {
        return catalogDetailsService.loadCatalogDetails(catalog.getId());
    }

    private long indexed(String catalogId, int version) throws Exception {
        try (IndexAdmin admin = outputFactory.getIndexAdmin()) {
            return admin.countByCatalogVersion(catalogId + ":" + version);
        }
    }

    private boolean indexExists(String catalogId) throws Exception {
        try (IndexAdmin admin = outputFactory.getIndexAdmin()) {
            return admin.indexExists(catalogId);
        }
    }

    /**
     * Vectors are counted across every collection matching the configured prefix, because the
     * width of the vectors is part of the collection's name and the width comes from the model.
     */
    private long vectored(String catalogId, Integer version) throws Exception {
        VectorStore vectorStore = outputFactory.getVectorStore();
        BeanLifeCycleUtils.afterPropertiesSet(vectorStore);
        try {
            long count = 0L;
            for (String collection : collections(vectorStore)) {
                count += version != null
                        ? vectorStore.count(collection, catalogId + ":" + version)
                        : vectorStore.countByCatalog(collection, catalogId);
            }
            return count;
        } finally {
            BeanLifeCycleUtils.destroyQuietly(vectorStore);
        }
    }

    private List<String> collections(VectorStore vectorStore) throws Exception {
        OutputProperties.Vector config = outputProperties.getVector();
        List<String> collections = new ArrayList<>();
        collections.addAll(vectorStore.collectionsMatching(config.getTextCollection()));
        collections.addAll(vectorStore.collectionsMatching(config.getImageCollection()));
        return collections;
    }

    private Path files(String catalogId) {
        return Path.of(outputProperties.getFile().getDirectory(), catalogId);
    }

    /** The three RocksDB directories a crawl keeps, which go with the rows. */
    private List<Path> state(String catalogId, int version) {
        String scope = catalogId + "/v" + version;
        return List.of(Path.of(webCrawlerProperties.getFrontierDirectory(), scope),
                Path.of(webCrawlerProperties.getDedup().getUrl().getDirectory(), scope),
                Path.of(webCrawlerProperties.getDedup().getContent().getDirectory(), scope));
    }

    @Test
    @DisplayName("create: a crawl writes to the database, the files, the index and the vectors")
    void createsEverywhere() throws Exception {
        Catalog catalog = create("matrix-create");

        var result = crawlerLauncher.crawl(catalog.getId(), null);
        System.out.println("DIAG reason=" + result.getReason() + " remaining=" + result.getRemaining()
                + " saved=" + result.getDashboard().getSavedResourceCount()
               
                + " invalid=" + result.getDashboard().getInvalidUrlCount()
                + " filtered=" + result.getDashboard().getFilteredUrlCount());

        String id = catalog.getId();
        assertThat(recordStore.countByCatalog(id, 0)).isPositive();
        assertThat(files(id)).isDirectory();
        assertThat(indexed(id, 0)).isPositive();
        assertThat(vectored(id, 0)).isPositive();
        assertThat(state(id, 0)).allSatisfy(path -> assertThat(path).isDirectory());
        // published, which is what makes the version searchable
        assertThat(catalogAdminService.require("matrix-create").getSearchVersion()).isZero();
    }

    // ---- read -----------------------------------------------------------------------------------

    @Test
    @DisplayName("read: the words and the vectors both find the page they belong to")
    void readsBackWhatItWrote() throws Exception {
        Catalog catalog = create("matrix-read");
        crawlerLauncher.crawl(catalog.getId(), null);

        Searcher searcher = outputFactory.getSearcher();
        BeanLifeCycleUtils.afterPropertiesSet(searcher);
        try {
            SearchResponse response =
                    searcher.search(SearchRequest.builder().keyword("leeks").build());
            assertThat(response.getResults()).isNotEmpty();
            assertThat(response.getResults()).anySatisfy(result -> assertThat(result.getTitle())
                    .containsIgnoringCase("soup"));
        } finally {
            BeanLifeCycleUtils.destroyQuietly(searcher);
        }

        // and the same page reached the vector store, addressed by catalog and version
        assertThat(vectored(catalog.getId(), 0)).isPositive();
    }

    // ---- update ---------------------------------------------------------------------------------

    @Test
    @DisplayName("update: a rebuild writes a new version and moves what search serves")
    void rebuildAddsAVersion() throws Exception {
        Catalog catalog = create("matrix-update");
        crawlerLauncher.crawl(catalog.getId(), null);

        crawlerLauncher.rebuild(catalog.getId(), null);

        String id = catalog.getId();
        assertThat(recordStore.findVersions(id)).containsExactly(0, 1);
        assertThat(indexed(id, 0)).isPositive();
        assertThat(indexed(id, 1)).isPositive();
        assertThat(vectored(id, 1)).isPositive();
        assertThat(catalogAdminService.require("matrix-update").getSearchVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("update: editing the definition keeps the versions it has already written")
    void editingKeepsTheData() throws Exception {
        Catalog catalog = create("matrix-edit");
        crawlerLauncher.crawl(catalog.getId(), null);

        Catalog edited = catalogAdminService.require("matrix-edit");
        edited.setCat("food");
        edited.setMaxFetchSize(40);
        catalogAdminService.save(edited);

        Catalog back = catalogAdminService.require("matrix-edit");
        assertThat(back.getCat()).isEqualTo("food");
        assertThat(back.getMaxFetchSize()).isEqualTo(40);
        assertThat(back.getSearchVersion()).isZero();
        assertThat(indexed(catalog.getId(), 0)).isPositive();
    }

    // ---- delete ---------------------------------------------------------------------------------

    @Test
    @DisplayName("delete: one version leaves the other standing in all four stores")
    void deletesOneVersion() throws Exception {
        Catalog catalog = create("matrix-delete-one");
        crawlerLauncher.crawl(catalog.getId(), null);
        crawlerLauncher.rebuild(catalog.getId(), null);
        String id = catalog.getId();

        deletionService.delete(detailsOf(catalog), List.of(0), EnumSet.allOf(DeleteLayer.class),
                false, true);

        assertThat(recordStore.countByCatalog(id, 0)).isZero();
        assertThat(indexed(id, 0)).isZero();
        assertThat(vectored(id, 0)).isZero();
        assertThat(state(id, 0)).allSatisfy(path -> assertThat(path).doesNotExist());

        assertThat(recordStore.countByCatalog(id, 1)).isPositive();
        assertThat(indexed(id, 1)).isPositive();
        assertThat(vectored(id, 1)).isPositive();
        assertThat(state(id, 1)).allSatisfy(path -> assertThat(path).isDirectory());
    }

    @Test
    @DisplayName("delete: the whole catalog leaves nothing behind in any of the four")
    void deletesTheWholeCatalog() throws Exception {
        Catalog catalog = create("matrix-delete-all");
        crawlerLauncher.crawl(catalog.getId(), null);
        crawlerLauncher.rebuild(catalog.getId(), null);
        String id = catalog.getId();

        deletionService.deleteCatalog(detailsOf(catalog), EnumSet.allOf(DeleteLayer.class), false,
                true);

        assertThat(recordStore.findVersions(id)).isEmpty();
        assertThat(indexExists(id)).isFalse();
        assertThat(vectored(id, null)).isZero();
        assertThat(files(id)).doesNotExist();
        assertThat(state(id, 0)).allSatisfy(path -> assertThat(path).doesNotExist());
        assertThat(state(id, 1)).allSatisfy(path -> assertThat(path).doesNotExist());

        // back to defined and never crawled, so the next crawl is v0 again
        Catalog back = catalogAdminService.require("matrix-delete-all");
        assertThat(back.getIndexVersion()).isZero();
        assertThat(back.getSearchVersion()).isEqualTo(-1);
    }

    @Test
    @DisplayName("delete: emptying a catalog keeps its index, dropping it does not")
    void cleaningKeepsTheIndex() throws Exception {
        Catalog catalog = create("matrix-clean");
        crawlerLauncher.crawl(catalog.getId(), null);
        String id = catalog.getId();

        deletionService.cleanCatalog(detailsOf(catalog), EnumSet.allOf(DeleteLayer.class), false,
                true);

        assertThat(indexExists(id)).isTrue();
        assertThat(indexed(id, 0)).isZero();
        assertThat(vectored(id, null)).isZero();
        assertThat(recordStore.findVersions(id)).isEmpty();
    }

    @Test
    @DisplayName("delete: a catalog can be crawled again into the stores it was emptied from")
    void crawlsAgainAfterADelete() throws Exception {
        Catalog catalog = create("matrix-again");
        crawlerLauncher.crawl(catalog.getId(), null);
        deletionService.cleanCatalog(detailsOf(catalog), EnumSet.allOf(DeleteLayer.class), false,
                true);
        String id = catalog.getId();

        crawlerLauncher.crawl(id, null);

        assertThat(recordStore.countByCatalog(id, 0)).isPositive();
        assertThat(indexed(id, 0)).isPositive();
        assertThat(vectored(id, 0)).isPositive();
    }

    @Test
    @DisplayName("delete: one layer at a time, and the other three are untouched")
    void deletesOneLayerAtATime() throws Exception {
        Catalog catalog = create("matrix-layers");
        crawlerLauncher.crawl(catalog.getId(), null);
        String id = catalog.getId();

        deletionService.delete(detailsOf(catalog), List.of(0), EnumSet.of(DeleteLayer.VECTOR),
                false, true);

        assertThat(vectored(id, 0)).isZero();
        assertThat(indexed(id, 0)).isPositive();
        assertThat(recordStore.countByCatalog(id, 0)).isPositive();
        assertThat(files(id)).isDirectory();

        deletionService.delete(detailsOf(catalog), List.of(0), EnumSet.of(DeleteLayer.INDEX),
                false, true);

        assertThat(indexed(id, 0)).isZero();
        assertThat(recordStore.countByCatalog(id, 0)).isPositive();
        assertThat(files(id)).isDirectory();
    }

}
