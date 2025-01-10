package com.github.greenfinger.components;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.support.atomic.RedisAtomicLong;

/**
 * 
 * @Description: OneTimeDashboardData
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
public class OneTimeDashboardData {

    private static final String NAMESPACE_PATTERN = "greenfinger:dashboard:%s:%s:%s";

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

    public OneTimeDashboardData(long catalogId, int version,
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

    public void reset(long durationInMs, boolean includingCount) {
        startTime.set(System.currentTimeMillis());
        endTime.set(startTime.get() + durationInMs);
        if (includingCount) {
            totalUrlCount.set(0);
            invalidUrlCount.set(0);
            existingUrlCount.set(0);
            filteredUrlCount.set(0);
            savedResourceCount.set(0);
            indexedResourceCount.set(0);
        }
        completed.set(false);
        timestamp = System.currentTimeMillis();
    }

    public boolean isCompleted() {
        return completed.get();
    }

    public void setCompleted(boolean completed) {
        this.completed.set(completed);
        this.timestamp = System.currentTimeMillis();
    }

    public long incrementTotalUrlCount() {
        try {
            return totalUrlCount.incrementAndGet();
        } finally {
            this.timestamp = System.currentTimeMillis();
        }
    }

    public long incrementInvalidUrlCount() {
        try {
            return invalidUrlCount.incrementAndGet();
        } finally {
            this.timestamp = System.currentTimeMillis();
        }
    }

    public long incrementExistingUrlCount() {
        try {
            return existingUrlCount.incrementAndGet();
        } finally {
            this.timestamp = System.currentTimeMillis();
        }
    }

    public long incrementFilteredUrlCount() {
        try {
            return filteredUrlCount.incrementAndGet();
        } finally {
            this.timestamp = System.currentTimeMillis();
        }
    }

    public long incrementSavedResourceCount() {
        try {
            return savedResourceCount.incrementAndGet();
        } finally {
            this.timestamp = System.currentTimeMillis();
        }
    }

    public long incrementIndexedResourceCount() {
        return indexedResourceCount.incrementAndGet();
    }

    public long incrementTotalUrlCount(int delta) {
        try {
            return totalUrlCount.addAndGet(delta);
        } finally {
            this.timestamp = System.currentTimeMillis();
        }
    }

    public long incrementInvalidUrlCount(int delta) {
        try {
            return invalidUrlCount.addAndGet(delta);
        } finally {
            this.timestamp = System.currentTimeMillis();
        }
    }

    public long incrementExistingUrlCount(int delta) {
        try {
            return existingUrlCount.addAndGet(delta);
        } finally {
            this.timestamp = System.currentTimeMillis();
        }
    }

    public long incrementFilteredUrlCount(int delta) {
        try {
            return filteredUrlCount.addAndGet(delta);
        } finally {
            this.timestamp = System.currentTimeMillis();
        }
    }

    public long incrementSavedResourceCount(int delta) {
        try {
            return savedResourceCount.addAndGet(delta);
        } finally {
            this.timestamp = System.currentTimeMillis();
        }
    }

    public long incrementIndexedResourceCount(int delta) {
        try {
            return indexedResourceCount.addAndGet(delta);
        } finally {
            this.timestamp = System.currentTimeMillis();
        }
    }

    public long getTotalUrlCount() {
        return totalUrlCount.get();
    }

    public long getInvalidUrlCount() {
        return invalidUrlCount.get();
    }

    public long getExistingUrlCount() {
        return existingUrlCount.get();
    }

    public long getFilteredUrlCount() {
        return filteredUrlCount.get();
    }

    public long getSavedResourceCount() {
        return savedResourceCount.get();
    }

    public long getIndexedResourceCount() {
        return indexedResourceCount.get();
    }

    public long getStartTime() {
        return startTime.get();
    }

    public long getEndTime() {
        return endTime.get();
    }

    public long getElapsedTime() {
        return startTime.get() > 0 ? System.currentTimeMillis() - startTime.get() : 0;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
