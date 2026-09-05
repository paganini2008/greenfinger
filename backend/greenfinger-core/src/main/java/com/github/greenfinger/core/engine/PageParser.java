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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import com.github.greenfinger.core.WebCrawlerProperties;

/**
 * Turns fetched html into the title, the text, the outgoing links and the images worth downloading.
 * 
 * @Description: PageParser
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class PageParser {

    /** Characters of surrounding text kept per image reference. */
    static final int MAX_CONTEXT_LENGTH = 500;

    /** How far up to look for words before giving up. */
    static final int MAX_CONTEXT_ANCESTORS = 4;

    public static final String SOURCE_IMG = "img";
    public static final String SOURCE_SRCSET = "srcset";
    public static final String SOURCE_META = "meta";

    private final WebCrawlerProperties.Image imageConfig;

    public PageParser(WebCrawlerProperties.Image imageConfig) {
        this.imageConfig = imageConfig;
    }

    public Document parse(String html, String baseUri) {
        return Jsoup.parse(html, baseUri);
    }

    public List<String> extractLinks(Document document) {
        List<String> links = new ArrayList<>();
        Elements elements = document.select("a[href]");
        for (Element element : elements) {
            String href = element.absUrl("href");
            if (StringUtils.isBlank(href)) {
                href = element.attr("href");
            }
            if (StringUtils.isNotBlank(href) && !href.startsWith("javascript:")
                    && !href.startsWith("mailto:") && !href.startsWith("tel:")) {
                links.add(href);
            }
        }
        return links;
    }

    /**
     * Collects image references from wherever the configuration says to look. Order is preserved
     * and duplicates within one page are dropped, so a logo repeated in header and footer is
     * considered once.
     */
    /**
     * The text of the nearest block level ancestor, truncated.
     *
     * <p>
     * Walks up rather than reading the immediate parent, because an image is usually wrapped in a
     * link or a span that carries no words of its own. Stops at the first ancestor that holds real
     * text, and gives up at the body so a sparse page does not end up with its whole contents
     * attached to one picture.
     */
    static String contextOf(Element element) {
        Element current = element.parent();
        for (int i = 0; i < MAX_CONTEXT_ANCESTORS && current != null; i++) {
            if ("body".equalsIgnoreCase(current.tagName())) {
                break;
            }
            String text = current.text();
            if (StringUtils.isNotBlank(text)) {
                return text.length() > MAX_CONTEXT_LENGTH ? text.substring(0, MAX_CONTEXT_LENGTH)
                        : text;
            }
            current = current.parent();
        }
        return null;
    }

    /**
     * The length of the text that sits inside links.
     *
     * <p>
     * Together with the total text length this gives the link density Boilerpipe is built on
     * (Kohlschutter et al., "Boilerplate Detection using Shallow Text Features", WSDM 2010): a
     * listing is almost entirely anchor text, an article almost none. Unlike a raw link count it
     * does not punish a long page for having a normal number of links.
     */
    public int linkTextLength(Document document) {
        int total = 0;
        for (Element element : document.select("a")) {
            total += element.text().length();
        }
        return total;
    }

    public List<ImageRef> extractImages(Document document) {
        List<ImageRef> images = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<String> sources = imageConfig.getSources();

        if (sources.contains(SOURCE_IMG)) {
            for (Element element : document.select("img")) {
                String src = element.absUrl("src");
                if (StringUtils.isBlank(src)) {
                    // lazy-loaded images keep the real url out of src until scripts run
                    src = firstNonBlankAbsUrl(element, "data-src", "data-original", "data-lazy-src");
                }
                if (StringUtils.isNotBlank(src) && seen.add(src)) {
                    ImageRef ref = new ImageRef(src, SOURCE_IMG);
                    ref.setAlt(element.attr("alt"));
                    ref.setTitle(element.attr("title"));
                    ref.setContext(contextOf(element));
                    ref.setDeclaredWidth(parseDimension(element.attr("width")));
                    ref.setDeclaredHeight(parseDimension(element.attr("height")));
                    images.add(ref);
                }
            }
        }

        if (sources.contains(SOURCE_SRCSET)) {
            for (Element element : document.select("img[srcset], source[srcset]")) {
                for (String candidate : parseSrcset(element)) {
                    if (StringUtils.isNotBlank(candidate) && seen.add(candidate)) {
                        images.add(new ImageRef(candidate, SOURCE_SRCSET));
                    }
                }
            }
        }

        if (sources.contains(SOURCE_META)) {
            for (String selector : List.of("meta[property=og:image]", "meta[name=og:image]",
                    "meta[name=twitter:image]", "meta[property=twitter:image]")) {
                for (Element element : document.select(selector)) {
                    String content = element.absUrl("content");
                    if (StringUtils.isBlank(content)) {
                        content = element.attr("content");
                    }
                    if (StringUtils.isNotBlank(content) && seen.add(content)) {
                        images.add(new ImageRef(content, SOURCE_META));
                    }
                }
            }
        }

        int maxPerPage = imageConfig.getMaxPerPage();
        if (maxPerPage >= 0 && images.size() > maxPerPage) {
            return images.subList(0, maxPerPage);
        }
        return images;
    }

    private String firstNonBlankAbsUrl(Element element, String... attributes) {
        for (String attribute : attributes) {
            String value = element.absUrl(attribute);
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    /**
     * Reads every candidate out of a srcset. The descriptors ("2x", "640w") say which rendering the
     * candidate is for, and are not part of the url.
     */
    private List<String> parseSrcset(Element element) {
        List<String> urls = new ArrayList<>();
        String srcset = element.attr("srcset");
        if (StringUtils.isBlank(srcset)) {
            return urls;
        }
        for (String part : srcset.split(",")) {
            String candidate = part.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            int space = candidate.indexOf(' ');
            if (space > 0) {
                candidate = candidate.substring(0, space);
            }
            String absolute = element.absUrl("srcset").isEmpty() ? candidate : candidate;
            if (!absolute.startsWith("http") && StringUtils.isNotBlank(element.baseUri())) {
                absolute = resolve(element.baseUri(), absolute);
            }
            urls.add(absolute);
        }
        return urls;
    }

    private String resolve(String baseUri, String candidate) {
        try {
            return java.net.URI.create(baseUri).resolve(candidate).toString();
        } catch (Exception e) {
            return candidate;
        }
    }

    private Integer parseDimension(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim().replace("px", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
