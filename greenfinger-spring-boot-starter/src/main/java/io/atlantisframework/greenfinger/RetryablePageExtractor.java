/**
* Copyright 2017-2022 Fred Feng (paganini.fy@gmail.com)

* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package io.atlantisframework.greenfinger;

import java.nio.charset.Charset;

import org.springframework.http.HttpStatus;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import io.atlantisframework.vortex.common.Tuple;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * RetryablePageExtractor
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
 */
@Slf4j
public class RetryablePageExtractor implements PageExtractor, RetryListener {

	private final PageExtractor pageExtractor;
	private final RetryTemplate retryTemplate;

	public RetryablePageExtractor(PageExtractor pageExtractor) {
		this(pageExtractor, 3);
	}

	public RetryablePageExtractor(PageExtractor pageExtractor, int maxAttempts) {
		this.pageExtractor = pageExtractor;
		this.retryTemplate = createRetryTemplate(maxAttempts);
	}

	@Override
	public String extractHtml(final String refer, final String url, final Charset pageEncoding,final Tuple tuple) throws Exception {
		return retryTemplate.execute(context -> {
			return pageExtractor.extractHtml(refer, url, pageEncoding, tuple);
		}, context -> {
			Throwable e = context.getLastThrowable();
			if (e instanceof PageExtractorException) {
				throw (PageExtractorException) e;
			}
			throw new PageExtractorException(url, HttpStatus.FOUND);
		});
	}

	protected RetryTemplate createRetryTemplate(int maxAttempts) {
		RetryTemplate retryTemplate = new RetryTemplate();
		RetryPolicy retryPolicy = maxAttempts > 0 ? new SimpleRetryPolicy(maxAttempts) : new NeverRetryPolicy();
		retryTemplate.setRetryPolicy(retryPolicy);
		retryTemplate.setBackOffPolicy(new FixedBackOffPolicy());
		retryTemplate.setListeners(new RetryListener[] { this });
		return retryTemplate;
	}

	@Override
	public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
		if (log.isTraceEnabled()) {
			log.trace("Start to extract page html with retry.");
		}
		return true;
	}

	@Override
	public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
		if (log.isTraceEnabled()) {
			log.trace("Complete to extract page html. Retry count: {}", context.getRetryCount());
		}
	}

	@Override
	public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable e) {
	}

}
