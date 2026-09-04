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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.service.CatalogAdminService;

/**
 * The http face of the crawler, through the real dispatcher.
 * 
 * @Description: WebApiIntegrationTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@SpringBootTest(classes = WebTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {"greenfinger.output.file.directory=${java.io.tmpdir}/gf-web/data",
        "greenfinger.frontier-directory=${java.io.tmpdir}/gf-web/frontier",
        "greenfinger.dedup.url.directory=${java.io.tmpdir}/gf-web/url",
        "greenfinger.dedup.content.directory=${java.io.tmpdir}/gf-web/content",
        "spring.datasource.url=jdbc:h2:mem:greenfinger-web;DB_CLOSE_DELAY=-1",
        // These cases are about what the endpoints do, not about who may call them.
        // Who may is asserted end to end in SecurityIntegrationTest, which runs the door on;
        // turning it off here also exercises the GF_SECURITY_ENABLED=false deployment.
        "greenfinger.security.enabled=false"})
class WebApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CatalogAdminService catalogAdminService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        catalogAdminService.findAll()
                .forEach(catalog -> catalogAdminService.delete(catalog.getId()));
    }

    private Catalog saved(String name) {
        Catalog catalog = new Catalog();
        catalog.setName(name);
        catalog.setUrl("https://" + name + ".example.com");
        return catalogAdminService.save(catalog);
    }

    @Test
    @DisplayName("a url alone is enough to create a catalog over http")
    void savesACatalog() throws Exception {
        Catalog body = new Catalog();
        body.setUrl("https://books.toscrape.com");

        mockMvc.perform(post("/v2/catalog").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("toscrape"))
                .andExpect(jsonPath("$.data.id").isNotEmpty());
    }

    @Test
    void listsCatalogs() throws Exception {
        saved("alpha");
        saved("beta");

        mockMvc.perform(get("/v2/catalog")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void fetchesOneByName() throws Exception {
        saved("gamma");

        mockMvc.perform(get("/v2/catalog/gamma")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://gamma.example.com"));
    }

    @Test
    @DisplayName("details show what the runtime will use, defaults filled in")
    void fetchesTheRuntimeView() throws Exception {
        saved("delta");

        mockMvc.perform(get("/v2/catalog/delta/details")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.searchVersion").value(-1))
                .andExpect(jsonPath("$.data.outputTypes[0]").value("file"));
    }

    @Test
    void listsCategories() throws Exception {
        saved("epsilon");

        mockMvc.perform(get("/v2/catalog/cats")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("default"));
    }

    @Test
    void deletesTheDefinition() throws Exception {
        saved("zeta");

        mockMvc.perform(delete("/v2/catalog/zeta")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
        mockMvc.perform(get("/v2/catalog/zeta")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a missing catalog is a 404 with a readable message, not a stack trace")
    void reportsAMissingCatalog() throws Exception {
        mockMvc.perform(get("/v2/catalog/ghost")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                        "ghost")));
    }

    @Test
    void reportsStatusForEveryCatalog() throws Exception {
        saved("eta");

        mockMvc.perform(get("/v2/crawl/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("eta"))
                .andExpect(jsonPath("$.data[0].running").value(false))
                .andExpect(jsonPath("$.data[0].indexVersion").value(0));
    }

    @Test
    @DisplayName("interrupting something that is not running is a plain false, not an error")
    void interruptsNothingGracefully() throws Exception {
        saved("theta");

        mockMvc.perform(post("/v2/crawl/theta/interrupt")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    @DisplayName("delete defaults to a dry run, because it is the one thing that cannot be undone")
    void deleteIsADryRunByDefault() throws Exception {
        saved("iota");

        mockMvc.perform(delete("/v2/crawl/iota/versions")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void searchNeedsSomethingPublishedFirst() throws Exception {
        saved("kappa");

        mockMvc.perform(get("/v2/search").param("q", "anything")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("finished crawling")));
    }

    @Test
    @DisplayName("meaning and pictures answer the same way, and never reach the vector store")
    void semanticSearchNeedsSomethingPublishedFirst() throws Exception {
        saved("lambda");

        // the guard is what keeps an unconfigured deployment from failing with a connection error
        // where the honest answer is that nothing has been published to search yet
        mockMvc.perform(get("/v2/search/semantic").param("q", "anything")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("finished crawling")));

        mockMvc.perform(get("/v2/search/images").param("q", "a red book")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("finished crawling")));
    }

    @Test
    void runningListIsEmptyWhenNothingRuns() throws Exception {
        saved("lambda");

        mockMvc.perform(get("/v2/catalog/running")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void summaryIsEmptyBeforeTheFirstRun() throws Exception {
        saved("mu");

        mockMvc.perform(get("/v2/catalog/mu/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

}
