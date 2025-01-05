package com.github.greenfinger.utils;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: RedisUrlPathFilter
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@Slf4j
public class RedisUrlPathFilter implements ExistingUrlPathFilter {


    private final String key;
    private final StringRedisTemplate redisTemplate;

    public RedisUrlPathFilter(long catalogId, int version,
            RedisConnectionFactory redisConnectionFactory) {
        this(String.format(NAMESPACE_PATTERN, catalogId, version), redisConnectionFactory);
    }

    public RedisUrlPathFilter(String key, RedisConnectionFactory redisConnectionFactory) {
        this.key = key;
        this.redisTemplate = new StringRedisTemplate(redisConnectionFactory);
    }

    @Override
    public boolean mightExist(String path) {
        boolean existed;
        if (!(existed = redisTemplate.opsForSet().isMember(key, path))) {
            redisTemplate.opsForSet().add(key, path);
        }
        return existed;
    }

    @Override
    public void clean() {
        redisTemplate.delete(key);
        if (log.isInfoEnabled()) {
            log.info("Clean RedisUrlPathFilter on key: {}", key);
        }
    }
}
