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

import com.github.greenfinger.CatalogDetails;

/**
 * 
 * @Description: ReadonlyDashboard
 * @Author: Fred Feng
 * @Date: 23/01/2025
 * @Version 1.0.0
 */
public class ReadonlyDashboard implements Dashboard {

    private final CatalogDetails catalogDetails;
    private final long totalUrlCount;
    private final long invalidUrlCount;
    private final long existingUrlCount;
    private final long filteredUrlCount;
    private final long savedResourceCount;
    private final long indexedResourceCount;
    private final long startTime;
    private final long endTime;
    private final long elapsedTime;
    private final double averageExecutionTime;
    private final long lastModified;
    private final String repr;

    public ReadonlyDashboard(Dashboard dashboard) {
        this.catalogDetails = dashboard.getCatalogDetails();
        this.totalUrlCount = dashboard.getTotalUrlCount();
        this.invalidUrlCount = dashboard.getInvalidUrlCount();
        this.existingUrlCount = dashboard.getExistingUrlCount();
        this.filteredUrlCount = dashboard.getFilteredUrlCount();
        this.savedResourceCount = dashboard.getSavedResourceCount();
        this.indexedResourceCount = dashboard.getIndexedResourceCount();
        this.startTime = dashboard.getStartTime();
        this.endTime = dashboard.getEndTime();
        this.elapsedTime = dashboard.getElapsedTime();
        this.averageExecutionTime = dashboard.getAverageExecutionTime();
        this.lastModified = dashboard.getLastModified();
        this.repr = dashboard.toString();
    }

    @Override
    public CatalogDetails getCatalogDetails() {
        return catalogDetails;
    }

    @Override
    public boolean isCompleted() {
        return true;
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
    public double getAverageExecutionTime() {
        return averageExecutionTime;
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
    public long getLastModified() {
        return lastModified;
    }

    @Override
    public String toString() {
        return repr;
    }

}
