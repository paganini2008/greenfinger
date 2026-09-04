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
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.jsoup.Jsoup;
import com.github.greenfinger.core.TestSite;
import com.github.greenfinger.core.WebCrawlerExtractorProperties;
import com.github.greenfinger.core.utils.BeanLifeCycleUtils;

/**
 * The browser engines against a page that only renders once its javascript has run.
 *
 * <p>
 * Off unless asked for. HtmlUnit ships its own engine, but Playwright needs its browsers downloaded
 * and Selenium needs one installed on the machine, so a plain {@code mvn test} on a build agent
 * would fail for reasons that have nothing to do with the crawler. Run it with:
 *
 * <pre>
 * mvn test -Dgreenfinger.browsers=true
 * </pre>
 * 
 * @Description: BrowserExtractorIT
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@EnabledIfSystemProperty(named = "greenfinger.browsers", matches = "true")
class BrowserExtractorIT {

    /**
     * The shape a template renders into: nothing in the markup, everything written by a script.
     */
    private static final String DEFERRED = """
            <html><body>
              <div id="content"></div>
              <script>
                document.getElementById('content').innerHTML =
                  '<article><p>This sentence exists only after the javascript has run, which is'
                  + ' precisely what a browser engine is for.</p></article>';
              </script>
            </body></html>
            """;

    private TestSite site;
    private WebCrawlerExtractorProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        site = new TestSite();
        site.html("/deferred", DEFERRED);
        properties = new WebCrawlerExtractorProperties();
    }

    @AfterEach
    void tearDown() {
        site.close();
    }

    private Extractor engine(String name) {
        return switch (name) {
            case "htmlunit" -> new HtmlUnitPooledExtractor(properties);
            case "playwright" -> new PlaywrightPooledExtractor(properties);
            case "selenium" -> new SeleniumExtractor(properties);
            default -> throw new IllegalArgumentException(name);
        };
    }

    @ParameterizedTest(name = "{0} runs the page''s javascript")
    @ValueSource(strings = {"htmlunit", "playwright", "selenium"})
    @DisplayName("every browser engine renders what plain http cannot")
    void rendersDeferredContent(String engineName) throws Exception {
        Extractor extractor = engine(engineName);
        BeanLifeCycleUtils.afterPropertiesSet(extractor);
        try {
            String html = extractor.extractHtml(null, site.baseUrl(), site.url("/deferred"),
                    StandardCharsets.UTF_8, null);
            String text = Jsoup.parse(html).body().text();

            assertThat(text).as(engineName).contains("only after the javascript has run");
        } finally {
            BeanLifeCycleUtils.destroyQuietly(extractor);
        }
    }

    @ParameterizedTest(name = "adaptive falls back to {0}")
    @ValueSource(strings = {"htmlunit", "playwright", "selenium"})
    @DisplayName("adaptive detects the unrendered page and hands it to the configured browser")
    void adaptiveFallsBackToEachEngine(String engineName) throws Exception {
        AdaptiveExtractor extractor = new AdaptiveExtractor(new RestClientExtractor(properties),
                engineName, () -> engine(engineName), new RenderingDetector(400, 120));
        BeanLifeCycleUtils.afterPropertiesSet(extractor);
        try {
            String html = extractor.extractHtml(null, site.baseUrl(), site.url("/deferred"),
                    StandardCharsets.UTF_8, null);

            assertThat(Jsoup.parse(html).body().text()).as(engineName)
                    .contains("only after the javascript has run");
            assertThat(extractor.getRendered().get()).as(engineName).isEqualTo(1);
        } finally {
            BeanLifeCycleUtils.destroyQuietly(extractor);
        }
    }

}
