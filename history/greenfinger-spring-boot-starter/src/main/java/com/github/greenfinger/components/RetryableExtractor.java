/*
 * Copyright 2017-2025 Fred Feng (paganini.fy@gmail.com)
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

package com.github.greenfinger.components;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import com.github.doodler.common.context.BeanLifeCycleUtils;
import com.github.doodler.common.context.ManagedBeanLifeCycle;
import com.github.doodler.common.transmitter.Packet;
import com.github.greenfinger.CatalogDetails;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: RetryableExtractor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@Slf4j
public class RetryableExtractor implements Extractor, RetryListener, ManagedBeanLifeCycle {

    private final Extractor extractor;
    private final RetryTemplate retryTemplate;

    public RetryableExtractor(Extractor extractor, int maxAttempts) {
        this.extractor = extractor;
        this.retryTemplate = createRetryTemplate(maxAttempts);
    }

    @Override
    public String extractHtml(final CatalogDetails catalogDetails, final String referUrl,
            final String url, final Charset pageEncoding, final Packet packet) throws Exception {
        return retryTemplate.execute(context -> {
            return extractor.extractHtml(catalogDetails, referUrl, url, pageEncoding, packet);
        }, context -> {
            Throwable e = context.getLastThrowable();
            if (e instanceof ExtractorException) {
                throw (ExtractorException) e;
            }
            throw new ExtractorException(url, HttpStatus.NOT_FOUND);
        });
    }

    protected RetryTemplate createRetryTemplate(int maxAttempts) {
        RetryTemplate retryTemplate = new RetryTemplate();
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(Exception.class, true);
        RetryPolicy retryPolicy =
                maxAttempts > 0 ? new SimpleRetryPolicy(maxAttempts, retryableExceptions)
                        : new NeverRetryPolicy();
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(new FixedBackOffPolicy());
        retryTemplate.setListeners(new RetryListener[] {this});
        return retryTemplate;
    }

    @Override
    public <T, E extends Throwable> boolean open(RetryContext context,
            RetryCallback<T, E> callback) {
        if (log.isTraceEnabled()) {
            log.trace("Start to extract page html with retry.");
        }
        return true;
    }

    @Override
    public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback,
            Throwable e) {
        if (e != null) {
            if (log.isErrorEnabled()) {
                log.error("Complete to extract page html. Retry count: {}", context.getRetryCount(),
                        e);
            }
        } else {
            if (log.isTraceEnabled()) {
                log.trace("Complete to extract page html. Retry count: {}",
                        context.getRetryCount());
            }
        }
    }

    @Override
    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
            Throwable e) {
        if (log.isWarnEnabled()) {
            log.warn("Request failure. Retry count: {} reason: {}", context.getRetryCount(),
                    e.getMessage(), e);
        }
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
