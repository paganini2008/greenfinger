/**
* Copyright 2017-2021 Fred Feng (paganini.fy@gmail.com)

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
package indi.atlantis.framework.greenfinger;

import java.util.Map;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.support.atomic.RedisAtomicLong;

import com.github.paganini2008.devtools.beans.ToStringBuilder;
import com.github.paganini2008.devtools.collection.MapUtils;
import com.github.paganini2008.devtools.date.DateUtils;
import com.github.paganini2008.devtools.multithreads.Executable;
import com.github.paganini2008.devtools.multithreads.ThreadUtils;
import com.github.paganini2008.springdessert.reditools.common.GenericRedisTemplate;

import indi.atlantis.framework.tridenter.utils.BeanLifeCycle;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * CrawlerStatistics
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
 */
@Slf4j
public class CrawlerStatistics implements BeanLifeCycle, Executable {

	private static final String defaultRedisKeyPattern = "atlantis:framework:greenfinger:%s:catalog:summary:%s";
	private static final long DEFAULT_CRAWLER_IDLE_TIMEOUT = DateUtils.convertToMillis(5, TimeUnit.MINUTES);

	private final String crawlerName;
	private final RedisConnectionFactory redisConnectionFactory;

	public CrawlerStatistics(String crawlerName, RedisConnectionFactory redisConnectionFactory) {
		this.crawlerName = crawlerName;
		this.redisConnectionFactory = redisConnectionFactory;
	}

	private final Map<Long, Summary> cache = new ConcurrentHashMap<Long, Summary>();
	private Timer timer;

	public void reset(long catalogId) {
		getSummary(catalogId).reset();
	}

	public void completeNow(long catalogId) {
		getSummary(catalogId).setCompleted(true);
	}

	public Summary getSummary(long catalogId) {
		final String keyPrefix = String.format(defaultRedisKeyPattern, crawlerName, catalogId);
		return MapUtils.get(cache, catalogId, () -> {
			return new Summary(keyPrefix, redisConnectionFactory);
		});
	}

	public String getCrawlerName() {
		return crawlerName;
	}

	@Override
	public void configure() throws Exception {
		timer = ThreadUtils.scheduleWithFixedDelay(this, 5, TimeUnit.SECONDS);
	}

	@Override
	public void destroy() {
		if (timer != null) {
			timer.cancel();
		}
		cache.values().forEach(summary -> {
			summary.reset();
		});
		cache.clear();
	}

	@Override
	public boolean execute() {
		Summary summary;
		for (Map.Entry<Long, Summary> entry : cache.entrySet()) {
			summary = entry.getValue();
			if (!summary.isCompleted()) {
				if (System.currentTimeMillis() - summary.getTimestamp() > DEFAULT_CRAWLER_IDLE_TIMEOUT) {
					summary.setCompleted(true);
					log.info("Complete crawling due to idle timeout is too long. CatalogId: {}", entry.getKey());
				}
			}
		}
		return true;
	}

	/**
	 * 
	 * Summary
	 *
	 * @author Fred Feng
	 * 
	 * @since 2.0.1
	 */
	public static class Summary {

		private final GenericRedisTemplate<Long> startTime;
		private final RedisAtomicLong urls;
		private final RedisAtomicLong invalidUrls;
		private final RedisAtomicLong existedUrls;
		private final RedisAtomicLong filteredUrls;
		private final RedisAtomicLong saved;
		private final RedisAtomicLong indexed;
		private final GenericRedisTemplate<Boolean> completed;
		private long timestamp;

		Summary(String keyPrefix, RedisConnectionFactory redisConnectionFactory) {
			startTime = new GenericRedisTemplate<Long>(keyPrefix + ":startTime", Long.class, redisConnectionFactory,
					System.currentTimeMillis());
			urls = new RedisAtomicLong(keyPrefix + ":urlCount", redisConnectionFactory);
			invalidUrls = new RedisAtomicLong(keyPrefix + ":invalidUrlCount", redisConnectionFactory);
			existedUrls = new RedisAtomicLong(keyPrefix + ":existedUrlCount", redisConnectionFactory);
			filteredUrls = new RedisAtomicLong(keyPrefix + ":filteredUrlCount", redisConnectionFactory);
			saved = new RedisAtomicLong(keyPrefix + ":savedCount", redisConnectionFactory);
			indexed = new RedisAtomicLong(keyPrefix + ":indexedCount", redisConnectionFactory);
			completed = new GenericRedisTemplate<Boolean>(keyPrefix + ":completed", Boolean.class, redisConnectionFactory, true);
		}

		public void reset() {
			startTime.set(System.currentTimeMillis());
			urls.set(0);
			invalidUrls.set(0);
			existedUrls.set(0);
			filteredUrls.set(0);
			saved.set(0);
			indexed.set(0);
			completed.set(false);
			timestamp = System.currentTimeMillis();
		}

		public boolean isCompleted() {
			return completed.get();
		}

		public void setCompleted(boolean completed) {
			this.completed.set(completed);
			this.timestamp = System.currentTimeMillis();
		}

		public long incrementUrlCount() {
			try {
				return urls.incrementAndGet();
			} finally {
				this.timestamp = System.currentTimeMillis();
			}
		}

		public long incrementInvalidUrlCount() {
			try {
				return invalidUrls.incrementAndGet();
			} finally {
				this.timestamp = System.currentTimeMillis();
			}
		}

		public long incrementExistedUrlCount() {
			try {
				return existedUrls.incrementAndGet();
			} finally {
				this.timestamp = System.currentTimeMillis();
			}
		}

		public long incrementFilteredUrlCount() {
			try {
				return filteredUrls.incrementAndGet();
			} finally {
				this.timestamp = System.currentTimeMillis();
			}
		}

		public long incrementSavedCount() {
			try {
				return saved.incrementAndGet();
			} finally {
				this.timestamp = System.currentTimeMillis();
			}
		}

		public long incrementIndexedCount() {
			return indexed.incrementAndGet();
		}

		public long incrementUrlCount(int delta) {
			try {
				return urls.addAndGet(delta);
			} finally {
				this.timestamp = System.currentTimeMillis();
			}
		}

		public long incrementInvalidUrlCount(int delta) {
			try {
				return invalidUrls.addAndGet(delta);
			} finally {
				this.timestamp = System.currentTimeMillis();
			}
		}

		public long incrementExistedUrlCount(int delta) {
			try {
				return existedUrls.addAndGet(delta);
			} finally {
				this.timestamp = System.currentTimeMillis();
			}
		}

		public long incrementFilteredUrlCount(int delta) {
			try {
				return filteredUrls.addAndGet(delta);
			} finally {
				this.timestamp = System.currentTimeMillis();
			}
		}

		public long incrementSavedCount(int delta) {
			try {
				return saved.addAndGet(delta);
			} finally {
				this.timestamp = System.currentTimeMillis();
			}
		}

		public long incrementIndexedCount(int delta) {
			try {
				return indexed.addAndGet(delta);
			} finally {
				this.timestamp = System.currentTimeMillis();
			}
		}

		public long getUrlCount() {
			return urls.get();
		}

		public long getInvalidUrlCount() {
			return invalidUrls.get();
		}

		public long getExistedUrlCount() {
			return existedUrls.get();
		}

		public long getFilteredUrlCount() {
			return filteredUrls.get();
		}

		public long getSavedCount() {
			return saved.get();
		}

		public long getIndexedCount() {
			return indexed.get();
		}

		public long getStartTime() {
			return startTime.get();
		}

		public long getElapsedTime() {
			return startTime.get() > 0 ? System.currentTimeMillis() - startTime.get() : 0;
		}

		public long getTimestamp() {
			return timestamp;
		}

		public String toString() {
			return ToStringBuilder.reflectionToString(this);
		}
	}

}
