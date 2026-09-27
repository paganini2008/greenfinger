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

package com.github.greenfinger.core.component.acceptor;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawlTask;

/**
 * Refuses a link that is plainly a file rather than a page.
 *
 * <p>
 * A gallery links its full size picture with {@code <a href="photo.jpg">}, and following that is
 * a fetch that can only end one way: the server answers {@code image/jpeg}, the extractor refuses
 * it, and the url has cost a request, a slot of {@code maxFetchSize} and -- the part that matters
 * -- a tick of the consecutive failure counter. Twenty gallery links in a row is an ordinary page,
 * and it used to end the crawl as "the site has refused everything", publishing nothing.
 *
 * <p>
 * This costs the crawl nothing. Pictures are not found by following links: the page parser reads
 * them from {@code img}, {@code srcset} and the meta tags, and that path is untouched here. The
 * only thing refused is treating the same file as a page as well.
 *
 * <p>
 * Extensions only, and only unambiguous ones. A url ending {@code .do} or {@code .php} is a page
 * on plenty of sites, so nothing dynamic is on the list. {@code GF_SKIP_EXTENSIONS} replaces the
 * list, which is the escape hatch for a site that serves html from an odd name.
 *
 * <p>
 * A pdf or a spreadsheet stays on the list even where the application has a parser for it. This
 * decides what is <b>crawled as a page</b>, and a pdf never is. Reading one is the other path:
 * {@link com.github.greenfinger.core.engine.PageParser#extractDownloadedFiles} records the link
 * and {@link com.github.greenfinger.core.document.DocumentContentParser} is what turns it into
 * text. Taking pdf off this list does not enable that -- it only spends a request discovering
 * that a pdf is not html.
 *
 * @Description: AssetUrlPathAcceptor
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
public class AssetUrlPathAcceptor implements UrlPathAcceptor {

    /** Nothing here has ever been a page. */
    public static final Set<String> DEFAULT_EXTENSIONS = Set.of(
            // pictures. The image pipeline collects these from the markup, never from a link
            "jpg", "jpeg", "png", "gif", "webp", "avif", "bmp", "ico", "tif", "tiff", "svg",
            // audio and video
            "mp3", "wav", "ogg", "flac", "m4a", "mp4", "m4v", "mov", "avi", "mkv", "webm",
            // archives and packages
            "zip", "gz", "tgz", "bz2", "xz", "7z", "rar", "tar", "dmg", "iso", "exe", "msi",
            "deb", "rpm", "apk", "jar",
            // documents. pdf is here because nothing reads one yet; it comes off the day one does
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "epub",
            // what a browser fetches for itself
            "css", "js", "mjs", "map", "woff", "woff2", "ttf", "eot", "otf");

    private final Set<String> extensions;

    public AssetUrlPathAcceptor() {
        this(null);
    }

    /**
     * @param configured what to refuse, as above
     * @param exempt     formats to let through even though they are files: a document the
     *                   installation has a parser for, so fetching it produces text rather than a
     *                   refusal. Whether a parser exists is checked where this is built, not here.
     */
    public AssetUrlPathAcceptor(String configured, Set<String> exempt) {
        Set<String> refused = new LinkedHashSet<>(parse(configured));
        if (exempt != null) {
            exempt.stream().map(one -> one.toLowerCase(Locale.ROOT)).forEach(refused::remove);
        }
        this.extensions = Set.copyOf(refused);
    }

    /**
     * @param configured a comma separated list replacing the defaults, or blank to keep them. An
     *                   explicit "none" turns the acceptor off for a site that really does serve
     *                   pages from those names.
     */
    public AssetUrlPathAcceptor(String configured) {
        this.extensions = parse(configured);
    }

    static Set<String> parse(String configured) {
        if (StringUtils.isBlank(configured)) {
            return DEFAULT_EXTENSIONS;
        }
        if ("none".equalsIgnoreCase(configured.trim())) {
            return Set.of();
        }
        Set<String> parsed = new LinkedHashSet<>();
        Arrays.stream(StringUtils.split(configured, ",")).map(String::trim)
                .map(one -> StringUtils.removeStart(one, ".")).filter(StringUtils::isNotBlank)
                .map(one -> one.toLowerCase(Locale.ROOT)).forEach(parsed::add);
        return parsed.isEmpty() ? DEFAULT_EXTENSIONS : parsed;
    }

    @Override
    public String getName() {
        return "asset";
    }

    /**
     * Ahead of everything that costs anything. It is a string comparison, and robots.txt, the
     * depth count and the path patterns are all more work than that.
     */
    @Override
    public int getOrder() {
        return Integer.MIN_VALUE + 2;
    }

    @Override
    public boolean accept(CatalogDetails catalogDetails, String referUrl, String url,
            CrawlTask task) {
        if (extensions.isEmpty() || StringUtils.isBlank(url)) {
            return true;
        }
        return !extensions.contains(extensionOf(url));
    }

    /**
     * The extension of the path, and only of the path.
     *
     * <p>
     * {@code photo.jpg?v=2} is a picture and {@code /download?file=x.jpg} is a page that hands one
     * out, so the query is cut off first. A dot in a directory rather than in the last segment --
     * {@code /v1.2/index} -- is not an extension either.
     */
    static String extensionOf(String url) {
        String path = StringUtils.substringBefore(StringUtils.substringBefore(url, "#"), "?");
        String last = StringUtils.substringAfterLast(path, "/");
        if (last.isEmpty() || !last.contains(".")) {
            return "";
        }
        return StringUtils.substringAfterLast(last, ".").toLowerCase(Locale.ROOT);
    }

}
