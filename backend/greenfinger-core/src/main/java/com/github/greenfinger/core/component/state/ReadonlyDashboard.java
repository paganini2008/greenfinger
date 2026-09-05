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

import com.github.greenfinger.core.catalog.CatalogDetails;

/**
 * An immutable snapshot, so a report cannot show counters that shifted while it was being rendered.
 * 
 * @Description: ReadonlyDashboard
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class ReadonlyDashboard implements Dashboard {

    private final CatalogDetails catalogDetails;
    private final long totalUrlCount;
    private final long handledUrlCount;
    private final long invalidUrlCount;
    private final long existingUrlCount;
    private final long filteredUrlCount;
    private final long savedResourceCount;
    private final long indexedResourceCount;
    private final long vectoredResourceCount;
    private final long savedImageCount;
    private final long duplicatedContentCount;
    private final long abandonedUrlCount;
    private final long startTime;
    private final long endTime;
    private final long elapsedTime;
    private final long lastModified;
    private final double averageExecutionTime;
    private final boolean completed;
    private final String completionReason;
    private final boolean interrupted;
    private final String repr;

    public ReadonlyDashboard(Dashboard dashboard) {
        this.catalogDetails = dashboard.getCatalogDetails();
        this.totalUrlCount = dashboard.getTotalUrlCount();
        this.handledUrlCount = dashboard.getHandledUrlCount();
        this.invalidUrlCount = dashboard.getInvalidUrlCount();
        this.existingUrlCount = dashboard.getExistingUrlCount();
        this.filteredUrlCount = dashboard.getFilteredUrlCount();
        this.savedResourceCount = dashboard.getSavedResourceCount();
        this.indexedResourceCount = dashboard.getIndexedResourceCount();
        this.vectoredResourceCount = dashboard.getVectoredResourceCount();
        this.savedImageCount = dashboard.getSavedImageCount();
        this.duplicatedContentCount = dashboard.getDuplicatedContentCount();
        this.abandonedUrlCount = dashboard.getAbandonedUrlCount();
        this.startTime = dashboard.getStartTime();
        this.endTime = dashboard.getEndTime();
        this.elapsedTime = dashboard.getElapsedTime();
        this.lastModified = dashboard.getLastModified();
        this.averageExecutionTime = dashboard.getAverageExecutionTime();
        this.completed = dashboard.isCompleted();
        // why it ended, and whether that was a finish or an intervention. Copied like every other
        // field: this snapshot is what the api answers with once the crawl is out of the registry,
        // and a snapshot that leaves them out reports every finished crawl as ended for no reason.
        this.completionReason = dashboard.getCompletionReason();
        this.interrupted = dashboard.isInterrupted();
        this.repr = dashboard.toString();
    }

    @Override
    public CatalogDetails getCatalogDetails() {
        return catalogDetails;
    }

    @Override
    public boolean isCompleted() {
        return completed;
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
    public long getTotalUrlCount() {
        return totalUrlCount;
    }

    @Override
    public long getHandledUrlCount() {
        return handledUrlCount;
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
    public long getVectoredResourceCount() {
        return vectoredResourceCount;
    }

    @Override
    public long getIndexedResourceCount() {
        return indexedResourceCount;
    }

    @Override
    public long getSavedImageCount() {
        return savedImageCount;
    }

    @Override
    public long getDuplicatedContentCount() {
        return duplicatedContentCount;
    }

    @Override
    public long getAbandonedUrlCount() {
        return abandonedUrlCount;
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
