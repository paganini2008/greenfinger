package com.github.greenfinger.components;

import java.util.concurrent.TimeUnit;
import com.github.doodler.common.utils.DateUtils;
import com.github.greenfinger.CatalogDetails;

/**
 * 
 * @Description: EmptyDashboard
 * @Author: Fred Feng
 * @Date: 27/01/2025
 * @Version 1.0.0
 */
class EmptyDashboard implements Dashboard {

    private final CatalogDetails catalogDetails;
    private final long timestamp;

    EmptyDashboard(CatalogDetails catalogDetails) {
        this.catalogDetails = catalogDetails;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public long getTotalUrlCount() {
        return 0;
    }

    @Override
    public long getInvalidUrlCount() {
        return 0;
    }

    @Override
    public long getExistingUrlCount() {
        return 0;
    }

    @Override
    public long getFilteredUrlCount() {
        return 0;
    }

    @Override
    public long getSavedResourceCount() {
        return 0;
    }

    @Override
    public long getIndexedResourceCount() {
        return 0;
    }

    @Override
    public long getStartTime() {
        return timestamp;
    }

    @Override
    public long getEndTime() {
        return timestamp
                + DateUtils.convertToMillis(catalogDetails.getFetchDuration(), TimeUnit.MINUTES);
    }

    @Override
    public long getElapsedTime() {
        return 0;
    }

    @Override
    public long getLastModified() {
        return System.currentTimeMillis();
    }

    @Override
    public boolean isCompleted() {
        return true;
    }

    @Override
    public double getAverageExecutionTime() {
        return 0;
    }

    @Override
    public CatalogDetails getCatalogDetails() {
        return catalogDetails;
    }

}
