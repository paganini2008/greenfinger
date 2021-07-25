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

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.github.paganini2008.springdessert.reditools.common.RedisBloomFilter;

/**
 * 
 * BloomFilterPathFilterFactory
 *
 * @author Fred Feng
 * 
 * @since 1.0
 */
public class BloomFilterPathFilterFactory implements PathFilterFactory {

	private static final String defaultRedisKeyPrefix = "spring:webcrawler:cluster:%s:catalog:bloomFiter:%s";
	private static final int maxExpectedInsertions = 100000000;
	private final RedisConnectionFactory redisConnectionFactory;
	private final RedisOperations<String, String> redisOperations;
	private final String crawlerName;

	public BloomFilterPathFilterFactory(String crawlerName, RedisConnectionFactory redisConnectionFactory) {
		this.crawlerName = crawlerName;
		this.redisConnectionFactory = redisConnectionFactory;
		this.redisOperations = new StringRedisTemplate(redisConnectionFactory);
	}

	@Override
	public void clean(long catalogId) {
		String key = String.format(defaultRedisKeyPrefix, crawlerName, catalogId);
		redisOperations.delete(key);
	}

	@Override
	public PathFilter getPathFilter(long catalogId) {
		String key = String.format(defaultRedisKeyPrefix, crawlerName, catalogId);
		RedisBloomFilter bloomFilter = new RedisBloomFilter(key, maxExpectedInsertions, 0.03d, redisConnectionFactory);
		return new BloomFilterPathFilter(bloomFilter);
	}

}
