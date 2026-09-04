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

package com.github.greenfinger.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.github.greenfinger.core.catalog.CatalogDetailsService;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.service.CatalogAdminService;

/**
 * The crawl endpoints, driven end to end: start a crawl over http, wait for it, then delete and
 * replay what it produced.
 * 
 * @Description: CrawlApiIntegrationTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@SpringBootTest(classes = WebTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {"greenfinger.output.file.directory=${java.io.tmpdir}/gf-api/data",
        "greenfinger.frontier-directory=${java.io.tmpdir}/gf-api/frontier",
        "greenfinger.dedup.url.directory=${java.io.tmpdir}/gf-api/url",
        "greenfinger.dedup.content.directory=${java.io.tmpdir}/gf-api/content",
        "spring.datasource.url=jdbc:h2:mem:greenfinger-api;DB_CLOSE_DELAY=-1",
        // these fixtures are a handful of pages on localhost; a real deployment waits two
        // minutes before believing the counters have stopped
        "greenfinger.completion-check-interval=200ms", "greenfinger.idle-timeout=600ms",
        // These cases are about what the endpoints do, not about who may call them.
        // Who may is asserted end to end in SecurityIntegrationTest, which runs the door on;
        // turning it off here also exercises the GF_SECURITY_ENABLED=false deployment.
        "greenfinger.security.enabled=false"})
class CrawlApiIntegrationTest {

    private static StubJsonServer elasticsearch;

    @BeforeAll
    static void startStub() throws Exception {
        elasticsearch = new StubJsonServer();
        elasticsearch.on("GET", "/webcrawler_resource", 200, "{}")
                .on("PUT", "/webcrawler_resource", 200, "{\"acknowledged\":true}")
                .on("POST", "/webcrawler_resource/_bulk", 200, "{\"errors\":false,\"items\":[]}")
                .on("POST", "/webcrawler_resource/_refresh", 200, "{}")
                .on("POST", "/webcrawler_resource/_count", 200, "{\"count\":2}")
                .on("POST", "/webcrawler_resource/_delete_by_query", 200, "{\"deleted\":2}");
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
    private MockMvc mockMvc;

    @Autowired
    private CatalogAdminService catalogAdminService;

    @Autowired
    private CatalogDetailsService catalogDetailsService;

    @Autowired
    private ResourceRecordStore recordStore;

    private LocalSite site;

    @Autowired
    private com.github.greenfinger.core.WebCrawlerSemaphore semaphore;

    @BeforeEach
    void setUp() throws Exception {
        // the endpoints hand the crawl to a background thread, so the previous test may still be
        // finishing; wiping its RocksDB stores underneath it would wedge the next one
        await().atMost(Duration.ofSeconds(30)).until(() -> !semaphore.isOccupied());
        wipe(Path.of(System.getProperty("java.io.tmpdir"), "gf-api"));
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

    private Catalog saved(String name, Set<OutputType> outputs) {
        Catalog catalog = new Catalog();
        catalog.setName(name);
        catalog.setUrl(site.baseUrl());
        catalog.setStartUrl(site.baseUrl());
        catalog.setPathPattern(site.baseUrl() + "/**");
        catalog.setMaxFetchSize(10);
        catalog.setFetchInterval(0L);
        catalog.setImageEnabled(false);
        catalog.setOutputTypes(outputs);
        return catalogAdminService.save(catalog);
    }

    private void awaitFinished(String id, int version) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
                .until(() -> catalogDetailsService.loadCatalogDetails(id)
                        .getSearchVersion() >= version);
    }

    @Test
    @DisplayName("a crawl started over http runs in the background and reports back through status")
    void startsACrawl() throws Exception {
        Catalog catalog = saved("api-crawl", Set.of(OutputType.FILE));

        mockMvc.perform(post("/v2/crawl/api-crawl")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers
                        .containsString("crawl of 'api-crawl' started")));

        awaitFinished(catalog.getId(), 0);
        assertThat(recordStore.countByCatalog(catalog.getId(), 0)).isEqualTo(2);
    }

    @Test
    void rebuildStartsANewVersion() throws Exception {
        Catalog catalog = saved("api-rebuild", Set.of(OutputType.FILE));
        mockMvc.perform(post("/v2/crawl/api-rebuild"));
        awaitFinished(catalog.getId(), 0);

        mockMvc.perform(post("/v2/crawl/api-rebuild/rebuild")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        awaitFinished(catalog.getId(), 1);
        assertThat(recordStore.findVersions(catalog.getId())).containsExactly(0, 1);
    }

    @Test
    void updateKeepsTheVersion() throws Exception {
        Catalog catalog = saved("api-update", Set.of(OutputType.FILE));
        mockMvc.perform(post("/v2/crawl/api-update"));
        awaitFinished(catalog.getId(), 0);

        mockMvc.perform(post("/v2/crawl/api-update/update").param("from", site.baseUrl()))
                .andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(30)).until(
                () -> !"crawl".equals(catalogAdminService.require("api-update").getRunningState()));
        assertThat(catalogDetailsService.loadCatalogDetails(catalog.getId()).getVersion())
                .isZero();
    }

    @Test
    @DisplayName("status carries the live counters while a crawl is in flight")
    void statusReportsCounters() throws Exception {
        Catalog catalog = saved("api-status", Set.of(OutputType.FILE));
        mockMvc.perform(post("/v2/crawl/api-status"));
        awaitFinished(catalog.getId(), 0);

        mockMvc.perform(get("/v2/crawl/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("api-status"))
                .andExpect(jsonPath("$.data[0].savedResourceCount").value(2))
                .andExpect(jsonPath("$.data[0].searchVersion").value(0));
    }

    @Test
    @DisplayName("delete says which stores it touched, and refuses the published version")
    void deletesAVersion() throws Exception {
        Catalog catalog = saved("api-delete", Set.of(OutputType.FILE));
        mockMvc.perform(post("/v2/crawl/api-delete"));
        awaitFinished(catalog.getId(), 0);

        mockMvc.perform(delete("/v2/crawl/api-delete/versions").param("version", "0")
                .param("layers", "db,file").param("dryRun", "true")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // still there: that was a dry run
        assertThat(recordStore.countByCatalog(catalog.getId(), 0)).isEqualTo(2);

        mockMvc.perform(delete("/v2/crawl/api-delete/versions").param("version", "0")
                .param("layers", "db,file").param("dryRun", "false")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(delete("/v2/crawl/api-delete/versions").param("version", "0")
                .param("layers", "db,file").param("dryRun", "false").param("force", "true"))
                .andExpect(status().isOk());
        assertThat(recordStore.countByCatalog(catalog.getId(), 0)).isZero();
    }

    @Test
    void deleteWithNoVersionsSelectedIsEmpty() throws Exception {
        saved("api-delete-empty", Set.of(OutputType.FILE));

        mockMvc.perform(delete("/v2/crawl/api-delete-empty/versions").param("keepLatest", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void replaysIntoTheIndex() throws Exception {
        Catalog catalog = saved("api-replay", Set.of(OutputType.FILE));
        mockMvc.perform(post("/v2/crawl/api-replay"));
        awaitFinished(catalog.getId(), 0);

        mockMvc.perform(post("/v2/crawl/api-replay/replay").param("layers", "index"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(2));
    }

    @Test
    void searchesWhatWasPublished() throws Exception {
        Catalog catalog = saved("api-search", Set.of(OutputType.FILE));
        mockMvc.perform(post("/v2/crawl/api-search"));
        awaitFinished(catalog.getId(), 0);

        elasticsearch.on("POST", "/webcrawler_resource/_search", 200,
                "{\"hits\":{\"total\":{\"value\":1},\"hits\":[{\"_id\":\"x\",\"_score\":1.0,"
                        + "\"_source\":{\"title\":\"Page A\",\"url\":\"http://a\"}}]}}");

        mockMvc.perform(get("/v2/search").param("q", "alpha")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

}
