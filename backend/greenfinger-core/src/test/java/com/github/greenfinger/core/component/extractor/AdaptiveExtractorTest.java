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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawlTask;

/**
 * 
 * @Description: AdaptiveExtractorTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
class AdaptiveExtractorTest {

    /** Returns whatever it was primed with, and counts how often it was asked. */
    private static class CannedExtractor implements NamedExtractor {

        private final String name;
        private final String html;
        private final RuntimeException failure;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicInteger started = new AtomicInteger();

        CannedExtractor(String name, String html) {
            this(name, html, null);
        }

        CannedExtractor(String name, String html, RuntimeException failure) {
            this.name = name;
            this.html = html;
            this.failure = failure;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String extractHtml(CatalogDetails catalogDetails, String referUrl, String url,
                Charset pageEncoding, CrawlTask task) {
            calls.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return html;
        }
    }

    private static final String SHELL =
            "<html><body><div id='root'></div><script src='/a.js'></script></body></html>";

    private static final String RENDERED = "<html><body><div id='root'><article><p>"
            + ("Rendered prose that only appeared once the page ran its javascript. ".repeat(6))
            + "</p></article></div></body></html>";

    private static final String SERVED = "<html><body><article><p>"
            + ("Ordinary prose that arrived with the page and needed nothing at all. ".repeat(6))
            + "</p></article></body></html>";

    private String fetch(AdaptiveExtractor extractor) throws Exception {
        return extractor.extractHtml(null, "https://a.com", "https://a.com/x",
                StandardCharsets.UTF_8, null);
    }

    private AdaptiveExtractor adaptive(CannedExtractor fast, String browserName,
            CannedExtractor browser) {
        return new AdaptiveExtractor(fast, browserName, () -> browser,
                new RenderingDetector(400, 120));
    }

    @Test
    @DisplayName("a page that arrived whole never starts a browser at all")
    void doesNotStartABrowserForAStaticPage() throws Exception {
        CannedExtractor fast = new CannedExtractor("restclient", SERVED);
        CannedExtractor browser = new CannedExtractor("htmlunit", RENDERED);

        AdaptiveExtractor extractor = adaptive(fast, "htmlunit", browser);
        assertThat(fetch(extractor)).isEqualTo(SERVED);

        assertThat(browser.calls.get()).isZero();
        assertThat(extractor.getRendered().get()).isZero();
        assertThat(extractor.getFetched().get()).isEqualTo(1);
    }

    @Test
    @DisplayName("an unrendered shell is fetched again through the browser")
    void rendersAnEmptyShell() throws Exception {
        CannedExtractor fast = new CannedExtractor("restclient", SHELL);
        CannedExtractor browser = new CannedExtractor("htmlunit", RENDERED);

        AdaptiveExtractor extractor = adaptive(fast, "htmlunit", browser);
        assertThat(fetch(extractor)).isEqualTo(RENDERED);

        assertThat(browser.calls.get()).isEqualTo(1);
        assertThat(extractor.getRendered().get()).isEqualTo(1);
    }

    @Test
    @DisplayName("all three browser engines work as the fallback")
    void anyBrowserEngineCanBeTheFallback() throws Exception {
        for (String engine : new String[] {"htmlunit", "playwright", "selenium"}) {
            CannedExtractor fast = new CannedExtractor("restclient", SHELL);
            CannedExtractor browser = new CannedExtractor(engine, RENDERED);

            AdaptiveExtractor extractor = adaptive(fast, engine, browser);

            assertThat(fetch(extractor)).isEqualTo(RENDERED);
            assertThat(browser.calls.get()).as(engine).isEqualTo(1);
            assertThat(extractor.getName()).isEqualTo("adaptive(" + engine + ")");
        }
    }

    @Test
    @DisplayName("the browser is started once and reused, not per page")
    void startsTheBrowserOnlyOnce() throws Exception {
        AtomicInteger created = new AtomicInteger();
        CannedExtractor fast = new CannedExtractor("restclient", SHELL);
        CannedExtractor browser = new CannedExtractor("selenium", RENDERED);

        AdaptiveExtractor extractor = new AdaptiveExtractor(fast, "selenium", () -> {
            created.incrementAndGet();
            return browser;
        }, new RenderingDetector(400, 120));

        fetch(extractor);
        fetch(extractor);
        fetch(extractor);

        assertThat(created.get()).isEqualTo(1);
        assertThat(browser.calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("a browser that fails is not fatal: what http returned is still a page")
    void survivesABrokenBrowser() throws Exception {
        CannedExtractor fast = new CannedExtractor("restclient", SHELL);
        CannedExtractor browser = new CannedExtractor("playwright", null,
                new IllegalStateException("no browser installed"));

        AdaptiveExtractor extractor = adaptive(fast, "playwright", browser);

        assertThat(fetch(extractor)).isEqualTo(SHELL);
        assertThat(extractor.getRendered().get()).isZero();
    }

    @Test
    @DisplayName("a render that produced no more than the plain fetch is not counted as one")
    void doesNotCountAPointlessRender() throws Exception {
        CannedExtractor fast = new CannedExtractor("restclient", SHELL);
        // the browser gave back the same shell: the page really is that empty
        CannedExtractor browser = new CannedExtractor("htmlunit", SHELL);

        AdaptiveExtractor extractor = adaptive(fast, "htmlunit", browser);

        assertThat(fetch(extractor)).isEqualTo(SHELL);
        assertThat(extractor.getRendered().get()).isZero();
    }

    @Test
    void countsWhatItDid() throws Exception {
        CannedExtractor fast = new CannedExtractor("restclient", SHELL);
        CannedExtractor browser = new CannedExtractor("htmlunit", RENDERED);
        AdaptiveExtractor extractor = adaptive(fast, "htmlunit", browser);

        fetch(extractor);
        fetch(extractor);

        assertThat(extractor.getFetched().get()).isEqualTo(2);
        assertThat(extractor.getRendered().get()).isEqualTo(2);
        extractor.destroy();
    }

}
