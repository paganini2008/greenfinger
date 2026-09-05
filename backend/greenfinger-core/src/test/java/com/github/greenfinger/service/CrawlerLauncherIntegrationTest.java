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
import java.util.Date;
import java.util.List;
import java.util.Map;
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
import com.github.greenfinger.core.WebCrawlerSemaphore;
import com.github.greenfinger.core.catalog.CatalogDetailsService;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.engine.CrawlRegistry;
import com.github.greenfinger.core.engine.CrawlerEngine;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.ContentMode;
import com.github.greenfinger.core.model.DeleteLayer;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.core.report.CrawlReportStore;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.output.OutputProperties;

/**
 * The whole write path against a real database and a real site: save the catalog, crawl it, and
 * check that the database, the files and the counters agree.
 * 
 * @Description: CrawlerLauncherIntegrationTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@SpringBootTest(classes = CrawlerTestApplication.class)
@TestPropertySource(properties = {"greenfinger.output.file.directory=${java.io.tmpdir}/gf-it/data",
        "greenfinger.frontier-directory=${java.io.tmpdir}/gf-it/frontier",
        "greenfinger.dedup.url.directory=${java.io.tmpdir}/gf-it/url",
        "greenfinger.dedup.content.directory=${java.io.tmpdir}/gf-it/content",
        // these sites are a handful of pages on localhost; a real deployment waits two
        // minutes before believing the counters have stopped
        "greenfinger.completion-check-interval=200ms", "greenfinger.idle-timeout=600ms"})
class CrawlerLauncherIntegrationTest {

    @Autowired
    private CrawlerLauncher crawlerLauncher;

    @Autowired
    private CatalogAdminService catalogAdminService;

    @Autowired
    private CatalogDetailsService catalogDetailsService;

    @Autowired
    private CatalogStore catalogStore;

    @Autowired
    private ResourceRecordStore recordStore;

    @Autowired
    private OutputFactory outputFactory;

    @Autowired
    private OutputProperties outputProperties;

    @Autowired
    private CrawlReportStore crawlReportStore;

    @Autowired
    private CrawlReportService crawlReportService;

    @Autowired
    private DeletionService deletionService;

    @Autowired
    private CrawlRegistry crawlRegistry;

    @Autowired
    private WebCrawlerSemaphore semaphore;

    /** The same three pages the fixture serves, named so a conditional test can restate them. */
    private static final String HOME = "<html><head><title>Index</title></head><body>"
            + "<p>The index page of the test site, with enough words to count as a page.</p>"
            + "<a href='/a'>A</a><a href='/b'>B</a></body></html>";
    private static final String PAGE_A = "<html><head><title>Page A</title></head><body>"
            + "<p>Alpha content, long enough to be worth keeping in the output.</p></body></html>";
    private static final String PAGE_B = "<html><head><title>Page B</title></head><body>"
            + "<p>Beta content, also long enough to be worth keeping in the output.</p>"
            + "</body></html>";

    private LocalSite site;

    @BeforeEach
    void setUp() throws Exception {
        // the crawl state and the output live under one temporary root; wiping it keeps each test
        // starting from nothing, and stops a previous run's url filter from skipping everything
        wipe(Path.of(System.getProperty("java.io.tmpdir"), "gf-it"));
        site = new LocalSite();
        site.html("/", "<html><head><title>Index</title></head><body>"
                + "<p>The index page of the test site, with enough words to count as a page.</p>"
                + "<a href='/a'>A</a><a href='/b'>B</a></body></html>");
        site.html("/a", "<html><head><title>Page A</title></head><body>"
                + "<p>Alpha content, long enough to be worth keeping in the output.</p>"
                + "</body></html>");
        site.html("/b", "<html><head><title>Page B</title></head><body>"
                + "<p>Beta content, also long enough to be worth keeping in the output.</p>"
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
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private Catalog saved(String name, Set<OutputType> outputs, ContentMode mode) {
        Catalog catalog = new Catalog();
        catalog.setName(name);
        catalog.setUrl(site.baseUrl());
        catalog.setStartUrl(site.baseUrl());
        catalog.setMaxFetchSize(10);
        catalog.setDepth(3);
        catalog.setFetchInterval(0L);
        catalog.setDuration(5L);
        catalog.setImageEnabled(false);
        catalog.setOutputTypes(outputs);
        catalog.setContentMode(mode);
        catalog.setPathPattern(site.baseUrl() + "/**");
        return catalogAdminService.save(catalog);
    }

    private Path root() {
        return Path.of(outputProperties.getFile().getDirectory());
    }

    @Test
    @DisplayName("a crawl reaches the database, the files and the counters alike")
    void crawlsIntoTheDatabaseAndTheFiles() throws Exception {
        Catalog catalog = saved("it-basic", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);

        CrawlerEngine.Result result = crawlerLauncher.crawl(catalog.getId(), null);

        assertThat(result.getDashboard().getSavedResourceCount()).isEqualTo(3);
        assertThat(recordStore.countByCatalog(catalog.getId(), 0)).isEqualTo(3);

        // every row points at a file that is actually there
        for (var record : recordStore.load(catalog.getId(), 0, 0, 10)) {
            assertThat(root().resolve(record.resource().getHtmlFilePath())).exists();
            assertThat(root().resolve(record.resource().getHtmlContentFilePath())).exists();
        }
    }

    @Test
    @DisplayName("by the time a crawl is no longer running, everything it owed is done")
    void nothingIsOwedOnceTheCrawlIsOutOfTheRegistry() throws Exception {
        Catalog catalog = saved("it-finished", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);

        crawlerLauncher.crawl(catalog.getId(), null);

        // What the api reads to answer "is this crawling" is the last thing to change. Released
        // any earlier, a delete pressed on a page that says the crawl has finished is refused as
        // "being crawled right now", and the version may not be published yet either.
        assertThat(crawlRegistry.isRunning(catalog.getId())).isFalse();
        assertThat(semaphore.isOccupied()).isFalse();
        assertThat(catalogDetailsService.loadCatalogDetails(catalog.getId()).getSearchVersion())
                .isZero();
        // and the guard lets it go, which is the thing that was actually failing
        deletionService.cleanCatalog(catalogDetailsService.loadCatalogDetails(catalog.getId()),
                java.util.EnumSet.of(DeleteLayer.DB), false, false);
        assertThat(recordStore.countByCatalog(catalog.getId(), 0)).isZero();
    }

    @Test
    @DisplayName("a finished crawl is what makes its version visible to search")
    void publishesTheSearchVersionOnlyOnCompletion() throws Exception {
        Catalog catalog = saved("it-publish", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);
        assertThat(catalogDetailsService.loadCatalogDetails(catalog.getId()).getSearchVersion())
                .isEqualTo(-1);

        crawlerLauncher.crawl(catalog.getId(), null);

        assertThat(catalogDetailsService.loadCatalogDetails(catalog.getId()).getSearchVersion())
                .isZero();
    }

    @Test
    @DisplayName("rebuild moves to a new version and leaves the old one entirely alone")
    void rebuildKeepsThePreviousVersion() throws Exception {
        Catalog catalog = saved("it-rebuild", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);
        crawlerLauncher.crawl(catalog.getId(), null);

        crawlerLauncher.rebuild(catalog.getId(), null);

        CatalogDetails details = catalogDetailsService.loadCatalogDetails(catalog.getId());
        assertThat(details.getVersion()).isEqualTo(1);
        assertThat(details.getSearchVersion()).isEqualTo(1);
        assertThat(recordStore.countByCatalog(catalog.getId(), 0)).isEqualTo(3);
        assertThat(recordStore.countByCatalog(catalog.getId(), 1)).isEqualTo(3);
        assertThat(recordStore.findVersions(catalog.getId())).containsExactly(0, 1);
    }

    @Test
    @DisplayName("the version is in the path, so the two versions' files sit side by side")
    void eachVersionHasItsOwnDirectory() throws Exception {
        Catalog catalog = saved("it-dirs", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);
        crawlerLauncher.crawl(catalog.getId(), null);
        crawlerLauncher.rebuild(catalog.getId(), null);

        // the directory is the catalog's id, not its name: a rename moves nothing on disk
        assertThat(root().resolve(catalog.getId() + "/v0")).exists();
        assertThat(root().resolve(catalog.getId() + "/v1")).exists();
        assertThat(root().resolve(catalog.getId() + "/v0/settings.json")).exists();
    }

    @Test
    @DisplayName("update keeps the version and the url filter, so only new urls are taken")
    void updateOnlyPicksUpWhatIsNew() throws Exception {
        Catalog catalog = saved("it-update", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);
        crawlerLauncher.crawl(catalog.getId(), null);
        assertThat(recordStore.countByCatalog(catalog.getId(), 0)).isEqualTo(3);

        site.html("/c", "<html><head><title>Page C</title></head><body>"
                + "<p>Gamma content, appearing only after the first crawl finished.</p>"
                + "</body></html>");
        site.html("/", "<html><head><title>Index</title></head><body>"
                + "<p>The index page of the test site, with enough words to count as a page.</p>"
                + "<a href='/a'>A</a><a href='/b'>B</a><a href='/c'>C</a></body></html>");

        crawlerLauncher.update(catalog.getId(), site.baseUrl(), null);

        // the same version, and only the page that did not exist before
        assertThat(catalogDetailsService.loadCatalogDetails(catalog.getId()).getVersion())
                .isZero();
        assertThat(recordStore.countByCatalog(catalog.getId(), 0)).isEqualTo(4);
    }

    @Test
    void settingsRecordHowTheVersionRan() throws Exception {
        Catalog catalog = saved("it-settings", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);
        crawlerLauncher.crawl(catalog.getId(), null);

        String settings =
                Files.readString(root().resolve(catalog.getId() + "/v0/settings.json"));
        assertThat(settings).contains("savedResourceCount").contains("\"version\" : 0");
    }

    @Test
    @DisplayName("the running state is cleared however the crawl ends")
    void runningStateReturnsToNone() throws Exception {
        Catalog catalog = saved("it-state", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);
        crawlerLauncher.crawl(catalog.getId(), null);

        assertThat(catalogStore.findById(catalog.getId()).orElseThrow().getRunningState())
                .isEqualTo("none");
        assertThat(catalogStore.findRunning()).isEmpty();
    }

    @Test
    @DisplayName("a crawl never leaves its own domain")
    void staysInsideTheDomain() throws Exception {
        site.html("/", "<html><head><title>Index</title></head><body>"
                + "<p>The index page, linking somewhere else entirely as pages tend to.</p>"
                + "<a href='/a'>A</a><a href='https://www.wikipedia.org/'>Away</a>"
                + "</body></html>");
        Catalog catalog = saved("it-domain", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);

        crawlerLauncher.crawl(catalog.getId(), null);

        assertThat(recordStore.load(catalog.getId(), 0, 0, 100))
                .allSatisfy(record -> assertThat(record.resource().getUrl())
                        .startsWith(site.baseUrl()));
    }

    @Test
    @DisplayName("start url is a prefix: a section crawl does not climb out of its section")
    void startUrlBoundsTheCrawl() throws Exception {
        Catalog catalog = saved("it-prefix", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);
        catalog.setStartUrl(site.baseUrl() + "/a");
        catalog.setPathPattern("**");
        catalogAdminService.save(catalog);

        crawlerLauncher.crawl(catalog.getId(), null);

        assertThat(recordStore.load(catalog.getId(), 0, 0, 100))
                .allSatisfy(record -> assertThat(record.resource().getUrl())
                        .startsWith(site.baseUrl() + "/a"));
    }

    @Test
    void maxFetchSizeStopsTheCrawl() throws Exception {
        Catalog catalog = saved("it-limit", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);
        catalog.setMaxFetchSize(1);
        catalogAdminService.save(catalog);

        CrawlerEngine.Result result = crawlerLauncher.crawl(catalog.getId(), null);

        assertThat(result.getReason()).contains("maxFetchSize");
        // the limit fires once exceeded, and several workers may already be in flight, so the
        // guarantee is that it stops early rather than at an exact count
        assertThat(result.getDashboard().getSavedResourceCount())
                .isLessThanOrEqualTo(3L).isPositive();
        // a configured limit is a finish, not a failure: the version is published and searchable
        assertThat(catalogDetailsService.loadCatalogDetails(catalog.getId()).getSearchVersion())
                .isZero();
    }

    @Test
    @DisplayName("a crawl stopped from outside leaves the previous version serving search")
    void anInterruptedCrawlIsNotPublished() throws Exception {
        Catalog catalog = saved("it-interrupt", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);
        crawlerLauncher.crawl(catalog.getId(), null);
        assertThat(catalogDetailsService.loadCatalogDetails(catalog.getId()).getSearchVersion())
                .isZero();

        crawlerLauncher.rebuild(catalog.getId(), context -> context.getGlobalStateManager()
                .interrupt("interrupted by the test"));

        assertThat(catalogDetailsService.loadCatalogDetails(catalog.getId()).getVersion())
                .isEqualTo(1);
        assertThat(catalogDetailsService.loadCatalogDetails(catalog.getId()).getSearchVersion())
                .isZero();
    }

    @Test
    @DisplayName("the file layer is added even when a catalog only asked for an index")
    void fileOutputIsNeverOptional() throws Exception {
        Catalog catalog = saved("it-file-implied", Set.of(OutputType.INDEX), ContentMode.TEXT);
        CatalogDetails details = catalogDetailsService.loadCatalogDetails(catalog.getId());

        assertThat(details.getOutputTypes()).contains(OutputType.FILE);
        assertThat(outputFactory.getFileLayout(details).versionPrefix())
                .isEqualTo(catalog.getId() + "/v0");
    }


    @Test
    @DisplayName("a merge asks the site first, and an unchanged page is answered without a body")
    void refreshAsksWithIfNoneMatch() throws Exception {
        Catalog catalog =
                saved("it-refresh-304", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);
        // the same three pages, now publishing validators the way a real site does
        site.conditional("/", HOME, "\"home-1\"");
        site.conditional("/a", PAGE_A, "\"a-1\"");
        site.conditional("/b", PAGE_B, "\"b-1\"");

        crawlerLauncher.crawl(catalog.getId(), null);
        assertThat(site.notModifiedCount()).isZero();
        // the validator was stored, which is what makes the next merge able to ask
        assertThat(recordStore.load(catalog.getId(), 0, 0, 100))
                .extracting(r -> r.resource().getEtag()).contains("\"a-1\"");

        var result = crawlerLauncher.update(catalog.getId(), site.baseUrl(), true, null);

        // every page answered 304: nothing was downloaded and nothing parsed
        assertThat(site.notModifiedCount()).isEqualTo(3);
        assertThat(result.getDashboard().getDuplicatedContentCount()).isEqualTo(3L);
        assertThat(result.getDashboard().getSavedResourceCount()).isZero();
        assertThat(recordStore.countByCatalog(catalog.getId(), 0)).isEqualTo(3);
    }

    @Test
    @DisplayName("a page whose validator moved on is fetched and merged as usual")
    void refreshStillMergesWhenTheValidatorChanges() throws Exception {
        Catalog catalog =
                saved("it-refresh-304-changed", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);
        site.conditional("/", HOME, "\"home-1\"");
        site.conditional("/a", PAGE_A, "\"a-1\"");
        site.conditional("/b", PAGE_B, "\"b-1\"");
        crawlerLauncher.crawl(catalog.getId(), null);

        site.conditional("/a", "<html><head><title>Page A</title></head><body><p>Alpha has been"
                + " rewritten, and the site says so with a new validator.</p></body></html>",
                "\"a-2\"");

        var result = crawlerLauncher.update(catalog.getId(), site.baseUrl(), true, null);

        assertThat(site.notModifiedCount()).isEqualTo(2);
        assertThat(result.getDashboard().getSavedResourceCount()).isEqualTo(1L);
        assertThat(recordStore.load(catalog.getId(), 0, 0, 100)).filteredOn(
                r -> r.resource().getUrl().endsWith("/a")).singleElement()
                .satisfies(r -> assertThat(r.resource().getEtag()).isEqualTo("\"a-2\""));
    }

    @Test
    @DisplayName("a refresh merges: changed pages are overwritten, unchanged ones cost nothing")
    void refreshMergesWhatChanged() throws Exception {
        Catalog catalog = saved("it-refresh", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);
        crawlerLauncher.crawl(catalog.getId(), null);

        var before = recordStore.load(catalog.getId(), 0, 0, 100);
        assertThat(before).hasSize(3);
        String changedId = before.stream()
                .filter(r -> r.resource().getUrl().endsWith("/a")).findFirst().orElseThrow()
                .resource().getId();
        String untouchedHash = before.stream()
                .filter(r -> r.resource().getUrl().endsWith("/b")).findFirst().orElseThrow()
                .resource().getContentHash();

        // one page rewritten, the others left exactly as they were
        site.html("/a", "<html><head><title>Page A</title></head><body>"
                + "<p>Alpha content has been rewritten since the last crawl, and reads quite "
                + "differently now that somebody has been at it.</p></body></html>");

        crawlerLauncher.update(catalog.getId(), site.baseUrl(), true, null);

        var after = recordStore.load(catalog.getId(), 0, 0, 100);
        // still three rows: a merge overwrites in place rather than adding beside
        assertThat(after).hasSize(3);
        assertThat(catalogDetailsService.loadCatalogDetails(catalog.getId()).getVersion())
                .isZero();

        var changed = recordStore.load(changedId).orElseThrow();
        assertThat(changed.resource().getContentHash()).isNotNull();
        assertThat(Files.readString(root().resolve(changed.resource().getHtmlContentFilePath())))
                .contains("rewritten since the last crawl");

        // the id is derived from the url and the version, so it survived the rewrite
        assertThat(changed.resource().getId()).isEqualTo(changedId);
        // and the page nobody touched kept its fingerprint
        assertThat(after.stream().filter(r -> r.resource().getUrl().endsWith("/b")).findFirst()
                .orElseThrow().resource().getContentHash()).isEqualTo(untouchedHash);
    }

    @Test
    @DisplayName("a refresh also picks up pages that appeared since, like a plain update")
    void refreshStillFindsNewPages() throws Exception {
        Catalog catalog = saved("it-refresh-new", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);
        crawlerLauncher.crawl(catalog.getId(), null);

        site.html("/c", "<html><head><title>Page C</title></head><body>"
                + "<p>Gamma content, which did not exist when the first crawl ran.</p>"
                + "</body></html>");
        site.html("/", "<html><head><title>Index</title></head><body>"
                + "<p>The index page of the test site, with enough words to count as a page.</p>"
                + "<a href='/a'>A</a><a href='/b'>B</a><a href='/c'>C</a></body></html>");

        crawlerLauncher.update(catalog.getId(), site.baseUrl(), true, null);

        assertThat(recordStore.countByCatalog(catalog.getId(), 0)).isEqualTo(4);
    }

    @Test
    @DisplayName("a refresh of an unchanged site writes nothing at all")
    void refreshOfAnUnchangedSiteIsANoOp() throws Exception {
        Catalog catalog = saved("it-refresh-same", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);
        crawlerLauncher.crawl(catalog.getId(), null);
        var before = recordStore.load(catalog.getId(), 0, 0, 100);

        var result = crawlerLauncher.update(catalog.getId(), site.baseUrl(), true, null);

        assertThat(recordStore.countByCatalog(catalog.getId(), 0)).isEqualTo(3);
        // every page came back the same, and each was counted as such
        assertThat(result.getDashboard().getDuplicatedContentCount()).isEqualTo(3L);
        assertThat(result.getDashboard().getSavedResourceCount()).isZero();

        var after = recordStore.load(catalog.getId(), 0, 0, 100);
        assertThat(after).extracting(r -> r.resource().getId())
                .containsExactlyElementsOf(before.stream().map(r -> r.resource().getId()).toList());
    }

    @Test
    @DisplayName("without --refresh, update leaves known pages alone even when they changed")
    void plainUpdateDoesNotRevisit() throws Exception {
        Catalog catalog = saved("it-no-refresh", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);
        crawlerLauncher.crawl(catalog.getId(), null);

        site.html("/a", "<html><head><title>Page A</title></head><body>"
                + "<p>Alpha content has been rewritten, and a plain update will not notice.</p>"
                + "</body></html>");

        crawlerLauncher.update(catalog.getId(), site.baseUrl(), false, null);

        var record = recordStore.load(catalog.getId(), 0, 0, 100).stream()
                .filter(r -> r.resource().getUrl().endsWith("/a")).findFirst().orElseThrow();
        assertThat(Files.readString(root().resolve(record.resource().getHtmlContentFilePath())))
                .doesNotContain("will not notice");
    }


    @Test
    @DisplayName("a merge that cannot finish refuses to start rather than half-doing it")
    void refusesAMergeThatWouldBeTruncated() throws Exception {
        Catalog catalog = saved("it-merge-limit", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);
        crawlerLauncher.crawl(catalog.getId(), null);
        assertThat(recordStore.countByCatalog(catalog.getId(), 0)).isEqualTo(3);

        // a limit below what is already stored: the merge could never revisit everything
        catalog.setMaxFetchSize(2);
        catalogAdminService.save(catalog);

        assertThatThrownBy(
                () -> crawlerLauncher.update(catalog.getId(), site.baseUrl(), true, null))
                        .isInstanceOf(WebCrawlerException.class)
                        .hasMessageContaining("silently stale")
                        .hasMessageContaining("Raise it above 3");
    }

    @Test
    @DisplayName("a merge cut short by the limit says so rather than reporting success")
    void reportsAMergeThatStoppedEarly() throws Exception {
        Catalog catalog = saved("it-merge-partial", Set.of(OutputType.FILE),
                ContentMode.TEXT_IMAGE);
        crawlerLauncher.crawl(catalog.getId(), null);

        // the site grows past the limit between the crawl and the merge
        for (int i = 0; i < 12; i++) {
            site.html("/new" + i, "<html><head><title>New " + i + "</title></head><body>"
                    + "<p>Fresh content number " + i + ", long enough to be kept in the output.</p>"
                    + "</body></html>");
        }
        StringBuilder index = new StringBuilder(
                "<html><head><title>Index</title></head><body><p>The index page, with links.</p>");
        for (int i = 0; i < 12; i++) {
            index.append("<a href='/new").append(i).append("'>N").append(i).append("</a>");
        }
        site.html("/", index.append("<a href='/a'>A</a><a href='/b'>B</a></body></html>")
                .toString());

        catalog.setMaxFetchSize(5);
        catalogAdminService.save(catalog);

        assertThatThrownBy(
                () -> crawlerLauncher.update(catalog.getId(), site.baseUrl(), true, null))
                        .isInstanceOf(WebCrawlerException.class)
                        .hasMessageContaining("stopped early");
    }

    @Test
    @DisplayName("a plain update is not a merge, so the limit guard does not apply")
    void plainUpdateIsNotGuarded() throws Exception {
        Catalog catalog = saved("it-merge-plain", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);
        crawlerLauncher.crawl(catalog.getId(), null);
        catalog.setMaxFetchSize(1);
        catalogAdminService.save(catalog);

        // no exception: a plain update never promised to revisit anything
        crawlerLauncher.update(catalog.getId(), site.baseUrl(), false, null);
    }


    @Test
    @DisplayName("a finished run leaves the whole dashboard in crawler_report, frozen")
    void recordsTheReportOfTheVersion() throws Exception {
        Catalog catalog = saved("it-report", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);

        crawlerLauncher.crawl(catalog.getId(), null);

        var report = crawlReportStore.find(catalog.getId(), 0);
        assertThat(report).isPresent();
        assertThat(report.get().getCreatedAt()).isNotNull();
        assertThat(report.get().getUpdatedAt()).isNotNull();

        Map<String, Object> content = crawlReportService.stored(catalog.getId(), 0).orElseThrow();
        assertThat(content.get("catalog")).isEqualTo("it-report");
        assertThat(content.get("version")).isEqualTo(0);

        // the frozen dashboard: the same counters the live view was showing while it ran
        Map<String, Object> dashboard = section(content, "dashboard");
        assertThat(dashboard.get("savedResourceCount")).isEqualTo(3);
        assertThat(dashboard).containsKeys("totalUrlCount", "handledUrlCount", "existingUrlCount",
                "filteredUrlCount", "invalidUrlCount", "duplicatedContentCount", "savedImageCount",
                "indexedResourceCount", "elapsedMillis");

        // and everything around it, written whether or not it has anything in it: a section that
        // is absent reads as "nobody looked"
        assertThat(content).containsKeys("run", "nodes", "cluster", "database", "storage",
                "outputs", "settings");
        assertThat(section(content, "cluster")).containsEntry("clustered", false);
        assertThat(section(content, "database")).containsKeys("product", "url", "resourceCount",
                "imageCount").containsEntry("resourceCount", 3);
        Map<String, Object> storage = section(content, "storage");
        assertThat(storage).containsEntry("target", "local");
        assertThat((Integer) storage.get("fileCount")).isPositive();
        assertThat(section(content, "settings")).isNotEmpty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> section(Map<String, Object> report, String name) {
        return (Map<String, Object>) report.get(name);
    }

    @Test
    @DisplayName("running a version again rewrites its row rather than adding one")
    void theReportIsOnePerVersion() throws Exception {
        Catalog catalog = saved("it-report-once", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);

        crawlerLauncher.crawl(catalog.getId(), null);
        Date created = crawlReportStore.find(catalog.getId(), 0).orElseThrow().getCreatedAt();

        crawlerLauncher.update(catalog.getId(), null, null);

        assertThat(crawlReportStore.findByCatalog(catalog.getId())).hasSize(1);
        assertThat(crawlReportStore.find(catalog.getId(), 0).orElseThrow().getCreatedAt())
                .isEqualTo(created);

        crawlerLauncher.rebuild(catalog.getId(), null);
        assertThat(crawlReportStore.findByCatalog(catalog.getId())).hasSize(2);
        // newest first, so the version somebody is asking about is the one they get
        assertThat(crawlReportStore.findByCatalog(catalog.getId()).get(0).getVersion())
                .isEqualTo(1);
        assertThat(crawlReportService.stored(catalog.getId(), null).orElseThrow().get("version"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("versions lists what each one holds, and which one search is serving")
    void listsTheVersions() throws Exception {
        Catalog catalog = saved("it-versions", Set.of(OutputType.FILE), ContentMode.TEXT_IMAGE);
        crawlerLauncher.crawl(catalog.getId(), null);
        crawlerLauncher.rebuild(catalog.getId(), null);

        List<Map<String, Object>> versions = crawlReportService.versions(catalog.getId());

        assertThat(versions).hasSize(2);
        assertThat(versions.get(0)).containsEntry("version", 1).containsEntry("current", true)
                .containsEntry("searchable", true);
        assertThat(versions.get(0).get("pages")).isEqualTo(3L);
        assertThat(versions.get(1)).containsEntry("version", 0).containsEntry("current", false);
        assertThat(versions.get(0).get("createdAt")).isNotNull();
    }

    @Test
    @DisplayName("deleting a version takes its report with it")
    void deletingAVersionRemovesItsReport() throws Exception {
        Catalog catalog = saved("it-report-delete", Set.of(OutputType.FILE),
                ContentMode.TEXT_IMAGE);
        crawlerLauncher.crawl(catalog.getId(), null);
        assertThat(crawlReportStore.find(catalog.getId(), 0)).isPresent();

        deletionService.delete(catalogDetailsService.loadCatalogDetails(catalog.getId()),
                List.of(0), java.util.EnumSet.of(DeleteLayer.DB), false, true);

        assertThat(crawlReportStore.find(catalog.getId(), 0)).isEmpty();
    }

}
