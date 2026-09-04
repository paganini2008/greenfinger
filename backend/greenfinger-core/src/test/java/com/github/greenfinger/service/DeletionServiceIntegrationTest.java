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

package com.github.greenfinger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsService;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.DeleteLayer;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.output.IndexAdmin;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.output.OutputProperties;

/**
 * Removing versions across the four stores, in the one order that is allowed.
 * 
 * @Description: DeletionServiceIntegrationTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@SpringBootTest(classes = CrawlerTestApplication.class)
@TestPropertySource(properties = {"greenfinger.output.file.directory=${java.io.tmpdir}/gf-del/data",
        "greenfinger.frontier-directory=${java.io.tmpdir}/gf-del/frontier",
        "greenfinger.dedup.url.directory=${java.io.tmpdir}/gf-del/url",
        "greenfinger.dedup.content.directory=${java.io.tmpdir}/gf-del/content",
        "greenfinger.output.index.lucene.directory=${java.io.tmpdir}/gf-del-lucene",
        "greenfinger.output.vector.lucene.directory=${java.io.tmpdir}/gf-del-lucene-vector",
        // these fixtures are a handful of pages on localhost; a real deployment waits
        // two minutes before believing the counters have stopped
        "greenfinger.completion-check-interval=200ms", "greenfinger.idle-timeout=600ms",
        "spring.datasource.url=jdbc:h2:mem:greenfinger-del;DB_CLOSE_DELAY=-1"})
class DeletionServiceIntegrationTest {

    @Autowired
    private com.github.greenfinger.core.WebCrawlerProperties webCrawlerProperties;

    @Autowired
    private CrawlerLauncher crawlerLauncher;

    @Autowired
    private CatalogAdminService catalogAdminService;

    @Autowired
    private CatalogDetailsService catalogDetailsService;

    @Autowired
    private DeletionService deletionService;

    @Autowired
    private VersionPruner versionPruner;

    @Autowired
    private ResourceRecordStore recordStore;

    @Autowired
    private OutputProperties outputProperties;

    @Autowired
    private com.github.greenfinger.output.OutputFactory outputFactory;

    private LocalSite site;

    @BeforeEach
    void setUp() throws Exception {
        wipe(Path.of(System.getProperty("java.io.tmpdir"), "gf-del"));
        catalogAdminService.findAll().forEach(c -> catalogAdminService.delete(c.getId()));
        site = new LocalSite();
        site.html("/", "<html><head><title>Index</title></head><body>"
                + "<p>The index page of the test site, with enough words to count as a page.</p>"
                + "<a href='/a'>A</a></body></html>");
        site.html("/a", "<html><head><title>Page A</title></head><body>"
                + "<p>Alpha content, long enough to be worth keeping in the output.</p>"
                + "</body></html>");
    }

    @AfterEach
    void tearDown() {
        site.close();
    }

    private static void wipe(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private Catalog crawled(String name, int versions) throws Exception {
        Catalog catalog = new Catalog();
        catalog.setName(name);
        catalog.setUrl(site.baseUrl());
        catalog.setStartUrl(site.baseUrl());
        catalog.setPathPattern(site.baseUrl() + "/**");
        catalog.setMaxFetchSize(10);
        catalog.setFetchInterval(0L);
        catalog.setImageEnabled(false);
        catalog.setOutputTypes(Set.of(OutputType.FILE));
        catalog.setMaxVersions(10);
        catalog = catalogAdminService.save(catalog);

        crawlerLauncher.crawl(catalog.getId(), null);
        for (int i = 1; i < versions; i++) {
            crawlerLauncher.rebuild(catalog.getId(), null);
        }
        return catalogAdminService.require(name);
    }

    private CatalogDetails detailsOf(Catalog catalog) {
        return catalogDetailsService.loadCatalogDetails(catalog.getId());
    }

    private Path root() {
        return Path.of(outputProperties.getFile().getDirectory());
    }

    /** The three RocksDB directories a version is crawled with, in the order they are listed. */
    private List<Path> stateOf(Catalog catalog, int version) {
        String scope = catalog.getId() + "/v" + version;
        return List.of(Path.of(webCrawlerProperties.getFrontierDirectory(), scope),
                Path.of(webCrawlerProperties.getDedup().getUrl().getDirectory(), scope),
                Path.of(webCrawlerProperties.getDedup().getContent().getDirectory(), scope));
    }

    @Test
    @DisplayName("the db layer takes the version's frontier and dedup stores with it")
    void removingTheRowsRemovesTheCrawlState() throws Exception {
        Catalog catalog = crawled("del-state", 2);
        CatalogDetails details = detailsOf(catalog);

        assertThat(stateOf(catalog, 0)).allSatisfy(path -> assertThat(path).isDirectory());

        deletionService.delete(details, List.of(0), EnumSet.of(DeleteLayer.DB), false, false);

        // gone with the rows they were answering questions about
        assertThat(stateOf(catalog, 0)).allSatisfy(path -> assertThat(path).doesNotExist());
        // and the version that was not named is untouched
        assertThat(stateOf(catalog, 1)).allSatisfy(path -> assertThat(path).isDirectory());
    }

    @Test
    @DisplayName("a layer that is not db leaves the crawl state alone")
    void deletingOnlyTheFilesKeepsTheCrawlState() throws Exception {
        Catalog catalog = crawled("del-state-keep", 1);
        CatalogDetails details = detailsOf(catalog);

        deletionService.delete(details, List.of(0), EnumSet.of(DeleteLayer.FILE), false, true);

        assertThat(stateOf(catalog, 0)).allSatisfy(path -> assertThat(path).isDirectory());
    }

    @Test
    @DisplayName("a dry run reports the state directories without removing them")
    void dryRunLeavesTheCrawlState() throws Exception {
        Catalog catalog = crawled("del-state-dry", 1);
        CatalogDetails details = detailsOf(catalog);

        deletionService.delete(details, List.of(0), EnumSet.of(DeleteLayer.DB), true, true);

        assertThat(stateOf(catalog, 0)).allSatisfy(path -> assertThat(path).isDirectory());
    }

    @Test
    @DisplayName("a dry run reports what would go and touches nothing")
    void dryRunChangesNothing() throws Exception {
        Catalog catalog = crawled("del-dry", 2);
        CatalogDetails details = detailsOf(catalog);

        DeleteReport report = deletionService.delete(details, List.of(0),
                EnumSet.of(DeleteLayer.DB, DeleteLayer.FILE), true, false);

        assertThat(report.getLines()).isNotEmpty();
        assertThat(report.hasFailures()).isFalse();
        assertThat(recordStore.countByCatalog(catalog.getId(), 0)).isEqualTo(2);
        assertThat(root().resolve(catalog.getId() + "/v0")).exists();
    }

    @Test
    void removesOneVersionFromTheDatabaseAndTheFiles() throws Exception {
        Catalog catalog = crawled("del-one", 2);
        CatalogDetails details = detailsOf(catalog);

        deletionService.delete(details, List.of(0),
                EnumSet.of(DeleteLayer.DB, DeleteLayer.FILE), false, false);

        assertThat(recordStore.countByCatalog(catalog.getId(), 0)).isZero();
        assertThat(root().resolve(catalog.getId() + "/v0")).doesNotExist();
        // the current version is untouched
        assertThat(recordStore.countByCatalog(catalog.getId(), 1)).isEqualTo(2);
        assertThat(root().resolve(catalog.getId() + "/v1")).exists();
    }

    @Test
    @DisplayName("layers can be picked apart: drop the files, keep the metadata")
    void deletesOnlyTheLayersNamed() throws Exception {
        Catalog catalog = crawled("del-layers", 2);
        CatalogDetails details = detailsOf(catalog);

        deletionService.delete(details, List.of(0), EnumSet.of(DeleteLayer.FILE), false, false);

        assertThat(root().resolve(catalog.getId() + "/v0")).doesNotExist();
        assertThat(recordStore.countByCatalog(catalog.getId(), 0)).isEqualTo(2);
    }

    @Test
    @DisplayName("the version search is serving is protected unless forced")
    void refusesToRemoveThePublishedVersion() throws Exception {
        Catalog catalog = crawled("del-guard", 1);
        CatalogDetails details = detailsOf(catalog);

        assertThatThrownBy(() -> deletionService.delete(details, List.of(0),
                EnumSet.of(DeleteLayer.DB), false, false))
                        .isInstanceOf(WebCrawlerException.class)
                        .hasMessageContaining("--force");

        deletionService.delete(details, List.of(0), EnumSet.of(DeleteLayer.DB), false, true);
        assertThat(recordStore.countByCatalog(catalog.getId(), 0)).isZero();
    }

    @Test
    @DisplayName("but emptying the catalog is not asked to force anything: all of it is the ask")
    void emptyingTakesThePublishedVersionWithIt() throws Exception {
        Catalog catalog = crawled("del-empty-served", 1);
        CatalogDetails details = detailsOf(catalog);

        deletionService.cleanCatalog(details, EnumSet.of(DeleteLayer.DB), false, false);

        assertThat(recordStore.countByCatalog(catalog.getId(), 0)).isZero();
    }

    @Test
    @DisplayName("and neither is deleting it entirely")
    void deletingEntirelyTakesThePublishedVersionWithIt() throws Exception {
        Catalog catalog = crawled("del-drop-served", 1);
        CatalogDetails details = detailsOf(catalog);

        deletionService.deleteCatalog(details, EnumSet.of(DeleteLayer.DB), false, false);

        assertThat(recordStore.countByCatalog(catalog.getId(), 0)).isZero();
    }

    @Test
    @DisplayName("deleting a version that is already gone is a no-op, so a retry is safe")
    void isIdempotent() throws Exception {
        Catalog catalog = crawled("del-again", 2);
        CatalogDetails details = detailsOf(catalog);

        deletionService.delete(details, List.of(0),
                EnumSet.of(DeleteLayer.DB, DeleteLayer.FILE), false, false);
        DeleteReport second = deletionService.delete(details, List.of(0),
                EnumSet.of(DeleteLayer.DB, DeleteLayer.FILE), false, false);

        assertThat(second.hasFailures()).isFalse();
    }

    @Test
    void listsTheVersionsThereAreToDelete() throws Exception {
        Catalog catalog = crawled("del-versions", 3);
        assertThat(deletionService.versionsOf(detailsOf(catalog))).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("pruning keeps the newest and never touches the two that are in use")
    void prunesTheOldest() throws Exception {
        Catalog catalog = crawled("del-prune", 4);
        catalog.setMaxVersions(2);
        catalogAdminService.save(catalog);

        versionPruner.prune(detailsOf(catalog));

        List<Integer> left = recordStore.findVersions(catalog.getId());
        assertThat(left).contains(3);
        assertThat(left).hasSizeLessThan(4);
    }

    @Test
    void pruningIsOffWhenTheLimitIsZero() throws Exception {
        Catalog catalog = crawled("del-nolimit", 2);
        catalog.setMaxVersions(0);
        catalogAdminService.save(catalog);

        DeleteReport report = versionPruner.prune(detailsOf(catalog));

        assertThat(report.getLines()).isEmpty();
        assertThat(recordStore.findVersions(catalog.getId())).containsExactly(0, 1);
    }

    @Test
    void pruningDoesNothingWhenThereIsRoom() throws Exception {
        Catalog catalog = crawled("del-room", 2);
        assertThat(versionPruner.prune(detailsOf(catalog)).getLines()).isEmpty();
    }

    @Test
    void reportsPerLayerSoAPartialRunIsVisible() throws Exception {
        Catalog catalog = crawled("del-report", 2);
        DeleteReport report = deletionService.delete(detailsOf(catalog), List.of(0),
                EnumSet.of(DeleteLayer.DB, DeleteLayer.FILE), true, false);

        assertThat(report.getLines()).extracting(DeleteReport.Line::layer)
                .containsExactly(DeleteLayer.FILE, DeleteLayer.DB);
        assertThat(report.total()).isPositive();
    }


    /**
     * The same catalog, crawled into the index as well, so the index layer has something to be
     * emptied or dropped.
     */
    private Catalog indexed(String name) throws Exception {
        Catalog catalog = new Catalog();
        catalog.setName(name);
        catalog.setUrl(site.baseUrl());
        catalog.setStartUrl(site.baseUrl());
        catalog.setPathPattern(site.baseUrl() + "/**");
        catalog.setMaxFetchSize(10);
        catalog.setFetchInterval(0L);
        catalog.setImageEnabled(false);
        catalog.setOutputTypes(Set.of(OutputType.FILE, OutputType.INDEX));
        catalog.setMaxVersions(10);
        catalog = catalogAdminService.save(catalog);
        crawlerLauncher.crawl(catalog.getId(), null);
        crawlerLauncher.rebuild(catalog.getId(), null);
        return catalogAdminService.require(name);
    }

    @Test
    @DisplayName("one version: the others are untouched, and the index survives")
    void deletesOneVersionOnly() throws Exception {
        Catalog catalog = indexed("del-one-version");
        CatalogDetails details = detailsOf(catalog);

        deletionService.delete(details, List.of(0), EnumSet.allOf(DeleteLayer.class), false, true);

        try (IndexAdmin admin = outputFactory.getIndexAdmin()) {
            assertThat(admin.indexExists(catalog.getId())).isTrue();
            assertThat(admin.countByCatalogVersion(catalog.getId() + ":0")).isZero();
            assertThat(admin.countByCatalogVersion(catalog.getId() + ":1")).isPositive();
        }
        assertThat(recordStore.countByCatalog(catalog.getId(), 0)).isZero();
        assertThat(recordStore.countByCatalog(catalog.getId(), 1)).isPositive();
    }

    @Test
    @DisplayName("every version: the catalog is emptied and its index is still there")
    void cleaningLeavesTheIndexStanding() throws Exception {
        Catalog catalog = indexed("del-clean");
        CatalogDetails details = detailsOf(catalog);

        DeleteReport report = deletionService.cleanCatalog(details,
                EnumSet.allOf(DeleteLayer.class), false, true);

        // the report is still read version by version, even though one statement did each layer
        assertThat(report.getLines()).extracting(DeleteReport.Line::version)
                .containsOnly(0, 1);
        try (IndexAdmin admin = outputFactory.getIndexAdmin()) {
            assertThat(admin.indexExists(catalog.getId())).isTrue();
            assertThat(admin.countByCatalog(catalog.getId())).isZero();
        }
        assertThat(recordStore.findVersions(catalog.getId())).isEmpty();
        assertThat(root().resolve(catalog.getId())).doesNotExist();
    }

    @Test
    @DisplayName("the whole catalog: the index goes with it")
    void deletingTheCatalogDropsTheIndex() throws Exception {
        Catalog catalog = indexed("del-purge");
        CatalogDetails details = detailsOf(catalog);

        deletionService.deleteCatalog(details, EnumSet.allOf(DeleteLayer.class), false, true);

        try (IndexAdmin admin = outputFactory.getIndexAdmin()) {
            assertThat(admin.indexExists(catalog.getId())).isFalse();
        }
        assertThat(recordStore.findVersions(catalog.getId())).isEmpty();
        // the definition is a separate thing and is still there
        assertThat(catalogAdminService.findAll()).extracting(Catalog::getId)
                .contains(catalog.getId());
    }

    @Test
    @DisplayName("a dry run of the whole catalog counts every layer and removes nothing")
    void aWholeCatalogDryRun() throws Exception {
        Catalog catalog = indexed("del-dry");
        CatalogDetails details = detailsOf(catalog);

        DeleteReport report = deletionService.deleteCatalog(details,
                EnumSet.allOf(DeleteLayer.class), true, true);

        assertThat(report.getLines()).isNotEmpty();
        try (IndexAdmin admin = outputFactory.getIndexAdmin()) {
            assertThat(admin.countByCatalog(catalog.getId())).isPositive();
        }
        assertThat(recordStore.findVersions(catalog.getId())).isNotEmpty();
    }

    @Test
    @DisplayName("only the layers asked for: the index alone leaves the rows and the files")
    void cleansOneLayerOnly() throws Exception {
        Catalog catalog = indexed("del-one-layer");
        CatalogDetails details = detailsOf(catalog);

        deletionService.cleanCatalog(details, EnumSet.of(DeleteLayer.INDEX), false, true);

        try (IndexAdmin admin = outputFactory.getIndexAdmin()) {
            assertThat(admin.countByCatalog(catalog.getId())).isZero();
        }
        assertThat(recordStore.findVersions(catalog.getId())).isNotEmpty();
        assertThat(root().resolve(catalog.getId())).exists();
    }

    @Test
    @DisplayName("an emptied catalog is back to v0, because v0 to v2 no longer exist anywhere")
    void emptyingPutsTheVersionsBack() throws Exception {
        Catalog catalog = crawled("it-reset", 3);
        assertThat(catalog.getIndexVersion()).isEqualTo(2);

        deletionService.cleanCatalog(detailsOf(catalog), Set.of(DeleteLayer.values()), false, true);

        Catalog after = catalogAdminService.require("it-reset");
        assertThat(after.getIndexVersion()).isZero();
        assertThat(after.getSearchVersion()).isEqualTo(-1);
        // the catalog itself is still there: that is the whole difference from deleting it
        assertThat(after.getName()).isEqualTo("it-reset");
    }

    @Test
    @DisplayName("removing some versions leaves the numbering alone: the rest are still there")
    void keepingSomeVersionsKeepsTheNumbering() throws Exception {
        Catalog catalog = crawled("it-keep-numbering", 3);

        deletionService.delete(detailsOf(catalog), java.util.List.of(0),
                Set.of(DeleteLayer.values()), false, true);

        assertThat(catalogAdminService.require("it-keep-numbering").getIndexVersion())
                .isEqualTo(2);
    }

}
