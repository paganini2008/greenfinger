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
import com.github.greenfinger.shell.GreenfingerShellMain;
import com.github.greenfinger.shell.TestHttpServer;
import com.github.greenfinger.shell.UsageException;
import com.github.greenfinger.core.WebCrawlerSemaphore;
import com.github.greenfinger.core.catalog.CatalogDetailsNotFoundException;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.service.CatalogAdminService;

/**
 * The commands driven the way the one-line form drives them, against a real database and a real
 * site. What they print is the product, so that is what gets asserted on.
 * 
 * @Description: CommandsIntegrationTest
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
        "spring.datasource.url=jdbc:h2:mem:greenfinger-cli;DB_CLOSE_DELAY=-1",
        "greenfinger.output.file.directory=${java.io.tmpdir}/gf-cli/data",
        "greenfinger.frontier-directory=${java.io.tmpdir}/gf-cli/frontier",
        "greenfinger.dedup.url.directory=${java.io.tmpdir}/gf-cli/url",
        "greenfinger.dedup.content.directory=${java.io.tmpdir}/gf-cli/content",
        "greenfinger.output.index.lucene.directory=${java.io.tmpdir}/gf-cli-lucene",
        "greenfinger.output.vector.lucene.directory=${java.io.tmpdir}/gf-cli-lucene-vector",
        "greenfinger.embedding.preload=false"})
class CommandsIntegrationTest {

    @Autowired
    private CrawlCommands crawlCommands;

    @Autowired
    private CatalogCommands catalogCommands;

    @Autowired
    private QueryCommands queryCommands;

    @Autowired
    private CatalogAdminService catalogAdminService;

    @Autowired
    private WebCrawlerSemaphore semaphore;

    private TestHttpServer site;

    @BeforeEach
    void setUp() throws Exception {
        wipe(Path.of(System.getProperty("java.io.tmpdir"), "gf-cli"));
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
     * A catalog, saved the way {@code catalog-save} saves one. The interview itself needs a
     * terminal, so what it produces is built here instead and the commands under test take the id.
     */
    private String define(String name) {
        Catalog catalog = new Catalog();
        catalog.setName(name);
        catalog.setUrl(site.url());
        catalog.setMaxFetchSize(10);
        catalog.setFetchInterval(0L);
        catalog.setImageEnabled(false);
        return catalogAdminService.save(catalog).getId();
    }

    private String crawl(String name) throws Exception {
        String id = define(name);
        crawlCommands.dispatch("catalog-crawl", null, new CrawlOptions().override("id", id));
        return id;
    }

    @Test
    @DisplayName("a catalog id crawls, and the summary says what happened")
    void crawlsFromAnId() throws Exception {
        String id = define("cli-basic");
        try (ConsoleCapture console = new ConsoleCapture()) {
            crawlCommands.dispatch("catalog-crawl", null, new CrawlOptions().override("id", id));
            assertThat(console.output()).contains("cli-basic").contains("Pages saved");
        }
        assertThat(catalogAdminService.requireById(id).getName()).isEqualTo("cli-basic");
    }

    @Test
    void statusListsWhatHasBeenCrawledWhenNothingIsRunning() throws Exception {
        crawl("cli-status");
        try (ConsoleCapture console = new ConsoleCapture()) {
            crawlCommands.dispatch("status", null, new CrawlOptions());
            assertThat(console.output()).contains("cli-status").contains("Nothing is crawling");
        }
    }

    @Test
    void rebuildStartsANewVersion() throws Exception {
        String id = crawl("cli-rebuild");
        try (ConsoleCapture console = new ConsoleCapture()) {
            crawlCommands.dispatch("rebuild", null, new CrawlOptions().override("id", id));
            assertThat(console.output()).contains("v1");
        }
    }

    @Test
    void updateKeepsTheVersion() throws Exception {
        String id = crawl("cli-update");
        try (ConsoleCapture console = new ConsoleCapture()) {
            crawlCommands.dispatch("update", null, new CrawlOptions().override("id", id));
            assertThat(console.output()).contains("v0");
        }
    }

    @Test
    @DisplayName("resume is an update that does not refresh")
    void resumeContinuesTheSameVersion() throws Exception {
        String id = crawl("cli-resume");
        try (ConsoleCapture console = new ConsoleCapture()) {
            crawlCommands.dispatch("resume", null, new CrawlOptions().override("id", id));
            assertThat(console.output()).contains("resume").contains("v0");
        }
    }

    @Test
    @DisplayName("delete defaults to reporting, and says so")
    void deleteDryRunReportsPerLayer() throws Exception {
        String id = crawl("cli-delete");
        try (ConsoleCapture console = new ConsoleCapture()) {
            crawlCommands.dispatch("delete", null,
                    new CrawlOptions().override("id", id).override("version", 0)
                            .override("layers", "db,file").override("dryRun", true));
            String output = console.output();
            assertThat(output).contains("Would delete").contains("db").contains("file")
                    .contains("Dry run");
        }
    }

    @Test
    @DisplayName("--keep-latest keeps the newest n and removes the rest")
    void deleteKeepingTheNewest() throws Exception {
        String id = crawl("cli-delete-keep");
        crawlCommands.dispatch("rebuild", null, new CrawlOptions().override("id", id));
        try (ConsoleCapture console = new ConsoleCapture()) {
            crawlCommands.dispatch("delete", null, new CrawlOptions().override("id", id)
                    .override("keepLatest", 1).override("dryRun", true));
            assertThat(console.output()).contains("v0").doesNotContain("v1");
        }
    }

    @Test
    @DisplayName("--all empties the catalog and leaves the index standing; --purge drops it")
    void everyVersionWithAndWithoutTheIndex() throws Exception {
        String id = crawl("cli-delete-all");
        try (ConsoleCapture console = new ConsoleCapture()) {
            // --force because the only version there is, is the one search is serving: removing
            // everything a catalog has is still not something to do by accident
            crawlCommands.dispatch("delete", null, new CrawlOptions().override("id", id)
                    .override("all", true).override("force", true));
            assertThat(console.output()).contains("Deleted")
                    .contains("the index is still there, empty");
        }

        String other = crawl("cli-delete-purge");
        try (ConsoleCapture console = new ConsoleCapture()) {
            crawlCommands.dispatch("delete", null, new CrawlOptions().override("id", other)
                    .override("purge", true).override("force", true));
            assertThat(console.output()).contains("index was dropped")
                    .contains("catalog-delete --id=" + other);
        }
    }

    @Test
    void deleteNeedsToBeToldWhichVersions() throws Exception {
        String id = crawl("cli-delete-args");
        assertThatThrownBy(() -> crawlCommands.dispatch("delete", null,
                new CrawlOptions().override("id", id))).isInstanceOf(UsageException.class)
                        .hasMessageContaining("--keep-latest").hasMessageContaining("--purge");
    }

    @Test
    void pausingSomethingIdleSaysSo() throws Exception {
        String id = crawl("cli-pause");
        try (ConsoleCapture console = new ConsoleCapture()) {
            crawlCommands.dispatch("pause", null, new CrawlOptions().override("id", id));
            assertThat(console.output()).contains("not running");
        }
    }

    @Test
    @DisplayName("a catalog is addressed by id, and a name is not one")
    void refusesAnythingThatIsNotAnId() {
        define("cli-by-name");
        assertThatThrownBy(
                () -> crawlCommands.dispatch("catalog-crawl", null, new CrawlOptions()))
                        .isInstanceOf(CatalogDetailsNotFoundException.class)
                        .hasMessageContaining("catalog id");
        assertThatThrownBy(() -> crawlCommands.dispatch("catalog-crawl", null,
                new CrawlOptions().override("id", "cli-by-name")))
                        .isInstanceOf(CatalogDetailsNotFoundException.class)
                        .hasMessageContaining("catalog-list");
    }

    @Test
    void helpAndOptionsListEverything() {
        try (ConsoleCapture console = new ConsoleCapture()) {
            crawlCommands.help();
            String output = console.output();
            assertThat(output).contains("catalog-crawl").contains("rebuild").contains("delete")
                    .contains("replay").contains("versions").contains("crawler-report")
                    .contains("greenfinger-cli.sh");
        }
        try (ConsoleCapture console = new ConsoleCapture()) {
            crawlCommands.options();
            String output = console.output();
            assertThat(output).contains("max-size").contains("output-types").contains("content")
                    .contains("text+image").contains("adaptive");
        }
    }

    @Test
    void anUnknownCommandSaysSoAndPointsAtHelp() {
        assertThatThrownBy(() -> crawlCommands.dispatch("nonsense", null, new CrawlOptions()))
                .isInstanceOf(UsageException.class)
                .hasMessageContaining("Unknown command")
                .hasMessageContaining("nonsense")
                .hasMessageContaining("help");
    }

    @Test
    void catalogCommandsListAndShow() throws Exception {
        String id = crawl("cli-catalog");
        try (ConsoleCapture console = new ConsoleCapture()) {
            catalogCommands.list();
            assertThat(console.output()).contains("cli-catalog").contains("Outputs").contains(id);
        }
        try (ConsoleCapture console = new ConsoleCapture()) {
            catalogCommands.show(id);
            String output = console.output();
            assertThat(output).contains("Start url").contains("Extractor").contains("Keep versions")
                    .contains("Counted by").contains("Url dedup");
            // what the last run did is crawler-report's job now, and this points at it rather
            // than printing a second, shorter version of the same thing
            assertThat(output).doesNotContain("Last run").contains("crawler-report --id=" + id);
        }
        try (ConsoleCapture console = new ConsoleCapture()) {
            catalogCommands.categories();
            assertThat(console.output()).contains("default");
        }
    }

    @Test
    @DisplayName("versions lists what a catalog has, and the report is stored beside it")
    void versionsAndTheStoredReport() throws Exception {
        String id = crawl("cli-versions");
        try (ConsoleCapture console = new ConsoleCapture()) {
            crawlCommands.dispatch("versions", null, new CrawlOptions().override("id", id));
            assertThat(console.output()).contains("v0").contains("searchable")
                    .contains("crawler-report");
        }
        try (ConsoleCapture console = new ConsoleCapture()) {
            crawlCommands.dispatch("crawler-report", null, new CrawlOptions().override("id", id));
            String output = console.output();
            assertThat(output).contains("cli-versions").contains("dashboard").contains("cluster")
                    .contains("database").contains("storage").contains("settings");
        }
    }

    @Test
    void deletingACatalogTakesItOffTheList() throws Exception {
        String id = define("cli-catalog-delete");
        try (ConsoleCapture console = new ConsoleCapture()) {
            catalogCommands.deleteCatalog(id);
            assertThat(console.output()).contains("Deleted");
        }
        assertThatThrownBy(() -> catalogAdminService.requireById(id))
                .isInstanceOf(CatalogDetailsNotFoundException.class);
    }

    @Test
    @DisplayName("catalog-show without an id shows the running crawl, and says so when none is")
    void catalogShowWithoutAnIdMeansTheRunningOne() {
        // nothing is crawling in this test, so the answer is what to type instead -- and not the
        // "no catalog with id null" the lookup would have produced
        assertThatThrownBy(() -> catalogCommands.show(null))
                .isInstanceOf(com.github.greenfinger.shell.UsageException.class)
                .hasMessageContaining("Nothing is crawling");
    }

    @Test
    void catalogCommandsAskForAnId() {
        assertThatThrownBy(() -> catalogCommands.deleteCatalog("  "))
                .isInstanceOf(CatalogDetailsNotFoundException.class)
                .hasMessageContaining("catalog id");
    }

    @Test
    void listingAnEmptyStoreSaysHowToStart() {
        try (ConsoleCapture console = new ConsoleCapture()) {
            catalogCommands.list();
            assertThat(console.output()).contains("No catalogs yet");
        }
    }

    @Test
    @DisplayName("search needs something published before it can look")
    void searchWithNothingCrawled() throws Exception {
        try (ConsoleCapture console = new ConsoleCapture()) {
            queryCommands.search("anything", null, 5, null);
            assertThat(console.output()).contains("finished crawling");
        }
        assertThatThrownBy(() -> queryCommands.search(null, null, 5, null))
                .isInstanceOf(UsageException.class)
                .hasMessageContaining("Give something to search for");
    }

    @Test
    void theSemaphoreIsReleasedAfterEveryRun() throws Exception {
        crawl("cli-semaphore");
        assertThat(semaphore.isOccupied()).isFalse();
    }

}
