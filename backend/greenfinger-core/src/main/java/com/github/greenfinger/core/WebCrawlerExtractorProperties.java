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

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 
 * @Description: WebCrawlerExtractorProperties
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@ConfigurationProperties("greenfinger.extractor")
@Getter
@Setter
@ToString
public class WebCrawlerExtractorProperties {

    /** Ceiling on one fetch, whichever engine is doing it. */
    private long timeout = 5L * 60 * 1000;

    private RestClient restClient = new RestClient();
    private Adaptive adaptive = new Adaptive();
    private Playwright playwright = new Playwright();
    private Selenium selenium = new Selenium();
    private HtmlUnit htmlunit = new HtmlUnit();
    private ObjectPool objectPool = new ObjectPool();

    /**
     * 
     * @Description: Base
     * @Author: Fred Feng
     * @Date: 29/08/2026
     * @Version 2.0.0
     */
    @Getter
    @Setter
    @ToString
    public static class Base {

        private Map<String, String> defaultHttpHeaders = new HashMap<>();
        private String proxyHost;
        private int proxyPort;

        /** Extra settle time after the page reports ready, for content that arrives late. */
        private long loadingTimeout;

        public String getProxyServer() {
            if (StringUtils.isNotBlank(proxyHost) && proxyPort > 0) {
                return proxyHost + ":" + proxyPort;
            }
            return "";
        }

    }

    /**
     * 
     * @Description: RestClient
     * @Author: Fred Feng
     * @Date: 29/08/2026
     * @Version 2.0.0
     */
    @Getter
    @Setter
    @ToString(callSuper = true)
    public static class RestClient extends Base {

        private int connectTimeout = 10000;
        private int readTimeout = 60000;

        /** How long to wait for a connection from the pool before giving up. */
        private int connectionRequestTimeout = 10000;

        private int maxConnectionTotal = 200;
        private int maxConnectionPerRoute = 20;
        private boolean followRedirects = true;
    }

    /**
     * 
     * @Description: ObjectPool
     * @Author: Fred Feng
     * @Date: 29/08/2026
     * @Version 2.0.0
     */
    @Getter
    @Setter
    @ToString
    public static class ObjectPool {

        private int minIdle = 1;
        private int maxIdle = 2;
        private int maxTotal = 8;
        private long borrowTimeout = 60000L;
    }

    /**
     * 
     * @Description: Playwright
     * @Author: Fred Feng
     * @Date: 29/08/2026
     * @Version 2.0.0
     */
    @Getter
    @Setter
    @ToString(callSuper = true)
    public static class Playwright extends Base {

        private boolean javaScriptEnabled = true;
        private boolean headless = true;
        private int timeout = 30 * 1000;

        /** chromium, firefox or webkit. */
        private String browser = "chromium";
    }

    /**
     * 
     * @Description: Selenium
     * @Author: Fred Feng
     * @Date: 29/08/2026
     * @Version 2.0.0
     */
    @Getter
    @Setter
    @ToString(callSuper = true)
    public static class Selenium extends Base {

        /**
         * Left empty on purpose: WebDriverManager resolves a driver matching the installed browser.
         * 1.x hard-coded a Windows path here, which no other machine could satisfy.
         */
        private String webDriverExecutionPath;

        private boolean headless = true;
        private String browser = "chrome";
        private int pageLoadTimeout = 30 * 1000;
    }

    /**
     * 
     * @Description: HtmlUnit
     * @Author: Fred Feng
     * @Date: 29/08/2026
     * @Version 2.0.0
     */
    @Getter
    @Setter
    @ToString(callSuper = true)
    public static class HtmlUnit extends Base {

        private int timeout = 30 * 1000;
        private boolean javaScriptEnabled = true;
        private long javaScriptTimeout = 10L * 1000;
        private boolean cssEnabled = false;
    }


    /**
     * How the adaptive extractor decides a page needs a browser.
     * 
     * @Description: Adaptive
     * @Author: Fred Feng
     * @Date: 30/08/2026
     * @Version 2.0.0
     */
    @Getter
    @Setter
    @ToString
    public static class Adaptive {

        /**
         * Which engine renders the pages plain http could not -- any of the three browsers, not
         * restclient, which is the fast path itself. Playwright is the default: a real browser, so
         * it renders what a browser renders, and it reports the page's http status, which Selenium
         * cannot. It downloads browser binaries on first use, so a deployment without internet
         * access, or short of memory, should use htmlunit.
         */
        private String browser = WebCrawlerConstants.ENGINE_PLAYWRIGHT;

        /**
         * Prose beyond this and the page arrived readable, whatever else it contains. Set low: the
         * point is to catch pages with nothing in them, not to second-guess short pages.
         */
        private int minTextLength = 400;

        /**
         * How little text an app shell may hold and still count as unrendered. A server-rendered
         * page has the same mount points and plenty of words.
         */
        private int shellTextLength = 120;
    }

}
