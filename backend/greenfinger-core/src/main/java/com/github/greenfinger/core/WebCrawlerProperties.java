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

package com.github.greenfinger.core;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 
 * @Description: WebCrawlerProperties
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@ConfigurationProperties("greenfinger")
@Getter
@Setter
@ToString
public class WebCrawlerProperties {

    private String defaultPageEncoding = "UTF-8";
    private int defaultMaxFetchSize = 10000;
    private int defaultMaxFetchDepth = -1;
    /** Minutes. Long enough to be useful, short enough that a first run cannot run away. */
    private long defaultFetchDuration = 30L;
    /** One retry: a transient network blip is common, a broken page is not worth chasing. */
    private int defaultMaxRetryCount = 1;
    private long defaultFetchInterval = 1000L;
    private String defaultUrlPathFilter = "rocksdb";
    /**
     * Plain http first, a browser only for the pages that came back as an unrendered shell.
     *
     * <p>
     * The default because the alternatives are both wrong more often than they are right: plain
     * http silently stores an empty page whenever a site renders itself, and a browser engine costs
     * an order of magnitude on the great majority of pages that never needed one. A site with no
     * javascript never starts a browser at all under this setting, so it costs nothing to leave on.
     */
    private String defaultExtractor = WebCrawlerConstants.ENGINE_ADAPTIVE;
    private String defaultOutputTypes = "file";

    /** Versions to keep before the oldest is pruned. See the delete api. */
    private int defaultMaxVersions = 10;

    /**
     * Size of the fetch pool. The standalone engine finishes when the frontier drains, so this is a
     * throughput knob rather than the completion mechanism it effectively was in 1.x.
     */
    private int workThreads = 16;

    /**
     * Offer the site the ETag and Last-Modified it sent last time, so a merge can be answered with
     * a 304 instead of the page.
     *
     * <p>
     * On, because it only ever applies to a page that has been crawled before and only saves work.
     * Turn it off for a site whose validators lie -- a page that changes without its ETag changing
     * would otherwise never be seen to change again.
     */
    private boolean conditionalGet = true;

    /** Bound on the pending url frontier; keeps a wide site from exhausting the heap. */
    private int queueCapacity = 100000;


    /** Where the resumable frontier is kept. Scoped per catalog and version underneath. */
    private String frontierDirectory = "./data/system/frontier";

    /**
     * How often the clock asks whether the crawl is over.
     *
     * <p>
     * Two questions can only be answered by a clock: has {@code fetchDuration} run out, and have
     * the counters stopped moving. Both are cheap -- a comparison against numbers that are already
     * in hand -- so the interval is about how promptly a finished crawl is noticed rather than
     * about cost. 1.x asked every five seconds; so does this.
     */
    private Duration completionCheckInterval = Duration.ofSeconds(5);

    /**
     * How long the counters may stand still before the crawl is wound up.
     *
     * <p>
     * Standing still means two things and the difference decides whether the version is published.
     * A small site simply runs out of urls: everything dispatched has been handled, nothing is
     * queued anywhere, and the quiet is the crawl being finished -- so it is published. The other
     * quiet is a node that stopped answering while holding urls, or a network that went away
     * mid-crawl: the counters do not meet, some pages will never arrive, and publishing that would
     * put a half version in front of searches. The frontier survives either way, so a resume picks
     * up whatever was missed.
     *
     * <p>
     * It is also, for a site smaller than {@code maxFetchSize}, how long the crawl sits there
     * after the last page: nothing else ends it. Two minutes is the compromise -- long enough that
     * a slow site with a fetch still outstanding is not called stalled and a good version thrown
     * away, short enough that a small site is not left waiting. 1.x used five, for the timeout it
     * had in the same place.
     */
    private Duration idleTimeout = Duration.ofMinutes(2);


    private Dedup dedup = new Dedup();
    private Sitemap sitemap = new Sitemap();
    private Content content = new Content();
    private Image image = new Image();

    /**
     * 
     * @Description: Dedup
     * @Author: Fred Feng
     * @Date: 29/08/2026
     * @Version 2.0.0
     */
    @Getter
    @Setter
    @ToString
    public static class Dedup {

        private Url url = new Url();
        private Content content = new Content();

        /**
         * 
         * @Description: Url
         * @Author: Fred Feng
         * @Date: 29/08/2026
         * @Version 2.0.0
         */
        @Getter
        @Setter
        @ToString
        public static class Url {

            /** Where the RocksDB store lives. */
            private String directory = "./data/system/dedup/url";

            /**
             * Strip tracking parameters and canonicalise before hashing, so that the same page
             * reached through a campaign link is recognised as already seen.
             */
            private boolean normalize = true;
        }

        /**
         * The second dedup pass, on the text of the page rather than its address. Catches the same
         * article republished under several urls, which url dedup by construction cannot.
         * 
         * @Description: Content
         * @Author: Fred Feng
         * @Date: 29/08/2026
         * @Version 2.0.0
         */
        @Getter
        @Setter
        @ToString
        public static class Content {

            private boolean enabled = true;

            /** {@code sha256} for exact matches, {@code simhash} for near-duplicates. */
            private String type = "sha256";

            private String directory = "./data/system/dedup/content";

            /**
             * Hamming distance under which two simhash fingerprints count as the same document.
             * Only consulted when type is simhash.
             *
             * <p>
             * Three is the classic threshold for a 64 bit fingerprint, and it is calibrated for
             * long documents: the same edit that moves a three-paragraph article by zero or one
             * bits moves a single paragraph by five or six, because the edit is a larger share of
             * what the page says. Raising this catches more near-duplicates on short pages at the
             * cost of eventually discarding pages that genuinely differ, which is the worse
             * failure -- so the default stays conservative and {@code minTextLength} keeps the
             * shortest pages out of the comparison entirely.
             */
            private int simhashDistance = 3;

            /** Pages shorter than this are never content-deduplicated; too little signal. */
            private int minTextLength = 200;
        }
    }

    /**
     * Image acquisition. New in 2.0 -- 1.x crawled text only.
     * 
     * @Description: Image
     * @Author: Fred Feng
     * @Date: 29/08/2026
     * @Version 2.0.0
     */
    @Getter
    @Setter
    @ToString
    public static class Image {

        private boolean enabled = true;

        /**
         * Where in the page to look. {@code img} covers src, {@code srcset} covers responsive
         * candidates and picture sources, {@code meta} covers og:image and twitter:image.
         */
        private List<String> sources = List.of("img", "srcset", "meta");

        /** Below these dimensions an image is an icon or a spacer, not content. Zero disables. */
        private int minWidth = 100;
        private int minHeight = 100;

        private long maxBytes = 10L * 1024 * 1024;

        private List<String> mimeTypes =
                List.of("image/jpeg", "image/png", "image/webp", "image/gif", "image/avif");

        /** Cap per page, so one gallery cannot dominate a crawl. Negative means unlimited. */
        private int maxPerPage = 50;

        /**
         * Total image bytes one page may hold in memory. The bytes are carried until the database
         * has accepted the page, so this bounds what a single page can cost; images beyond it are
         * skipped rather than fetched and discarded.
         */
        private long maxPageBytes = 64L * 1024 * 1024;

        private int connectTimeout = 10000;
        private int readTimeout = 30000;
    }


    /**
     * Reading what a site publishes about itself.
     *
     * <p>
     * Following links from the home page reaches the deep pages eventually; a sitemap hands over
     * thousands of them at once. On by default, and free when a site has none.
     * 
     * @Description: Sitemap
     * @Author: Fred Feng
     * @Date: 30/08/2026
     * @Version 2.0.0
     */
    @Getter
    @Setter
    @ToString
    public static class Sitemap {

        private boolean enabled = true;

        /**
         * A cap, not a target. A large site's sitemap can list millions of urls, and the frontier
         * is not the place to put all of them at once -- maxFetchSize decides how many actually
         * get crawled anyway.
         */
        private int maxUrls = 50000;

        /** Sitemap indexes point at sitemaps; one level down covers every site that has one. */
        private int maxIndexDepth = 2;

        private int connectTimeout = 10000;
        private int readTimeout = 30000;
    }


    /**
     * What counts as the page's text.
     * 
     * @Description: Content
     * @Author: Fred Feng
     * @Date: 30/08/2026
     * @Version 2.0.0
     */
    @Getter
    @Setter
    @ToString
    public static class Content {

        /**
         * Keep the article and drop the furniture around it.
         *
         * <p>
         * On by default. Turning it off indexes the navigation, the sidebar and the footer along
         * with the article, which is what every search then has to compete with.
         */
        private boolean extractArticle = true;

        /** A block shorter than this is furniture whatever else it looks like. */
        private int minBlockLength = 200;

        /**
         * Below this the extraction is not believed and the whole body is kept -- a listing has no
         * article to find, and indexing too much beats indexing nothing.
         */
        private int minContentLength = 180;
    }

}
