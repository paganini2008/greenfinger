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
public class RedissionBloomUrlPathFilter implements ExistingUrlPathFilter, InitializingBean {

    private static final int MAX_EXPECTED_INSERTIONS = 100_000_000;

    private final String name;
    private final RedissonClient redissonClient;

    public RedissionBloomUrlPathFilter(long catalogId, int version, RedissonClient redissonClient) {
        this(String.format(NAMESPACE_PATTERN, catalogId, version), redissonClient);
    }

    public RedissionBloomUrlPathFilter(String name, RedissonClient redissonClient) {
        this.name = name;
        this.redissonClient = redissonClient;
    }

    private RBloomFilter<Object> bloomFilter;

    @Override
    public void afterPropertiesSet() throws Exception {
        bloomFilter = redissonClient.getBloomFilter(name);
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
