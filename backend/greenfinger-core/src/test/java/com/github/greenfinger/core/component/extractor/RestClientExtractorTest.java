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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.TestSite;
import com.github.greenfinger.core.WebCrawlerConstants;
import com.github.greenfinger.core.WebCrawlerExtractorProperties;

/**
 * 
 * @Description: RestClientExtractorTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class RestClientExtractorTest {

    private TestSite site;
    private RestClientExtractor extractor;

    @BeforeEach
    void setUp() throws Exception {
        site = new TestSite();
        extractor = new RestClientExtractor(new WebCrawlerExtractorProperties());
        extractor.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() throws Exception {
        extractor.destroy();
        site.close();
    }

    @Test
    void fetchesThePage() throws Exception {
        site.html("/", "<html><head><title>Hello</title></head><body>World</body></html>");
        String html = extractor.test(site.url("/"), StandardCharsets.UTF_8);
        assertThat(html).contains("<title>Hello</title>", "World");
    }

    @Test
    @DisplayName("a non-2xx response is a failure the caller can inspect")
    void reportsHttpFailures() {
        site.status("/gone", 410);
        assertThatThrownBy(() -> extractor.test(site.url("/gone"), StandardCharsets.UTF_8))
                .isInstanceOf(ExtractorException.class)
                .hasMessageContaining("410");
    }

    @Test
    void missingPagesAreFailuresToo() {
        assertThatThrownBy(() -> extractor.test(site.url("/nope"), StandardCharsets.UTF_8))
                .isInstanceOf(ExtractorException.class);
    }

    @Test
    @DisplayName("a response declaring its charset is decoded with that charset, not UTF-8")
    void honoursTheResponseCharset() throws Exception {
        String chinese = "<html><body>\u4e2d\u6587\u5185\u5bb9</body></html>";
        Charset gbk = Charset.forName("GBK");
        site.page("/gbk", "text/html; charset=GBK", chinese.getBytes(gbk), 200);

        String html = extractor.test(site.url("/gbk"), StandardCharsets.UTF_8);
        assertThat(html).contains("\u4e2d\u6587\u5185\u5bb9");
    }

    @Test
    @DisplayName("with no charset declared, the catalog's page encoding is used")
    void fallsBackToTheConfiguredEncoding() throws Exception {
        String chinese = "<html><body>\u4e2d\u6587\u5185\u5bb9</body></html>";
        Charset gbk = Charset.forName("GBK");
        site.page("/nocharset", "text/html", chinese.getBytes(gbk), 200);

        assertThat(extractor.test(site.url("/nocharset"), gbk)).contains("\u4e2d\u6587\u5185\u5bb9");
    }

    @Test
    void reportsItsName() {
        assertThat(extractor.getName()).isEqualTo(WebCrawlerConstants.EXTRACTOR_RESTCLIENT);
    }

    @Test
    @DisplayName("a rewriter can alter the html before anything parses it")
    void appliesResponseBodyRewriters() throws Exception {
        site.html("/rewrite", "<html><body>original</body></html>");
        extractor.setResponseBodyRewriters(java.util.List
                .of((catalog, refer, url, encoding, content) -> content.replace("original",
                        "rewritten")));

        assertThat(extractor.test(site.url("/rewrite"), StandardCharsets.UTF_8))
                .contains("rewritten").doesNotContain("original");
    }

}
