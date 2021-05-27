package indi.atlantis.framework.greenfinger;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.github.paganini2008.springworld.reditools.common.RedisBloomFilter;

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
