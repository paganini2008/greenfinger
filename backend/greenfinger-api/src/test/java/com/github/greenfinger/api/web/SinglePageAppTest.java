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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Serving the front end.
 *
 * <p>
 * The case that matters is the reload: /catalogs is a route the browser owns, not a file, and
 * without the fallback every refresh on a real page would 404. The other half matters just as
 * much -- an api path that does not exist must stay a 404, or a typo in a url comes back as html
 * the caller cannot parse.
 *
 * @Description: SinglePageAppTest
 * @Author: Fred Feng
 * @Date: 31/08/2026
 * @Version 2.0.0
 */
@SpringBootTest(classes = WebTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {"greenfinger.output.file.directory=${java.io.tmpdir}/gf-spa/data",
        "greenfinger.frontier-directory=${java.io.tmpdir}/gf-spa/frontier",
        "greenfinger.dedup.url.directory=${java.io.tmpdir}/gf-spa/url",
        "greenfinger.dedup.content.directory=${java.io.tmpdir}/gf-spa/content",
        "spring.datasource.url=jdbc:h2:mem:greenfinger-spa;DB_CLOSE_DELAY=-1",
        "greenfinger.security.enabled=false"})
class SinglePageAppTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("the root is the welcome page, which Spring forwards to index.html")
    void servesThePageAtTheRoot() throws Exception {
        // a forward, not a body: the container renders it, and MockMvc records where it went
        mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
    }

    @Test
    void servesTheBuiltFilesThemselves() throws Exception {
        mockMvc.perform(get("/index.html")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<app-root>")));
    }

    @Test
    @DisplayName("a reload on a route the browser owns still gets the page")
    void servesThePageForADeepLink() throws Exception {
        mockMvc.perform(get("/catalogs")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<app-root>")));
        mockMvc.perform(get("/catalogs/books/monitor")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<app-root>")));
    }

    @Test
    @DisplayName("an api path that does not exist stays a 404, and never becomes a page")
    void doesNotAnswerApiPathsWithThePage() throws Exception {
        mockMvc.perform(get("/v2/not-an-endpoint")).andExpect(status().isNotFound());
    }

}
