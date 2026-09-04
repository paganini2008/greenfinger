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

import java.util.List;

/**
 * 
 * @Description: WebCrawlerConstants
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public abstract class WebCrawlerConstants {

    public static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0");

    /** What an extractor calls itself, for logging and for the dashboard. */
    public static final String EXTRACTOR_RESTCLIENT = "RESTCLIENT";
    public static final String EXTRACTOR_PLAYWRIGHT = "PLAYWRIGHT";
    public static final String EXTRACTOR_SELENIUM = "SELENIUM";
    public static final String EXTRACTOR_HTMLUNIT = "HTMLUNIT";

    /**
     * What a catalog stores in its {@code extractor} column, and what a person types after
     * {@code --extractor}. Lower case, because that is how it is compared.
     */
    public static final String ENGINE_RESTCLIENT = "restclient";
    public static final String ENGINE_HTMLUNIT = "htmlunit";
    public static final String ENGINE_PLAYWRIGHT = "playwright";
    public static final String ENGINE_SELENIUM = "selenium";

    /** Plain http first, a browser only for the pages that came back as an unrendered shell. */
    public static final String ENGINE_ADAPTIVE = "adaptive";

    /** Accepted spellings of {@link #ENGINE_RESTCLIENT}, the second kept from 1.x. */
    public static final String ENGINE_DEFAULT = "default";
    public static final String ENGINE_RESTTEMPLATE = "resttemplate";

    /**
     * The browser engines are optional dependencies. These are the classes whose presence says one
     * is actually on the classpath.
     */
    public static final String CLASS_HTMLUNIT = "org.htmlunit.WebClient";
    public static final String CLASS_PLAYWRIGHT = "com.microsoft.playwright.Playwright";
    public static final String CLASS_SELENIUM = "org.openqa.selenium.WebDriver";

    /**
     * Which browser to fall back to when the configured one is not on the classpath, in the order
     * tried.
     *
     * <p>
     * HtmlUnit first, because it is the only one of the three that needs nothing installed: the
     * engine is in the jar. Playwright downloads its own browsers on first use and Selenium drives
     * one that has to already be on the machine, so either can be missing at runtime on a host
     * where the dependency itself is present.
     */
    public static final List<String> BROWSER_FALLBACK_ORDER =
            List.of(ENGINE_HTMLUNIT, ENGINE_SELENIUM, ENGINE_PLAYWRIGHT);

    /**
     * The url dedup that ships. It is not the only one there can be: a catalog names its filter,
     * and an application that wants a different one supplies its own
     * {@code WebCrawlerComponentFactory} and answers to whatever name it likes.
     */
    public static final String URL_PATH_FILTER_ROCKSDB = "rocksdb";

    public static final String RUNNING_STATE_NONE = "none";
    public static final String RUNNING_STATE_CRAWL = "crawl";
    public static final String RUNNING_STATE_UPDATE = "update";
    public static final String RUNNING_STATE_REBUILD = "rebuild";

}
