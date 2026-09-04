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

package com.github.greenfinger.core.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import lombok.extern.slf4j.Slf4j;

/**
 * Separates the article from the furniture around it.
 *
 * <p>
 * Taking the whole body puts the navigation, the sidebar, the cookie banner and the footer into
 * the index and into every embedding. It is the single biggest source of noise in both: a search
 * matches a word that only ever appeared in a menu, and a page's vector ends up describing the
 * site's chrome rather than what the page is about.
 *
 * <p>
 * The method is the one boilerplate detection has used since Boilerpipe (Kohlschutter et al.,
 * "Boilerplate Detection using Shallow Text Features", WSDM 2010) and Readability: score the
 * candidate blocks by how much prose they hold against how much of that prose is inside links, and
 * keep the best one. No model, no dictionary, and it works the same in any language -- which
 * matters here, because the crawler is not told what language a page is in.
 *
 * <p>
 * It gives up rather than guesses. When nothing scores well -- a page that really is a listing, or
 * markup too unusual to read -- the whole body is used, on the grounds that indexing too much
 * beats indexing nothing.
 * 
 * @Description: ContentExtractor
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class ContentExtractor {

    /** Never part of the article, whatever they score. */
    private static final String STRIP =
            "script, style, noscript, iframe, svg, form, button, "
                    + "nav, header, footer, aside, template";

    /**
     * Class and id names that say what a block is. Crude, and the reason it still helps is that
     * the whole industry writes markup the same way.
     */
    private static final List<String> NEGATIVE_HINTS =
            List.of("nav", "menu", "sidebar", "footer", "header", "banner", "advert", "ads",
                    "comment", "share", "social", "related", "recommend", "breadcrumb", "pagination",
                    "cookie", "popup", "modal", "widget", "toolbar", "copyright");

    private static final List<String> POSITIVE_HINTS = List.of("article", "content", "post", "entry",
            "main", "body", "story", "text", "detail");

    /**
     * What one CJK character is worth against one latin character, as a rough measure of how much
     * was said. Around the ratio of English characters per word.
     */
    private static final int CJK_WEIGHT = 4;

    /** Blocks shorter than this are furniture whatever else they look like. */
    private final int minBlockLength;

    /** Below this the extraction is not trusted and the whole body is used. */
    private final int minContentLength;

    private final boolean enabled;

    public ContentExtractor(boolean enabled, int minBlockLength, int minContentLength) {
        this.enabled = enabled;
        this.minBlockLength = minBlockLength;
        this.minContentLength = minContentLength;
    }

    /**
     * @return the article's text, or the whole body when nothing looks like an article
     */
    public String extract(Document document) {
        String whole = document.body() != null ? document.body().text() : "";
        if (!enabled || StringUtils.isBlank(whole)) {
            return whole;
        }
        try {
            Document working = document.clone();
            working.select(STRIP).remove();
            if (working.body() == null) {
                return whole;
            }

            Element best = null;
            double bestScore = 0d;
            for (Element candidate : candidatesIn(working)) {
                double score = score(candidate);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            if (best == null) {
                return whole;
            }
            String extracted = best.text();
            // a page that really is a listing has no article to find; keeping too much beats
            // keeping nothing
            return weightedLength(extracted) >= minContentLength ? extracted : whole;
        } catch (Exception e) {
            log.debug("Content extraction failed, keeping the whole body: {}", e.getMessage());
            return whole;
        }
    }

    /**
     * An explicit {@code <article>} or {@code <main>} settles it when the page has one; otherwise
     * every block-level container is a candidate.
     */
    private Elements candidatesIn(Document working) {
        Elements explicit = working.select("article, main, [role=main]");
        if (!explicit.isEmpty()) {
            return explicit;
        }
        return working.select("div, section, td");
    }

    /**
     * Prose, discounted by how much of it is anchor text, and by whether the block calls itself
     * something an article is never called.
     */
    private double score(Element element) {
        String text = element.text();
        int length = text.length();
        if (weightedLength(text) < minBlockLength) {
            return 0d;
        }
        int anchorLength = 0;
        for (Element anchor : element.select("a")) {
            anchorLength += anchor.text().length();
        }
        double linkDensity = Math.min(1d, (double) anchorLength / length);
        // mostly links: a menu or a listing, not an article
        if (linkDensity > 0.5d) {
            return 0d;
        }

        // paragraphs are what articles are made of; a wall of divs usually is not one
        int paragraphs = element.select("p").size();

        double score = length * (1d - linkDensity) * (1d + Math.min(paragraphs, 20) * 0.05d);
        return score * hintFactor(element);
    }

    /**
     * Length as a measure of how much was said, not of how many characters were typed.
     *
     * <p>
     * A threshold in characters is a threshold in English. Two hundred characters of English is a
     * couple of sentences; two hundred characters of Chinese is several paragraphs, because a han
     * character carries roughly what an English word does. Counting them at face value makes every
     * CJK article look too short to be one, and the extractor quietly falls back to the whole page
     * for an entire language.
     *
     * <p>
     * Each CJK character is therefore counted as {@value #CJK_WEIGHT} so that one threshold means
     * the same thing in either script.
     */
    static int weightedLength(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int weighted = 0;
        for (int i = 0; i < text.length(); i++) {
            weighted += isCjk(text.charAt(i)) ? CJK_WEIGHT : 1;
        }
        return weighted;
    }

    private static boolean isCjk(char ch) {
        Character.UnicodeScript script = Character.UnicodeScript.of(ch);
        return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    private double hintFactor(Element element) {
        String signature = (element.id() + " " + element.className()).toLowerCase(Locale.ROOT);
        if (StringUtils.isBlank(signature.trim())) {
            return 1d;
        }
        for (String hint : NEGATIVE_HINTS) {
            if (signature.contains(hint)) {
                return 0.2d;
            }
        }
        for (String hint : POSITIVE_HINTS) {
            if (signature.contains(hint)) {
                return 1.5d;
            }
        }
        return 1d;
    }

    /**
     * The blocks that were dropped, for anyone wanting to see what extraction removed.
     */
    public List<String> strippedTags() {
        return new ArrayList<>(List.of(STRIP.split(",\\s*")));
    }

}
