package com.github.greenfinger.components;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.InitializingBean;

/**
 * 
 * @Description: RedissionBloomUrlPathFilter
 * @Author: Fred Feng
 * @Date: 01/01/2025
 * @Version 1.0.0
 */
public class RedissionBloomUrlPathFilter extends RedisBasedUrlPathFilter
        implements InitializingBean {

    private static final int MAX_EXPECTED_INSERTIONS = 100_000_000;
    private final RedissonClient redissonClient;

    public RedissionBloomUrlPathFilter(long catalogId, int version, RedissonClient redissonClient) {
        super(catalogId, version);
        this.redissonClient = redissonClient;
    }

    public RedissionBloomUrlPathFilter(String key, RedissonClient redissonClient) {
        super(key);
        this.redissonClient = redissonClient;
    }

    private RBloomFilter<Object> bloomFilter;

    @Override
    public void afterPropertiesSet() throws Exception {
        bloomFilter = redissonClient.getBloomFilter(key);
        bloomFilter.tryInit(MAX_EXPECTED_INSERTIONS, 0.03d);
    }

    @Override
    public boolean mightExist(String path) {
        boolean result;
        if (!(result = bloomFilter.contains(path))) {
            bloomFilter.add(path);
        }
        return result;
    }

    @Override
    public void clean() {
        if (bloomFilter != null) {
            bloomFilter.deleteAsync();
        }
    }

    @Override
    public long size() {
        return bloomFilter.count();
    }

}
