package com.github.greenfinger.components;

/**
 * 
 * @Description: RedisBasedUrlPathFilter
 * @Author: Fred Feng
 * @Date: 11/01/2025
 * @Version 1.0.0
 */
public abstract class RedisBasedUrlPathFilter implements ExistingUrlPathFilter {

    static final String NAME_PATTERN_URL_PATH_FILTER = "greenfinger:url-path-filter:%s:%s";

    protected RedisBasedUrlPathFilter(long catalogId, int version) {
        this.key = String.format(NAME_PATTERN_URL_PATH_FILTER, catalogId, version);
    }

    protected RedisBasedUrlPathFilter(String key) {
        this.key = key;
    }

    protected final String key;

    public String getKey() {
        return key;
    }
}
