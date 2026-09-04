/*
 * Copyright 2017-2026 Fred Feng (paganini.fy@gmail.com)
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

package com.github.greenfinger.core.component.state;

import java.text.DateFormat;
import java.util.Date;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.time.DurationFormatUtils;
import com.github.greenfinger.core.ManagedBeanLifeCycle;
import com.github.greenfinger.core.catalog.CatalogDetails;

/**
 * In-memory counters for one crawl. 1.x kept these in Redis because several nodes shared them; a
 * standalone crawl has one writer, so atomics are both correct and considerably cheaper.
 * 
 * @Description: DefaultDashboard
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class DefaultDashboard implements Dashboard, ManagedBeanLifeCycle {

    private final CatalogDetails catalogDetails;

    final AtomicLong totalUrlCount = new AtomicLong(0);
    final AtomicLong handledUrlCount = new AtomicLong(0);
    final AtomicLong invalidUrlCount = new AtomicLong(0);
    final AtomicLong existingUrlCount = new AtomicLong(0);
    final AtomicLong filteredUrlCount = new AtomicLong(0);
    final AtomicLong savedResourceCount = new AtomicLong(0);
    final AtomicLong indexedResourceCount = new AtomicLong(0);
    final AtomicLong savedImageCount = new AtomicLong(0);
    final AtomicLong duplicatedContentCount = new AtomicLong(0);

    final AtomicBoolean completed = new AtomicBoolean(false);
    volatile String completionReason;
    volatile boolean interrupted;
    final Map<CountingType, ElapsedWindow> elapsed = new EnumMap<>(CountingType.class);

    volatile long startTime;
    volatile long endTime;
    volatile long lastModified;

    public DefaultDashboard(CatalogDetails catalogDetails) {
        this.catalogDetails = catalogDetails;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        completed.set(false);
        completionReason = null;
        interrupted = false;
        startTime = System.currentTimeMillis();
        endTime = startTime + TimeUnit.MINUTES.toMillis(catalogDetails.getFetchDuration());
        lastModified = startTime;
        totalUrlCount.set(0);
        handledUrlCount.set(0);
        invalidUrlCount.set(0);
        existingUrlCount.set(0);
        filteredUrlCount.set(0);
        savedResourceCount.set(0);
        indexedResourceCount.set(0);
        savedImageCount.set(0);
        duplicatedContentCount.set(0);
        elapsed.clear();
    }

    AtomicLong counterOf(CountingType countingType) {
        switch (countingType) {
            case URL_TOTAL_COUNT:
                return totalUrlCount;
            case HANDLED_URL_COUNT:
                return handledUrlCount;
            case INVALID_URL_COUNT:
                return invalidUrlCount;
            case EXISTING_URL_COUNT:
                return existingUrlCount;
            case FILTERED_URL_COUNT:
                return filteredUrlCount;
            case SAVED_RESOURCE_COUNT:
                return savedResourceCount;
            case INDEXED_RESOURCE_COUNT:
                return indexedResourceCount;
            case SAVED_IMAGE_COUNT:
                return savedImageCount;
            case DUPLICATED_CONTENT_COUNT:
                return duplicatedContentCount;
            default:
                throw new UnsupportedOperationException(
                        "Unknown incremental counting type: " + countingType);
        }
    }

    @Override
    public long getTotalUrlCount() {
        return totalUrlCount.get();
    }

    @Override
    public long getHandledUrlCount() {
        return handledUrlCount.get();
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
    public long getSavedImageCount() {
        return savedImageCount.get();
    }

    @Override
    public long getDuplicatedContentCount() {
        return duplicatedContentCount.get();
    }

    @Override
    public boolean isCompleted() {
        return completed.get();
    }

    @Override
    public String getCompletionReason() {
        return completionReason;
    }

    @Override
    public boolean isInterrupted() {
        return interrupted;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    public long getEndTime() {
        return endTime;
    }

    @Override
    public long getElapsedTime() {
        if (startTime <= 0) {
            return 0L;
        }
        return isCompleted() ? Math.max(0L, lastModified - startTime)
                : System.currentTimeMillis() - startTime;
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    @Override
    public double getAverageExecutionTime() {
        ElapsedWindow window = elapsed.get(catalogDetails.getCountingType());
        return window != null ? window.average() : 0d;
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
        String line = "%-18s %-14s %-14s %-14s %-14s %-14s %-14s %-14s%n";
        str.append(String.format(line, "StartTime", "TotalUrls", "InvalidUrls", "ExistingUrls",
                "FilteredUrls", "SavedRes", "SavedImages", "DupContent"));
        str.append(String.format(line, dateFormat.format(new Date(getStartTime())),
                getTotalUrlCount(), getInvalidUrlCount(), getExistingUrlCount(),
                getFilteredUrlCount(), getSavedResourceCount(), getSavedImageCount(),
                getDuplicatedContentCount()));
        str.append(String.format("Elapsed: %s, Completed: %s",
                DurationFormatUtils.formatDuration(getElapsedTime(), "H'h' m'm' s's'"),
                isCompleted()));
        return str.toString();
    }

    /**
     * A bounded window of recent durations, kept so the average does not drift with the whole
     * history of a long crawl.
     * 
     * @Description: ElapsedWindow
     * @Author: Fred Feng
     * @Date: 29/08/2026
     * @Version 2.0.0
     */
    static class ElapsedWindow {

        private static final int CAPACITY = 256;
        private final long[] values = new long[CAPACITY];
        private int size;
        private int cursor;

        synchronized void add(long value) {
            if (value <= 0) {
                return;
            }
            values[cursor] = value;
            cursor = (cursor + 1) % CAPACITY;
            if (size < CAPACITY) {
                size++;
            }
        }

        synchronized double average() {
            if (size == 0) {
                return 0d;
            }
            long total = 0;
            for (int i = 0; i < size; i++) {
                total += values[i];
            }
            return (double) total / size;
        }

    }

}
