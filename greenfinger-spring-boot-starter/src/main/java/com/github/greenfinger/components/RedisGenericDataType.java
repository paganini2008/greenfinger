package com.github.greenfinger.components;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * 
 * @Description: RedisGenericDataType
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@SuppressWarnings("all")
public class RedisGenericDataType<T> {

    public RedisGenericDataType(String key, Class<T> valueClass,
            RedisConnectionFactory redisConnectionFactory) {
        this(key, valueClass, redisConnectionFactory, null);
    }

    public RedisGenericDataType(String key, Class<T> valueClass,
            RedisConnectionFactory redisConnectionFactory, T defaultValue) {
        this.key = key;

        RedisTemplate<String, T> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(RedisSerializer.string());
        redisTemplate.setValueSerializer(new GenericToStringSerializer<T>(valueClass));
        redisTemplate.setHashKeySerializer(RedisSerializer.string());
        redisTemplate.setHashValueSerializer(new GenericToStringSerializer<T>(valueClass));
        redisTemplate.setExposeConnection(true);
        redisTemplate.afterPropertiesSet();
        this.redisTemplate = redisTemplate;

        if (defaultValue != null) {
            setIfAbsent(defaultValue);
        }
    }

    private final RedisTemplate<String, T> redisTemplate;
    private final String key;

    public void set(T value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void setIfAbsent(T value) {
        redisTemplate.opsForValue().setIfAbsent(key, value);
    }

    public void set(T value, long expiration, TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(key, value, expiration, timeUnit);
    }

    public void setIfAbsent(T value, long expiration, TimeUnit timeUnit) {
        redisTemplate.opsForValue().setIfAbsent(key, value, expiration, timeUnit);
    }

    public T get() {
        return redisTemplate.opsForValue().get(key);
    }

    public Long leftPush(T value) {
        return redisTemplate.opsForList().leftPush(key, value);
    }

    public Long leftPushAll(Collection<T> c) {
        return redisTemplate.opsForList().leftPushAll(key, c);
    }

    public Long rightPush(T value) {
        return redisTemplate.opsForList().rightPush(key, value);
    }

    public Long rightPushAll(Collection<T> c) {
        return redisTemplate.opsForList().rightPushAll(key, c);
    }

    public T leftPop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    public T rightPop(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }

    public T indexOfList(long index) {
        return redisTemplate.opsForList().index(key, index);
    }

    public Long removeFromList(long count, T value) {
        return redisTemplate.opsForList().remove(key, count, value);
    }

    public List<T> rangeOfList(long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    public long sizeOfList() {
        Long l = redisTemplate.opsForList().size(key);
        return l != null ? l.longValue() : 0;
    }

    public void putHash(String hashKey, T value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    public Boolean putHashIfAbsent(String hashKey, T value) {
        return redisTemplate.opsForHash().putIfAbsent(key, hashKey, value);
    }

    public T getHash(String hashKey) {
        return (T) redisTemplate.opsForHash().get(key, hashKey);
    }

    public boolean hasHashKey(String hashKey) {
        return redisTemplate.opsForHash().hasKey(key, hashKey);
    }

    public List<T> multiGetHash(String... hashKeys) {
        return (List<T>) redisTemplate.opsForHash().multiGet(key, Arrays.asList(hashKeys));
    }

    public Long deleteHash(String... hashKeys) {
        return redisTemplate.opsForHash().delete(key, hashKeys);
    }

    public long sizeOfHash() {
        return redisTemplate.opsForHash().size(key);
    }

}
