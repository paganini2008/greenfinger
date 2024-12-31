package com.github.greenfinger;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.github.greenfinger.utils.ExistingUrlPathFilter;
import com.github.greenfinger.utils.RedisBloomFilter;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: BloomFilterUrlPathFilter
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@Slf4j
public class BloomFilterUrlPathFilter implements ExistingUrlPathFilter {

    private static final int MAX_EXPECTED_INSERTIONS = 100_000_000;

    public BloomFilterUrlPathFilter(long catalogId, int version,
            RedisConnectionFactory redisConnectionFactory) {
        this.key = String.format(NAMESPACE_PATTERN, catalogId, version);
        this.bloomFilter =
                new RedisBloomFilter(key, MAX_EXPECTED_INSERTIONS, 0.03d, redisConnectionFactory);
        this.redisConnectionFactory = redisConnectionFactory;
    }

    private final String key;
    private final RedisBloomFilter bloomFilter;
    private final RedisConnectionFactory redisConnectionFactory;

    @Override
    public boolean mightExist(String path) {
        boolean existed;
        if ((existed = bloomFilter.mightContain(path)) == false) {
            bloomFilter.put(path);
        }
        return existed;
    }

    @Override
    public void clean() {
        StringRedisTemplate redisTemplate = new StringRedisTemplate(redisConnectionFactory);
        redisTemplate.delete(key);
        if (log.isInfoEnabled()) {
            log.info("Clean RedisUrlPathFilter on key: {}", key);
        }
    }

}
