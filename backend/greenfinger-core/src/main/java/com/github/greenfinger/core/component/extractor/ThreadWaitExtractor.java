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
import com.github.greenfinger.core.utils.BeanLifeCycleUtils;
import com.github.greenfinger.core.ManagedBeanLifeCycle;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawlTask;

/**
 * Paces requests by the catalog's fetch interval. Politeness, and the difference between a crawler
 * a site tolerates and one it blocks.
 * 
 * @Description: ThreadWaitExtractor
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class ThreadWaitExtractor implements Extractor, ManagedBeanLifeCycle {

    private final Extractor extractor;
    private final ThreadWait threadWait;

    public ThreadWaitExtractor(Extractor extractor, ThreadWait threadWait) {
        this.extractor = extractor;
        this.threadWait = threadWait;
    }

    @Override
    public String getName() {
        return extractor.getName();
    }

    @Override
    public String extractHtml(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, CrawlTask task) throws Exception {
        pause(catalogDetails);
        return extractor.extractHtml(catalogDetails, referUrl, url, pageEncoding, task);
    }

    /**
     * Forwarded rather than left to the default: falling back to {@code extractHtml} would drop
     * both the 304 and the validators, and the crawl would go on downloading pages it had already
     * been told were unchanged.
     */
    @Override
    public FetchedPage fetch(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, CrawlTask task, ConditionalGet conditions) throws Exception {
        pause(catalogDetails);
        return extractor.fetch(catalogDetails, referUrl, url, pageEncoding, task, conditions);
    }

    /** The politeness delay: still owed even to a request that may come back empty. */
    private void pause(CatalogDetails catalogDetails) {
        Long fetchInterval = catalogDetails != null ? catalogDetails.getFetchInterval() : null;
        if (fetchInterval != null) {
            threadWait.doWait(fetchInterval);
        } else {
            ThreadWait.RANDOM_SLEEP.doWait(1000L);
        }
    }

    @Override
    public String defaultHtml(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, CrawlTask task, Throwable e) {
        return extractor.defaultHtml(catalogDetails, referUrl, url, pageEncoding, task, e);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        BeanLifeCycleUtils.afterPropertiesSet(extractor);
    }

    @Override
    public void destroy() throws Exception {
        BeanLifeCycleUtils.destroy(extractor);
    }

}
