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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.hamcrest.Matchers;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.service.CatalogAdminService;

/**
 * The two ways a search can have nothing to look in, which are two different situations.
 *
 * <p>
 * "Nothing has finished crawling yet" is about the installation: no catalog anywhere has a version
 * being served, and the answer is to crawl something. Naming a catalog that has no version is the
 * other one, and saying the same sentence there reads as though search were broken, when every
 * other catalog is searchable. The wording was split for that reason and nothing held it in place.
 *
 * @Description: SearchNothingToSearchTest
 * @Author: Fred Feng
 * @Date: 27/09/2026
 * @Version 2.0.0
 */
@SpringBootTest(classes = WebTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {"greenfinger.output.file.directory=${java.io.tmpdir}/gf-nothing/data",
                "greenfinger.frontier-directory=${java.io.tmpdir}/gf-nothing/frontier",
                "greenfinger.dedup.url.directory=${java.io.tmpdir}/gf-nothing/url",
                "greenfinger.dedup.content.directory=${java.io.tmpdir}/gf-nothing/content",
                "spring.datasource.url=jdbc:h2:mem:greenfinger-nothing;DB_CLOSE_DELAY=-1",
                "greenfinger.security.enabled=false"})
class SearchNothingToSearchTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CatalogAdminService catalogAdminService;

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
    @DisplayName("nothing anywhere has been crawled: the installation is what is empty")
    void theWholeInstallationIsEmpty() throws Exception {
        mockMvc.perform(get("/v2/search").param("q", "anything")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Nothing has finished crawling yet"));
    }

    @Test
    @DisplayName("a named catalog with no version is named, and other catalogs are not implicated")
    void oneCatalogHasNoVersion() throws Exception {
        saved("alpha");

        mockMvc.perform(get("/v2/search").param("q", "anything").param("catalog", "alpha"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message",
                        Matchers.allOf(Matchers.containsString("'alpha'"),
                                Matchers.containsString("no version being served yet"),
                                Matchers.containsString("other catalogs are unaffected"))));
    }

    @Test
    @DisplayName("naming a catalog does not fall back to the installation-wide wording")
    void theTwoAreNotInterchangeable() throws Exception {
        saved("alpha");
        saved("beta");

        mockMvc.perform(get("/v2/search").param("q", "anything").param("catalog", "beta"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message",
                        Matchers.not(Matchers.containsString("Nothing has finished crawling yet"))));
    }

    @Test
    @DisplayName("the meaning search says the same two things, because it is the same question")
    void semanticSearchSaysItToo() throws Exception {
        mockMvc.perform(get("/v2/search/semantic").param("q", "anything"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Nothing has finished crawling yet"));

        saved("alpha");
        mockMvc.perform(get("/v2/search/semantic").param("q", "anything").param("catalog", "alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", Matchers.containsString("'alpha'")));
    }
}
