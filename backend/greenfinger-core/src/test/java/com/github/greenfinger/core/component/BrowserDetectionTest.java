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

package com.github.greenfinger.core.component;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.WebCrawlerConstants;

/**
 * The names the browser check looks for have to be findable.
 *
 * <p>
 * They were the simple names -- "WebClient", "Playwright", "WebDriver" -- which
 * {@code Class.forName} resolves against the default package and never finds. Every installation
 * was therefore told it had no browser, and adaptive crawled plain http even with all three
 * engines on the classpath. The bug was invisible: a shell page is stored, not refused.
 *
 * @Description: BrowserDetectionTest
 * @Author: Fred Feng
 * @Date: 27/09/2026
 * @Version 2.0.0
 */
class BrowserDetectionTest {

    @Test
    @DisplayName("every engine the factory checks for is a class that can be loaded")
    void theNamesResolve() throws Exception {
        for (String className : new String[] {WebCrawlerConstants.CLASS_HTMLUNIT,
                WebCrawlerConstants.CLASS_PLAYWRIGHT, WebCrawlerConstants.CLASS_SELENIUM}) {
            assertThat(className).as("a simple name resolves against the default package and can"
                    + " only fail").contains(".");
            assertThat(Class.forName(className, false, getClass().getClassLoader())).isNotNull();
        }
    }

    @Test
    @DisplayName("the fallback order names engines the factory knows")
    void theFallbackOrderIsReal() {
        assertThat(WebCrawlerConstants.BROWSER_FALLBACK_ORDER).isNotEmpty()
                .allSatisfy(engine -> assertThat(engine)
                        .isIn(WebCrawlerConstants.ENGINE_HTMLUNIT,
                                WebCrawlerConstants.ENGINE_PLAYWRIGHT,
                                WebCrawlerConstants.ENGINE_SELENIUM));
    }

}
