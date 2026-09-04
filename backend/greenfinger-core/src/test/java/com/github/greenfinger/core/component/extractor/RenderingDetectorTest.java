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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 
 * @Description: RenderingDetectorTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
class RenderingDetectorTest {

    private final RenderingDetector detector = new RenderingDetector(400, 120);

    private static String prose() {
        return ("A paragraph of ordinary prose that arrived with the page and needs no javascript "
                + "to be readable at all. ").repeat(5);
    }

    @Test
    @DisplayName("a page that arrived readable is never re-fetched, whatever else it contains")
    void leavesAServedPageAlone() {
        String html = "<html><body><div id='root'><p>" + prose() + "</p></div>"
                + "<script src='/app.js'></script><script>var a=1;</script>"
                + "<script>var b=2;</script></body></html>";

        assertThat(detector.needsRendering(html)).isFalse();
    }

    @Test
    @DisplayName("React's empty mount point")
    void detectsAnEmptyReactShell() {
        assertThat(detector.needsRendering(
                "<html><body><div id='root'></div><script src='/bundle.js'></script></body></html>"))
                        .isTrue();
    }

    @Test
    void detectsVueAndNextAndNuxtShells() {
        assertThat(detector.needsRendering("<html><body><div id='app'></div></body></html>"))
                .isTrue();
        assertThat(detector.needsRendering("<html><body><div id='__next'></div></body></html>"))
                .isTrue();
        assertThat(detector.needsRendering("<html><body><div id='__nuxt'></div></body></html>"))
                .isTrue();
        assertThat(detector.needsRendering("<html><body><div ng-app='x'></div></body></html>"))
                .isTrue();
    }

    @Test
    @DisplayName("hydration state gives it away even when the mount point has another name")
    void detectsHydrationMarkers() {
        assertThat(detector.needsRendering("<html><body><div class='shell'></div>"
                + "<script>window.__NEXT_DATA__ = {}</script></body></html>")).isTrue();
        assertThat(detector.needsRendering("<html><body><div></div>"
                + "<script>window.__INITIAL_STATE__ = {}</script></body></html>")).isTrue();
    }

    @Test
    @DisplayName("the site saying so itself")
    void detectsANoscriptNotice() {
        assertThat(detector.needsRendering("<html><body>"
                + "<noscript>You need to enable JavaScript to run this app.</noscript>"
                + "</body></html>")).isTrue();
    }

    @Test
    @DisplayName("scripts and no words")
    void detectsAScriptOnlyPage() {
        assertThat(detector.needsRendering("<html><body>"
                + "<script src='/a.js'></script><script src='/b.js'></script>"
                + "<script>init();</script></body></html>")).isTrue();
    }

    @Test
    @DisplayName("one script is enough when the page shipped almost nothing")
    void detectsATemplateRenderedByOneScript() {
        // quotes.toscrape.com/js is exactly this: no framework marker, no noscript, two scripts,
        // and eighty characters of chrome where the content should be
        assertThat(detector.needsRendering("<html><body>"
                + "<div class='header'>Quotes to Scrape Login Next</div>"
                + "<script>var data = []; render(data);</script></body></html>")).isTrue();
    }

    @Test
    @DisplayName("a server-rendered page has the same mount point and is left alone")
    void doesNotChaseServerRenderedPages() {
        assertThat(detector.needsRendering("<html><body><div id='root'><article><p>"
                + prose() + "</p></article></div></body></html>")).isFalse();
    }

    @Test
    @DisplayName("a short page with no script at all is left alone")
    void doesNotChaseAShortStaticPage() {
        assertThat(detector.needsRendering("<html><body><p>Short but complete.</p></body></html>"))
                .isFalse();
    }

    @Test
    @DisplayName("a short page with a script is rendered once, deliberately")
    void rendersAShortScriptedPageAndAcceptsTheCost() {
        // Nothing in the html distinguishes "short static page that happens to load analytics"
        // from "template waiting for javascript". The two costs are not equal: a needless render
        // is one page load, discarded when it yields nothing more, while missing a real one stores
        // an empty page for good. So this errs towards rendering.
        assertThat(detector.needsRendering(
                "<html><body><p>Short.</p><script>var a=1;</script></body></html>")).isTrue();
    }

    @Test
    void emptyOrUnparseableInputIsNotRendered() {
        assertThat(detector.needsRendering(null)).isFalse();
        assertThat(detector.needsRendering("")).isFalse();
        assertThat(detector.needsRendering("   ")).isFalse();
    }

}
