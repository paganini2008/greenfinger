package com.github.greenfinger;

import com.github.greenfinger.components.CountingType;
import com.github.greenfinger.components.Dashboard;

/**
 * 
 * @Description: Snapshot
 * @Author: Fred Feng
 * @Date: 23/01/2025
 * @Version 1.0.0
 */
public class Snapshot implements Dashboard {

    private final long totalUrlCount;
    private final long invalidUrlCount;
    private final long existingUrlCount;
    private final long filteredUrlCount;
    private final long savedResourceCount;
    private final long indexedResourceCount;
    private final long startTime;
    private final long endTime;
    private final long elapsedTime;

    public Snapshot(Dashboard dashboard) {
        this.totalUrlCount = dashboard.getTotalUrlCount();
        this.invalidUrlCount = dashboard.getInvalidUrlCount();
        this.existingUrlCount = dashboard.getExistingUrlCount();
        this.filteredUrlCount = dashboard.getFilteredUrlCount();
        this.savedResourceCount = dashboard.getSavedResourceCount();
        this.indexedResourceCount = dashboard.getIndexedResourceCount();
        this.startTime = dashboard.getStartTime();
        this.endTime = dashboard.getEndTime();
        this.elapsedTime = dashboard.getElapsedTime();
    }

    @Override
    public void reset(long durationInMs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCompleted() {
        return true;
    }

    @Override
    public void setCompleted(boolean completed) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long incrementCount(CountingType countingType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long incrementCount(CountingType countingType, int delta) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getTotalUrlCount() {
        return totalUrlCount;
    }

    @Override
    public long getInvalidUrlCount() {
        return invalidUrlCount;
    }

    @Override
    public long getExistingUrlCount() {
        return existingUrlCount;
    }

    @Override
    public long getFilteredUrlCount() {
        return filteredUrlCount;
    }

    @Override
    public long getSavedResourceCount() {
        return savedResourceCount;
    }

    @Override
    public long getIndexedResourceCount() {
        return indexedResourceCount;
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
        return elapsedTime;
    }

    @Override
    public long getTimestamp() {
        return System.currentTimeMillis();
    }

}
