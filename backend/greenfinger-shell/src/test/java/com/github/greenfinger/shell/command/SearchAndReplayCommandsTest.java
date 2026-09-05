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

package com.github.greenfinger.shell.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import com.github.greenfinger.shell.ConsoleCapture;
import com.github.greenfinger.shell.CrawlOptions;
import com.github.greenfinger.shell.UsageException;
import com.github.greenfinger.shell.GreenfingerShellMain;
import com.github.greenfinger.shell.TestHttpServer;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.output.vector.VectorPoint;
import com.github.greenfinger.output.vector.VectorStore;
import com.github.greenfinger.service.CatalogAdminService;

/**
 * The search commands, driven against the embedded index -- which is the default, so this is the
 * path a fresh clone takes and there is nothing to stub. What is asserted is the whole round trip:
 * a crawl writes documents into a Lucene index of the catalog's own, and the commands find them
 * again.
 * 
 * @Description: SearchAndReplayCommandsTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@SpringBootTest(classes = GreenfingerShellMain.class)
@TestPropertySource(properties = {"spring.shell.interactive.enabled=false",
        "spring.main.banner-mode=off",
        // these fixtures are a handful of pages on localhost; a real deployment waits two
        // minutes before believing the counters have stopped
        "greenfinger.completion-check-interval=200ms", "greenfinger.idle-timeout=600ms",
        "spring.datasource.url=jdbc:h2:mem:greenfinger-cli-search;DB_CLOSE_DELAY=-1",
        "greenfinger.output.file.directory=${java.io.tmpdir}/gf-cli-search/data",
        "greenfinger.frontier-directory=${java.io.tmpdir}/gf-cli-search/frontier",
        "greenfinger.dedup.url.directory=${java.io.tmpdir}/gf-cli-search/url",
        "greenfinger.dedup.content.directory=${java.io.tmpdir}/gf-cli-search/content",
        "greenfinger.output.index.lucene.directory=${java.io.tmpdir}/gf-cli-search-lucene",
        "greenfinger.output.vector.lucene.directory=${java.io.tmpdir}/gf-cli-search-lucene-vector",
        "greenfinger.embedding.preload=false"})
class SearchAndReplayCommandsTest {

    @Autowired
    private CrawlCommands crawlCommands;

    @Autowired
    private QueryCommands queryCommands;

    @Autowired
    private CatalogAdminService catalogAdminService;

    @Autowired
    private OutputFactory outputFactory;

    private TestHttpServer site;

    @BeforeEach
    void setUp() throws Exception {
        wipe(Path.of(System.getProperty("java.io.tmpdir"), "gf-cli-search"));
        catalogAdminService.findAll().forEach(c -> catalogAdminService.delete(c.getId()));
        site = new TestHttpServer();
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

    /**
     * A catalog and one crawl of it. The interview needs a terminal, so the definition is built
     * here and the commands under test are given its id -- which is all any of them takes now.
     */
    private String crawl(String name) throws Exception {
        Catalog catalog = new Catalog();
        catalog.setName(name);
        catalog.setUrl(site.url());
        catalog.setMaxFetchSize(10);
        catalog.setFetchInterval(0L);
        catalog.setImageEnabled(false);
        // both pages, not just the seed: the default pattern is built from the host and does not
        // match a bare ip and port, which is what left these tests searching an index of one
        // document for a word that is on the other one
        catalog.setPathPattern(site.url() + "/**");
        catalog.setOutputTypes(java.util.Set.of(OutputType.FILE, OutputType.INDEX));
        String id = catalogAdminService.save(catalog).getId();
        crawlCommands.dispatch("catalog-crawl", null, new CrawlOptions().override("id", id));
        return id;
    }

    @Test
    @DisplayName("a full text search renders what the index returned")
    void searchesTheIndex() throws Exception {
        crawl("cli-search");
        try (ConsoleCapture console = new ConsoleCapture()) {
            queryCommands.search("alpha", null, 5, null);
            String output = console.output();
            assertThat(output).contains("Page A").contains("match");
        }
    }

    @Test
    void searchCanBeNarrowedToOneCatalog() throws Exception {
        String id = crawl("cli-search-one");
        try (ConsoleCapture console = new ConsoleCapture()) {
            queryCommands.search("alpha", id, 3, null);
            assertThat(console.output()).contains("Page A");
        }
    }

    @Test
    @DisplayName("index-info says where the index is, what is in it, and what else is there")
    void indexInfoReportsTheIndexAndItsContents() throws Exception {
        crawl("cli-count");
        try (ConsoleCapture console = new ConsoleCapture()) {
            queryCommands.indexInfo();
            String output = console.output();
            assertThat(output).contains("lucene").contains("greenfinger-<catalog id>")
                    .contains("cli-count").contains("v0");
        }
    }

    @Test
    @DisplayName("vector-info says which store is configured even when nothing is in it")
    void vectorInfoReportsTheStore() throws Exception {
        try (ConsoleCapture console = new ConsoleCapture()) {
            queryCommands.vectorInfo();
            String output = console.output();
            assertThat(output).contains("Vector store").contains("lucene")
                    .contains("greenfinger_text").contains("Nothing has been embedded");
        }
    }

    @Test
    @DisplayName("the collection's real name carries the width, so it is found by prefix")
    void vectorInfoCountsTheCollectionThatWasActuallyWritten() throws Exception {
        String id = crawl("cli-vectors");
        // what the writer would have created: the configured name plus the model's width
        VectorStore store = outputFactory.getVectorStore();
        store.afterPropertiesSet();
        try {
            store.ensureCollection("greenfinger_text_4", 4);
            store.upsert("greenfinger_text_4",
                    java.util.List.of(new VectorPoint("chunk-1", new float[] {1f, 0f, 0f, 0f},
                            java.util.Map.of("catalogVersion", id + ":0"))));
        } finally {
            store.destroy();
        }

        try (ConsoleCapture console = new ConsoleCapture()) {
            queryCommands.vectorInfo();
            assertThat(console.output()).contains("greenfinger_text_4").contains("cli-vectors");
        }
    }

    @Test
    @DisplayName("replay rebuilds the index from the database, without fetching anything")
    void replaysIntoTheIndex() throws Exception {
        String id = crawl("cli-replay");
        int before = site.requestedPaths().size();

        try (ConsoleCapture console = new ConsoleCapture()) {
            crawlCommands.dispatch("replay", null, new CrawlOptions()
                    .override("id", id).override("layers", "index"));
            assertThat(console.output()).contains("Replayed");
        }
        assertThat(site.requestedPaths()).hasSize(before);
    }

    @Test
    void testUrlReportsWhatItIsAbout() throws Exception {
        try (ConsoleCapture console = new ConsoleCapture()) {
            crawlCommands.dispatch("test-url", null,
                    new CrawlOptions().override("url", site.url()));
            assertThat(console.output()).contains("Fetching");
        }
        assertThatThrownBy(() -> crawlCommands.dispatch("test-url", null, new CrawlOptions()))
                .isInstanceOf(UsageException.class).hasMessageContaining("Give a url");
    }

    @Test
    void theDispatcherRoutesToTheOtherCommandClasses() throws Exception {
        crawl("cli-route");
        try (ConsoleCapture console = new ConsoleCapture()) {
            crawlCommands.dispatch("catalogs", null, new CrawlOptions());
            assertThat(console.output()).contains("cli-route");
        }
        try (ConsoleCapture console = new ConsoleCapture()) {
            crawlCommands.dispatch("cats", null, new CrawlOptions());
            assertThat(console.output()).contains("other");
        }
        try (ConsoleCapture console = new ConsoleCapture()) {
            crawlCommands.dispatch("index-info", null, new CrawlOptions());
            assertThat(console.output()).contains("cli-route");
        }
        try (ConsoleCapture console = new ConsoleCapture()) {
            crawlCommands.dispatch("search", null,
                    new CrawlOptions().override("query", "alpha").override("size", 3));
            assertThat(console.output()).contains("Page A");
        }
    }

    @Test
    @DisplayName("a primary command carries when the joined two-word form matches nothing")
    void fallsBackToThePrimaryCommand() throws Exception {
        String id = crawl("cli-primary");
        try (ConsoleCapture console = new ConsoleCapture()) {
            crawlCommands.dispatch("versions-nonsense", "versions",
                    new CrawlOptions().override("id", id));
            assertThat(console.output()).contains("v0");
        }
    }

}
