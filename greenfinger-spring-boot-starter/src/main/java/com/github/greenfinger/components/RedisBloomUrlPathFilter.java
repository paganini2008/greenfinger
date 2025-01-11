package com.github.greenfinger.components;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.github.doodler.common.redis.RedisBloomFilter;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: RedisBloomUrlPathFilter
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@Slf4j
public class RedisBloomUrlPathFilter extends RedisBasedExistingUrlPathFilter
        implements InitializingBean {

    private static final int MAX_EXPECTED_INSERTIONS = 100_000_000;

    public RedisBloomUrlPathFilter(long catalogId, int version,
            RedisConnectionFactory redisConnectionFactory) {
        super(catalogId, version);
        this.redisConnectionFactory = redisConnectionFactory;
    }

    private final RedisConnectionFactory redisConnectionFactory;
    private RedisBloomFilter bloomFilter;


    @Override
    public void afterPropertiesSet() throws Exception {
        this.bloomFilter =
                new RedisBloomFilter(key, MAX_EXPECTED_INSERTIONS, 0.03d, redisConnectionFactory);
    }

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
            log.info("Clean RedisUrlPathFilter: {}", key);
        }
    }
}
