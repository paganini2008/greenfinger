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
import com.github.greenfinger.core.utils.ThreadUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Retries a failed fetch, backing off a little further each time so a struggling host is not
 * hammered.
 * 
 * @Description: RetryableExtractor
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class RetryableExtractor implements Extractor, ManagedBeanLifeCycle {

    private static final long DEFAULT_BACKOFF_PERIOD = 1000L;

    private final Extractor extractor;
    private final int maxAttempts;
    private final long backOffPeriod;

    public RetryableExtractor(Extractor extractor, int maxAttempts) {
        this(extractor, maxAttempts, DEFAULT_BACKOFF_PERIOD);
    }

    public RetryableExtractor(Extractor extractor, int maxAttempts, long backOffPeriod) {
        this.extractor = extractor;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backOffPeriod = backOffPeriod;
    }

    @Override
    public String getName() {
        return extractor.getName();
    }

    @Override
    public String extractHtml(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, CrawlTask task) throws Exception {
        return withRetries(url,
                () -> extractor.extractHtml(catalogDetails, referUrl, url, pageEncoding, task));
    }

    /**
     * Forwarded rather than left to the default, which would call {@code extractHtml} and lose both
     * the 304 and the validators -- a wrapper that quietly turns a conditional fetch back into an
     * ordinary one is worse than none, because nothing would look wrong.
     */
    @Override
    public FetchedPage fetch(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, CrawlTask task, ConditionalGet conditions) throws Exception {
        return withRetries(url, () -> extractor.fetch(catalogDetails, referUrl, url, pageEncoding,
                task, conditions));
    }

    private <T> T withRetries(String url, Attempt<T> once) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return once.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                lastError = e;
                if (log.isWarnEnabled()) {
                    log.warn("Attempt {}/{} failed for '{}': {}", attempt, maxAttempts, url,
                            e.getMessage());
                }
                if (attempt < maxAttempts) {
                    // linear back-off: a host that just refused us is unlikely to be ready at once
                    ThreadUtils.sleep(backOffPeriod * attempt);
                }
            }
        }
        if (lastError instanceof ExtractorException) {
            throw lastError;
        }
        throw new ExtractorException(url, lastError);
    }

    /**
     * 
     * @Description: Attempt
     * @Author: Fred Feng
     * @Date: 01/09/2026
     * @Version 2.0.0
     */
    @FunctionalInterface
    private interface Attempt<T> {

        T run() throws Exception;
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
