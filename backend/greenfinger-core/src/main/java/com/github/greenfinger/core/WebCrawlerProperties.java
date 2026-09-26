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
    private long defaultFetchDuration = 10L;
    /** One retry: a transient network blip is common, a broken page is not worth chasing. */
    private int defaultMaxRetryCount = 1;
    private long defaultFetchInterval = 1000L;
    private String defaultUrlPathFilter = "rocksdb";
    /**
     * Plain http first, a browser only for the pages that came back as an unrendered shell.
     *
     * <p>
     * The alternatives are each wrong more often: plain http stores empty pages for sites that
     * render themselves, and a browser costs an order of magnitude on pages that never needed one.
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
     * Offer back the ETag and Last-Modified, so a merge can be answered with a 304. Turn it off
     * for a site whose validators lie: a page that changes without its ETag would never be seen
     * to change again.
     */
    private boolean conditionalGet = true;

    /** Bound on the pending url frontier; keeps a wide site from exhausting the heap. */
    private int queueCapacity = 100000;


    /** Where the resumable frontier is kept. Scoped per catalog and version underneath. */
    private String frontierDirectory = "./data/system/frontier";

    /**
     * How often the clock asks whether the crawl is over -- {@code fetchDuration} spent, or the
     * counters standing still. Both are comparisons against numbers already in hand, so this is
     * about how promptly a finished crawl is noticed rather than about cost.
     */
    private Duration completionCheckInterval = Duration.ofSeconds(5);

    /**
     * How long the counters may stand still before the crawl is wound up.
     *
     * <p>
     * Quiet means two things, and which one decides whether the version is published: a site that
     * ran out of urls with the counters meeting is finished, while a node that stopped answering
     * while holding urls leaves a half version. The frontier survives either way, so a resume
     * picks up what was missed.
     *
     * <p>
     * It is also how long a site smaller than {@code maxFetchSize} sits there after its last page.
     */
    private Duration idleTimeout = Duration.ofMinutes(2);

    /**
     * How many failed fetches in a row end the crawl, or 0 to never end it on that alone.
     *
     * <p>
     * In a row, not in total, and reset by the first page that arrives: a members-only corner of
     * an open site is a few 403s among successes, while a challenge in front of the whole domain
     * is an unbroken run. Ends the crawl the way a person asking for it does, so nothing is
     * published; the duration timeout remains the backstop underneath.
     */
    private int maxConsecutiveFailures = 20;


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
             * Hamming distance under which two simhash fingerprints count as one document.
             *
             * <p>
             * Three is the classic threshold for 64 bits and is calibrated for long documents: an
             * edit moves a short page much further. Raising it eventually discards pages that
             * genuinely differ, so {@code minTextLength} keeps short ones out instead.
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
