/*
 * Copyright 2017-2025 Fred Feng (paganini.fy@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.greenfinger.components;

import static com.github.greenfinger.components.RedisGlobalStateManager.NAMESPACE_PATTERN;
import java.text.DateFormat;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.support.atomic.RedisAtomicLong;
import com.github.doodler.common.utils.DateUtils;
import com.github.greenfinger.CatalogDetails;

/**
 * 
 * @Description: RedisDashboard
 * @Author: Fred Feng
 * @Date: 26/01/2025
 * @Version 1.0.0
 */
public class RedisDashboard implements Dashboard, InitializingBean {

    public RedisDashboard(CatalogDetails catalogDetails,
            RedisConnectionFactory redisConnectionFactory) {
        long catalogId = catalogDetails.getId();
        int version = catalogDetails.getVersion();
        startTime = new RedisGenericDataType<Long>(
                String.format(NAMESPACE_PATTERN, catalogId, version, "startTime"), Long.class,
                redisConnectionFactory, System.currentTimeMillis());
        endTime = new RedisGenericDataType<Long>(
                String.format(NAMESPACE_PATTERN, catalogId, version, "endTime"), Long.class,
                redisConnectionFactory);
        completed = new RedisGenericDataType<Boolean>(
                String.format(NAMESPACE_PATTERN, catalogId, version, "completed"), Boolean.class,
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
        this.catalogDetails = catalogDetails;

    }

    final CatalogDetails catalogDetails;
    final RedisGenericDataType<Long> startTime;
    final RedisGenericDataType<Long> endTime;
    final RedisGenericDataType<Boolean> completed;
    final RedisAtomicLong totalUrlCount;
    final RedisAtomicLong invalidUrlCount;
    final RedisAtomicLong existingUrlCount;
    final RedisAtomicLong filteredUrlCount;
    final RedisAtomicLong savedResourceCount;
    final RedisAtomicLong indexedResourceCount;

    final EnumMap<CountingType, List<Long>> elapsed = new EnumMap<>(CountingType.class);
    long lastModified;

    @Override
    public void afterPropertiesSet() throws Exception {
        completed.set(false);
        startTime.set(System.currentTimeMillis());
        endTime.set(startTime.get()
                + DateUtils.convertToMillis(catalogDetails.getFetchDuration(), TimeUnit.MINUTES));
        totalUrlCount.set(0);
        invalidUrlCount.set(0);
        existingUrlCount.set(0);
        filteredUrlCount.set(0);
        savedResourceCount.set(0);
        indexedResourceCount.set(0);
        lastModified = System.currentTimeMillis();
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
    public boolean isCompleted() {
        return completed.exists() ? completed.get() : false;
    }

    @Override
    public long getStartTime() {
        return startTime.exists() ? startTime.get() : 0L;
    }

    @Override
    public long getEndTime() {
        return endTime.exists() ? endTime.get() : 0L;
    }

    @Override
    public double getAverageExecutionTime() {
        List<Long> list = elapsed.get(catalogDetails.getCountingType());
        if (CollectionUtils.isEmpty(list)) {
            return 0;
        }
        return list.stream().filter(e -> e > 0).mapToLong(Long::longValue).average().getAsDouble();
    }

    @Override
    public long getElapsedTime() {
        return getStartTime() > 0
                ? isCompleted() ? getEndTime() - getStartTime()
                        : System.currentTimeMillis() - getStartTime()
                : 0;
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    @Override
    public CatalogDetails getCatalogDetails() {
        return catalogDetails;
    }

    @Override
    public String toString() {
        DateFormat dateFormat =
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
        StringBuilder str = new StringBuilder();

        str.append(
                "╔══════════════════════════╦══════════════════════════╦════════════════╦════════════════╦════════════════╦════════════════╦════════════════╦════════════════╦═══════==═════════╗\n");
        str.append(String.format(
                "║ %-24s ║ %-24s ║ %-14s ║ %-14s ║ %-14s ║ %-14s ║ %-14s ║ %-14s ║ %-15s ║\n",
                "StartTime", "EndTime", "Completed", "TotalUrls", "InvalidUrls", "ExistingUrls",
                "FilteredUrls", "SavedResources", "IndexedResources"));
        str.append(
                "╠══════════════════════════╬══════════════════════════╬════════════════╬════════════════╬════════════════╬════════════════╬════════════════╬════════════════╬═════════════==═══╣\n");

        str.append(String.format(
                "║ %-24s ║ %-24s ║ %-14s ║ %-14s ║ %-14s ║ %-14s ║ %-14s ║ %-14s ║ %-16s ║\n",
                dateFormat.format(new Date(getStartTime())),
                dateFormat.format(new Date(getEndTime())), isCompleted(), getTotalUrlCount(),
                getInvalidUrlCount(), getExistingUrlCount(), getFilteredUrlCount(),
                getSavedResourceCount(), getIndexedResourceCount()));

        str.append(
                "╚══════════════════════════╩══════════════════════════╩════════════════╩════════════════╩════════════════╩════════════════╩════════════════╩════════════════╩═══════════==═════╝\n");

        return str.toString();
    }

}
