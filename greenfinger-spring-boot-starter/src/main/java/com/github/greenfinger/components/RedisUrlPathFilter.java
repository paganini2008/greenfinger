package com.github.greenfinger.components;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
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
public class RedisUrlPathFilter extends RedisBasedUrlPathFilter {

    private final StringRedisTemplate redisTemplate;

    public RedisUrlPathFilter(long catalogId, int version,
            RedisConnectionFactory redisConnectionFactory) {
        super(catalogId, version);
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
        if (redisTemplate.hasKey(key)) {
            redisTemplate.delete(key);
            if (log.isInfoEnabled()) {
                log.info("Clean RedisUrlPathFilter: {}", key);
            }
        }
    }

    @Override
    public long size() {
        return redisTemplate.opsForSet().size(key);
    }

    @Override
    public int export(UrlPathFilterExporter exporter, boolean deleted) throws Exception {
        Cursor<String> cursor = redisTemplate.opsForSet().scan(key, null);
        String item;
        int n = 0;
        while (cursor.hasNext()) {
            item = cursor.next();
            if (exporter != null) {
                if (exporter.doExport(n++, item)) {
                    if (deleted) {
                        redisTemplate.opsForSet().remove(key, item);
                    }
                } else {
                    break;
                }
            }
        }
        return n;
    }

}
