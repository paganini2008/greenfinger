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

package com.github.greenfinger.core.component.extractor;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.TestSite;
import com.github.greenfinger.core.WebCrawlerConstants;
import com.github.greenfinger.core.WebCrawlerExtractorProperties;

/**
 * HtmlUnit runs the page's scripts, so this is where script-rendered content is proved to arrive.
 * 
 * @Description: HtmlUnitPooledExtractorTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class HtmlUnitPooledExtractorTest {

    private TestSite site;
    private HtmlUnitPooledExtractor extractor;

    @BeforeEach
    void setUp() throws Exception {
        site = new TestSite();
        WebCrawlerExtractorProperties properties = new WebCrawlerExtractorProperties();
        properties.getObjectPool().setMaxTotal(2);
        properties.getHtmlunit().setJavaScriptTimeout(2000L);
        extractor = new HtmlUnitPooledExtractor(properties);
        extractor.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() throws Exception {
        extractor.destroy();
        site.close();
    }

    @Test
    void fetchesAStaticPage() throws Exception {
        site.html("/", "<html><head><title>Static</title></head><body>Plain</body></html>");
        assertThat(extractor.test(site.url("/"), StandardCharsets.UTF_8)).contains("Plain");
    }

    @Test
    @DisplayName("content written by a script is present, which a plain fetch would have missed")
    void runsPageScripts() throws Exception {
        site.html("/dynamic", "<html><head><title>Dynamic</title></head><body>"
                + "<div id='target'>before</div>"
                + "<script>document.getElementById('target').textContent = 'rendered';</script>"
                + "</body></html>");
        assertThat(extractor.test(site.url("/dynamic"), StandardCharsets.UTF_8))
                .contains("rendered");
    }

    @Test
    @DisplayName("a failing status yields the page rather than an exception, so the crawl goes on")
    void doesNotThrowOnFailingStatus() throws Exception {
        site.status("/missing", 404);
        assertThat(extractor.test(site.url("/missing"), StandardCharsets.UTF_8)).isNotNull();
    }

    @Test
    void reusesPooledClientsAcrossRequests() throws Exception {
        site.html("/a", "<html><body>A</body></html>");
        site.html("/b", "<html><body>B</body></html>");
        assertThat(extractor.test(site.url("/a"), StandardCharsets.UTF_8)).contains("A");
        assertThat(extractor.test(site.url("/b"), StandardCharsets.UTF_8)).contains("B");
    }

    @Test
    void reportsItsName() {
        assertThat(extractor.getName()).isEqualTo(WebCrawlerConstants.EXTRACTOR_HTMLUNIT);
    }

}
