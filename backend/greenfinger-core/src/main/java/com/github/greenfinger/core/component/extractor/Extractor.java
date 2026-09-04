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

import java.nio.charset.Charset;
import org.springframework.http.HttpStatus;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.WebCrawlerComponent;
import com.github.greenfinger.core.engine.CrawlTask;

/**
 * Fetches the html for one url. Four engines implement this -- an http client for static pages, and
 * HtmlUnit, Playwright and Selenium for pages that only exist after their scripts have run.
 * 
 * @Description: Extractor
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public interface Extractor extends WebCrawlerComponent {

    default String test(String url, Charset pageEncoding) throws Exception {
        return extractHtml(null, url, url, pageEncoding, null);
    }

    String extractHtml(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, CrawlTask task) throws Exception;

    /**
     * The same fetch, offering the site what it told us last time so it can answer 304.
     *
     * <p>
     * Default: ask unconditionally and report no validators. Only the http engine implements the
     * conditional form -- a browser engine drives a page load and never exposes the response, so
     * pretending otherwise would mean claiming a saving that is not being made.
     */
    default FetchedPage fetch(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, CrawlTask task, ConditionalGet conditions) throws Exception {
        return FetchedPage
                .of(extractHtml(catalogDetails, referUrl, url, pageEncoding, task));
    }

    /**
     * What to hand back when the fetch failed. Empty by default, which the engine reads as "nothing
     * to store and no links to follow".
     */
    default String defaultHtml(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, CrawlTask task, Throwable e) {
        return "";
    }

    /**
     * 
     * @Description: Result
     * @Author: Fred Feng
     * @Date: 29/08/2026
     * @Version 2.0.0
     */
    interface Result {

        HttpStatus getHttpStatus();

        String getContent();

        long getElapsed();
    }

}
