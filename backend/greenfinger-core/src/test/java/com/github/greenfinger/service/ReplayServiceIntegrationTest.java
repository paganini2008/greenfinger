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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.OutputType;

/**
 * Rebuilding an output from the database, without crawling anything again.
 * 
 * @Description: ReplayServiceIntegrationTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@SpringBootTest(classes = CrawlerTestApplication.class)
@TestPropertySource(properties = {
        // Elasticsearch on purpose: the embedded index is covered by its own tests, and this is
        // the one place the replay's path into a real server is exercised end to end
        "greenfinger.output.index.provider=elasticsearch",
        // these fixtures are a handful of pages on localhost; a real deployment waits
        // two minutes before believing the counters have stopped
        "greenfinger.completion-check-interval=200ms", "greenfinger.idle-timeout=600ms",
        "greenfinger.output.file.directory=${java.io.tmpdir}/gf-rep/data",
        "greenfinger.frontier-directory=${java.io.tmpdir}/gf-rep/frontier",
        "greenfinger.dedup.url.directory=${java.io.tmpdir}/gf-rep/url",
        "greenfinger.dedup.content.directory=${java.io.tmpdir}/gf-rep/content",
        "greenfinger.output.index.lucene.directory=${java.io.tmpdir}/gf-rep-lucene",
        "greenfinger.output.vector.lucene.directory=${java.io.tmpdir}/gf-rep-lucene-vector",
        "spring.datasource.url=jdbc:h2:mem:greenfinger-rep;DB_CLOSE_DELAY=-1"})
class ReplayServiceIntegrationTest {

    private static StubJsonServer elasticsearch;

    @BeforeAll
    static void startStub() throws Exception {
        elasticsearch = new StubJsonServer();
        // by prefix, because the index is named after the catalog's id and that is minted at
        // run time: every path this replay touches starts with the same one
        elasticsearch.on("GET", "/greenfinger-", 200, "{}")
                .on("PUT", "/greenfinger-", 200, "{\"acknowledged\":true}")
                .on("POST", "/greenfinger-", 200, "{\"errors\":false,\"items\":[]}");
    }

    @AfterAll
    static void stopStub() {
        elasticsearch.close();
    }

    @DynamicPropertySource
    static void indexEndpoint(DynamicPropertyRegistry registry) {
        registry.add("greenfinger.output.index.uris", () -> elasticsearch.url());
    }

    @Autowired
    private CrawlerLauncher crawlerLauncher;

    @Autowired
    private CatalogAdminService catalogAdminService;

    @Autowired
    private ReplayService replayService;

    @Autowired
    private CatalogStore catalogStore;

    private LocalSite site;

    @BeforeEach
    void setUp() throws Exception {
        wipe(Path.of(System.getProperty("java.io.tmpdir"), "gf-rep"));
        catalogAdminService.findAll().forEach(c -> catalogAdminService.delete(c.getId()));
        elasticsearch.getRequests().clear();
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

    private Catalog crawled(String name) throws Exception {
        Catalog catalog = new Catalog();
        catalog.setName(name);
        catalog.setUrl(site.baseUrl());
        catalog.setStartUrl(site.baseUrl());
        catalog.setPathPattern(site.baseUrl() + "/**");
        catalog.setMaxFetchSize(10);
        catalog.setFetchInterval(0L);
        catalog.setImageEnabled(false);
        // crawl to files only, so the index is built purely by the replay
        catalog.setOutputTypes(Set.of(OutputType.FILE));
        catalog = catalogAdminService.save(catalog);
        crawlerLauncher.crawl(catalog.getId(), null);
        return catalogAdminService.require(name);
    }

    @Test
    @DisplayName("an index dropped by accident is rebuilt from the database and the files")
    void rebuildsTheIndexWithoutCrawlingAgain() throws Exception {
        Catalog catalog = crawled("rep-index");
        int before = site.requestCount();

        long replayed = replayService.replay(catalog.getId(), 0, Set.of(OutputType.INDEX));

        assertThat(replayed).isEqualTo(2);
        assertThat(elasticsearch.getRequests())
                .anyMatch(r -> r.startsWith("POST /greenfinger-" + catalog.getId() + "/_bulk"));
        // nothing was fetched again: the input came from what was already stored
        assertThat(site.requestCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("a version that was never published becomes searchable once it is replayed")
    void aReplayPublishesTheVersionItRebuilt() throws Exception {
        Catalog catalog = crawled("rep-publish");
        // what a catalog looks like when the node that was going to publish it went away between
        // the last page and the publish: every row and every file is there, and search skips it
        catalogAdminService.require(catalog.getId());
        catalogStore.resetVersions(catalog.getId());
        assertThat(catalogAdminService.require(catalog.getId()).getSearchVersion()).isEqualTo(-1);

        replayService.replay(catalog.getId(), 0, Set.of(OutputType.INDEX));

        assertThat(catalogAdminService.require(catalog.getId()).getSearchVersion()).isZero();
    }

    @Test
    @DisplayName("replaying an older version never demotes the one search is already serving")
    void aReplayNeverPublishesBackwards() throws Exception {
        Catalog catalog = crawled("rep-no-demote");
        catalogStore.publishSearchVersion(catalog.getId(), 3);

        replayService.replay(catalog.getId(), 0, Set.of(OutputType.INDEX));

        assertThat(catalogAdminService.require(catalog.getId()).getSearchVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("a replay of the files alone changes nothing about what search is serving")
    void restoringFilesDoesNotPublishAnything() throws Exception {
        Catalog catalog = crawled("rep-files-only");
        catalogStore.resetVersions(catalog.getId());

        replayService.replay(catalog.getId(), 0, Set.of(OutputType.FILE));

        assertThat(catalogAdminService.require(catalog.getId()).getSearchVersion()).isEqualTo(-1);
    }

    @Test
    @DisplayName("files deleted by accident are fetched back from the urls in the database")
    void filesAreRestoredFromTheirUrls() throws Exception {
        Catalog catalog = crawled("rep-file");
        List<Path> files = filesUnder(dataRoot());
        assertThat(files).isNotEmpty();
        for (Path file : files) {
            Files.delete(file);
        }
        int before = site.requestCount();

        long checked = replayService.replay(catalog.getId(), 0, Set.of(OutputType.FILE));

        // the one layer a replay cannot rebuild from the database alone, so it goes and gets it:
        // the row keeps the url, which is all that is needed to ask for the page again
        assertThat(checked).isEqualTo(2);
        assertThat(filesUnder(dataRoot())).hasSameSizeAs(files);
        assertThat(site.requestCount()).isGreaterThan(before);
        FileRestorer.Result result = replayService.getLastFileRestore();
        assertThat(result.pages()).isEqualTo(2);
        assertThat(result.unreachable()).isZero();
    }

    @Test
    @DisplayName("a file that is already there costs no request at all")
    void restoringWhatIsIntactFetchesNothing() throws Exception {
        Catalog catalog = crawled("rep-file-intact");
        int before = site.requestCount();

        replayService.replay(catalog.getId(), 0, Set.of(OutputType.FILE));

        // what makes the operation safe to run twice, and cheap the second time
        assertThat(site.requestCount()).isEqualTo(before);
        assertThat(replayService.getLastFileRestore().intact()).isEqualTo(2);
        assertThat(replayService.getLastFileRestore().pages()).isZero();
    }

    @Test
    @DisplayName("a page taken down since the crawl is reported rather than passed over")
    void aPageThatNoLongerAnswersIsCounted() throws Exception {
        Catalog catalog = crawled("rep-file-gone");
        for (Path file : filesUnder(dataRoot())) {
            Files.delete(file);
        }
        site.close();

        replayService.replay(catalog.getId(), 0, Set.of(OutputType.FILE));

        FileRestorer.Result result = replayService.getLastFileRestore();
        assertThat(result.unreachable()).isEqualTo(2);
        assertThat(result.pages()).isZero();
    }

    @Test
    @DisplayName("asking for the index does not drag the file layer along with it")
    void anIndexReplayDoesNotRefetchTheSite() throws Exception {
        Catalog catalog = crawled("rep-file-not-implied");
        int before = site.requestCount();

        // OutputType.parse adds FILE whether it was asked for or not, which is right for a crawl
        // and would turn this into a second crawl of the whole site
        replayService.replay(catalog.getId(), 0, OutputType.parseExact("index"));

        assertThat(site.requestCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("images are restored too: the row keeps the url each picture came from")
    void imagesAreRestoredFromTheirSourceUrls() throws Exception {
        site.image("/pic.png", pngBytes());
        site.html("/", "<html><head><title>Index</title></head><body>"
                + "<p>The index page of the test site, with enough words to count as a page.</p>"
                + "<img src='/pic.png' width='120' height='120' alt='A picture'/>"
                + "<a href='/a'>A</a></body></html>");
        Catalog catalog = crawledWithImages("rep-file-images");
        List<Path> pictures = picturesUnder(dataRoot());
        assertThat(pictures).isNotEmpty();
        for (Path picture : pictures) {
            Files.delete(picture);
        }

        replayService.replay(catalog.getId(), 0, Set.of(OutputType.FILE));

        // written back at the path the row records, not at one derived from what came back: that
        // path is how the index and the vectors refer to this picture
        assertThat(picturesUnder(dataRoot())).containsExactlyElementsOf(pictures);
        assertThat(replayService.getLastFileRestore().images()).isEqualTo(pictures.size());
    }

    private Catalog crawledWithImages(String name) throws Exception {
        Catalog catalog = new Catalog();
        catalog.setName(name);
        catalog.setUrl(site.baseUrl());
        catalog.setStartUrl(site.baseUrl());
        catalog.setPathPattern(site.baseUrl() + "/**");
        catalog.setMaxFetchSize(10);
        catalog.setFetchInterval(0L);
        catalog.setImageEnabled(true);
        catalog.setOutputTypes(Set.of(OutputType.FILE));
        catalog = catalogAdminService.save(catalog);
        crawlerLauncher.crawl(catalog.getId(), null);
        return catalogAdminService.require(name);
    }

    /** A real png, small enough to inline: one opaque pixel. */
    private static byte[] pngBytes() throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(120, 120,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", buffer);
        return buffer.toByteArray();
    }

    private List<Path> picturesUnder(Path root) throws Exception {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (var walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".png")).sorted().toList();
        }
    }

    private Path dataRoot() {
        return Path.of(System.getProperty("java.io.tmpdir"), "gf-rep", "data");
    }

    private List<Path> filesUnder(Path root) throws Exception {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (var walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".html")
                            || p.getFileName().toString().endsWith(".txt"))
                    .sorted().toList();
        }
    }

    @Test
    void replayingAVersionWithNoDataIsHarmless() throws Exception {
        Catalog catalog = crawled("rep-empty");
        assertThat(replayService.replay(catalog.getId(), 7, Set.of(OutputType.INDEX))).isZero();
    }

    @Test
    @DisplayName("the replay writes the version it was told to, not the catalog's current one")
    void replaysTheRequestedVersion() throws Exception {
        Catalog catalog = crawled("rep-version");
        crawlerLauncher.rebuild(catalog.getId(), null);

        long replayed = replayService.replay(catalog.getId(), 0, Set.of(OutputType.INDEX));

        assertThat(replayed).isEqualTo(2);
    }

}
