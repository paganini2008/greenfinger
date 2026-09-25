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
 * Decides whether plain-http html is the page, or only the shell javascript was to fill in.
 *
 * <p>
 * Rendering everything in a browser costs an order of magnitude, so fetch cheaply first and pay for
 * a browser only when the result looks empty. Any one of three signals is enough: an empty app
 * shell ({@code id="root"}, {@code id="app"}, {@code ng-app}), a noscript notice, or scripts with
 * almost no prose. Thresholds lean conservative -- a false positive costs one wasted render, a
 * false negative silently stores an empty page.
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
     * Scripts and no words. One script is enough: a page with almost no text and any javascript is
     * worth a second look. The very low text bar is what stops this matching the whole web.
     */
    private boolean isMostlyScript(Document document, int textLength) {
        if (textLength > shellTextLength) {
            return false;
        }
        return !document.select("script[src], script:not([src])").isEmpty();
    }

}
