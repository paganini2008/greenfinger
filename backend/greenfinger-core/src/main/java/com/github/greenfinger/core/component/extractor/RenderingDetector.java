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

import java.util.List;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Decides whether html that came back from a plain http fetch is the page, or only the shell that
 * javascript was supposed to fill in.
 *
 * <p>
 * The question is worth asking because the answer is usually no. Rendering every page in a browser
 * to catch the few that need it costs an order of magnitude in time and memory, and most of the web
 * is still served whole. So the crawler fetches cheaply first and pays for a browser only when what
 * came back looks empty.
 *
 * <p>
 * Three signals, any one of which is enough:
 *
 * <ul>
 * <li>An <b>app shell</b>: a container the frameworks all mount into, sitting empty. {@code <div
 * id="root"></div>} is React's, {@code <div id="app">} Vue's, {@code ng-app} Angular's.</li>
 * <li>A <b>noscript notice</b> telling a human to enable javascript, which is the site saying so
 * itself.</li>
 * <li><b>Scripts without prose</b>: a page whose markup is mostly script tags and whose body holds
 * almost no text. On its own this is weak -- an image gallery looks the same -- so it needs the
 * text to be very short indeed.</li>
 * </ul>
 *
 * <p>
 * The cost of being wrong is asymmetric, and the thresholds lean accordingly: a false positive
 * costs one wasted browser render, while a false negative silently stores an empty page. Even so
 * they are deliberately conservative, because a crawl that renders everything is no better than
 * configuring the browser engine outright.
 * 
 * @Description: RenderingDetector
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public class RenderingDetector {

    /**
     * The containers single page frameworks mount into. Empty, they mean the page never ran.
     */
    private static final List<String> APP_SHELLS = List.of("#root", "#app", "#__next", "#__nuxt",
            "[data-reactroot]", "[ng-app]", "[ng-view]", "#ember-app", "#svelte");

    /**
     * State that a framework leaves in the markup for its own hydration. Their presence says the
     * page is a rendered application even when the shell selector missed.
     */
    private static final List<String> HYDRATION_MARKERS =
            List.of("__NEXT_DATA__", "__NUXT__", "window.__INITIAL_STATE__",
                    "window.__APOLLO_STATE__", "__remixContext");

    private final int minTextLength;
    private final int shellTextLength;

    public RenderingDetector(int minTextLength, int shellTextLength) {
        this.minTextLength = minTextLength;
        this.shellTextLength = shellTextLength;
    }

    /**
     * @return true when the page should be fetched again with a browser
     */
    public boolean needsRendering(String html) {
        if (StringUtils.isBlank(html)) {
            return false;
        }
        Document document;
        try {
            document = Jsoup.parse(html);
        } catch (Exception e) {
            return false;
        }
        String text = document.body() != null ? document.body().text() : "";
        int textLength = text.length();

        // plenty of prose: whatever else the page does, it arrived readable
        if (textLength >= minTextLength) {
            return false;
        }
        return hasEmptyAppShell(document, textLength) || saysToEnableJavascript(document)
                || isMostlyScript(document, textLength);
    }

    /**
     * A mount point with nothing in it. The length check matters: a server-rendered React page has
     * the same {@code #root} and is perfectly readable.
     */
    private boolean hasEmptyAppShell(Document document, int textLength) {
        if (textLength > shellTextLength) {
            return false;
        }
        for (String selector : APP_SHELLS) {
            if (!document.select(selector).isEmpty()) {
                return true;
            }
        }
        String html = document.html();
        for (String marker : HYDRATION_MARKERS) {
            if (html.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /** The site telling a human what it needs; the crawler can take the same hint. */
    private boolean saysToEnableJavascript(Document document) {
        for (var noscript : document.select("noscript")) {
            String text = noscript.text().toLowerCase(Locale.ROOT);
            if (text.contains("javascript") || text.contains("js")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Scripts and no words.
     *
     * <p>
     * The bar is one script, not several: a page that shipped almost no text and any javascript at
     * all is worth a second look. Real pages do this -- a template rendered by a few lines of
     * inline script carries no framework marker and no noscript notice, and would otherwise be
     * stored as the eighty characters of chrome that arrived around it.
     *
     * <p>
     * What keeps this from rendering the whole web is the text bar, which is very low, and the
     * fact that a render producing no more than the plain fetch is discarded -- so the cost of
     * being wrong is one wasted page load, once.
     */
    private boolean isMostlyScript(Document document, int textLength) {
        if (textLength > shellTextLength) {
            return false;
        }
        return !document.select("script[src], script:not([src])").isEmpty();
    }

}
