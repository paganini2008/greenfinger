package com.github.greenfinger.components;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.support.atomic.RedisAtomicLong;

/**
 * 
 * @Description: OneTimeDashboard
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
public class OneTimeDashboard implements Dashboard {

    private final RedisAtomicLong totalUrlCount;
    private final RedisAtomicLong invalidUrlCount;
    private final RedisAtomicLong existingUrlCount;
    private final RedisAtomicLong filteredUrlCount;
    private final RedisAtomicLong savedResourceCount;
    private final RedisAtomicLong indexedResourceCount;
    private final RedisGenericDataType<Long> startTime;
    private final RedisGenericDataType<Long> endTime;
    private final RedisGenericDataType<Boolean> completed;
    private long timestamp;

    public OneTimeDashboard(long catalogId, int version,
            RedisConnectionFactory redisConnectionFactory) {
        startTime = new RedisGenericDataType<Long>(
                String.format(NAMESPACE_PATTERN, catalogId, version, "startTime"), Long.class,
                redisConnectionFactory, System.currentTimeMillis());
        endTime = new RedisGenericDataType<Long>(
                String.format(NAMESPACE_PATTERN, catalogId, version, "endTime"), Long.class,
                redisConnectionFactory);
        totalUrlCount = new RedisAtomicLong(
                String.format(NAMESPACE_PATTERN, catalogId, version, "totalUrlCount"),
                redisConnectionFactory);
        invalidUrlCount = new RedisAtomicLong(
                String.format(NAMESPACE_PATTERN, catalogId, version, "invalidUrlCount"),
                redisConnectionFactory);
        existingUrlCount = new RedisAtomicLong(
                String.format(NAMESPACE_PATTERN, catalogId, version, "existingUrlCount"),
                redisConnectionFactory);
        filteredUrlCount = new RedisAtomicLong(
                String.format(NAMESPACE_PATTERN, catalogId, version, "filteredUrlCount"),
                redisConnectionFactory);
        savedResourceCount = new RedisAtomicLong(
                String.format(NAMESPACE_PATTERN, catalogId, version, "savedResourceCount"),
                redisConnectionFactory);
        indexedResourceCount = new RedisAtomicLong(
                String.format(NAMESPACE_PATTERN, catalogId, version, "indexedResourceCount"),
                redisConnectionFactory);
        completed = new RedisGenericDataType<Boolean>(
                String.format(NAMESPACE_PATTERN, catalogId, version, "completed"), Boolean.class,
                redisConnectionFactory, true);
    }

    @Override
    public void reset(long durationInMs) {
        startTime.set(System.currentTimeMillis());
        endTime.set(startTime.get() + durationInMs);
        totalUrlCount.set(0);
        invalidUrlCount.set(0);
        existingUrlCount.set(0);
        filteredUrlCount.set(0);
        savedResourceCount.set(0);
        indexedResourceCount.set(0);
        completed.set(false);
        timestamp = System.currentTimeMillis();
    }

    @Override
    public boolean isCompleted() {
        return completed.get();
    }

    @Override
    public void setCompleted(boolean completed) {
        this.completed.set(completed);
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public long incrementCount(CountingType countingType) {
        return incrementCount(countingType, 1);
    }

    @Override
    public long incrementCount(CountingType countingType, int delta) {
        RedisAtomicLong longCounter = null;
        switch (countingType) {
            case URL_TOTAL_COUNT:
                longCounter = totalUrlCount;
                break;
            case INVALID_URL_COUNT:
                longCounter = invalidUrlCount;
                break;
            case EXISTING_URL_COUNT:
                longCounter = existingUrlCount;
                break;
            case FILTERED_URL_COUNT:
                longCounter = filteredUrlCount;
                break;
            case SAVED_RESOURCE_COUNT:
                longCounter = savedResourceCount;
                break;
            case INDEXED_RESOURCE_COUNT:
                longCounter = indexedResourceCount;
                break;
            default:
                throw new UnsupportedOperationException(
                        "Unknown incremental type: " + countingType);
        }
        try {
            if (delta == 1) {
                return longCounter.incrementAndGet();
            }
            return longCounter.addAndGet(delta);
        } finally {
            this.timestamp = System.currentTimeMillis();
        }
    }

    @Override
    public long getTotalUrlCount() {
        return totalUrlCount.get();
    }

    @Override
    public long getInvalidUrlCount() {
        return invalidUrlCount.get();
    }

    @Override
    public long getExistingUrlCount() {
        return existingUrlCount.get();
    }

    @Override
    public long getFilteredUrlCount() {
        return filteredUrlCount.get();
    }

    @Override
    public long getSavedResourceCount() {
        return savedResourceCount.get();
    }

    @Override
    public long getIndexedResourceCount() {
        return indexedResourceCount.get();
    }

    @Override
    public long getStartTime() {
        return startTime.get();
    }

    @Override
    public long getEndTime() {
        return endTime.get();
    }

    @Override
    public long getElapsedTime() {
        return startTime.get() > 0 ? System.currentTimeMillis() - startTime.get() : 0;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String getDescription() {
        return "OneTimeDashboard";
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
