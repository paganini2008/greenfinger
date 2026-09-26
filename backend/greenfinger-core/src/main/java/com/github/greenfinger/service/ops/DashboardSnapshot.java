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

package com.github.greenfinger.service.ops;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.state.Dashboard;

/**
 * The counters of one crawl, flattened so they can travel and be rendered by a process that is not
 * running it.
 *
 * <p>
 * The derived getters are left out of the json in both directions. They are computed from the
 * fields that are in it, so sending them is redundant -- and {@code getProgress()} reads the
 * catalog's counting type, which makes writing a half-filled dashboard throw rather than produce
 * a frame. Unknown fields are ignored on the way in as well, so a node running a newer build can
 * send a field an older one has never heard of.
 *
 * @Description: DashboardSnapshot
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
@JsonIgnoreProperties(value = {"progress", "remainingTime"}, ignoreUnknown = true)
public class DashboardSnapshot implements Dashboard {
    private long totalUrlCount;
    private long handledUrlCount;
    private long invalidUrlCount;
    private int consecutiveFailures;
    private String lastFailure;
    private long existingUrlCount;
    private long filteredUrlCount;
    private long savedResourceCount;
    private long indexedResourceCount;
    private long vectoredResourceCount;
    private long savedImageCount;
    private long duplicatedContentCount;
    private long abandonedUrlCount;
    private long startTime;
    private long endTime;
    private long elapsedTime;
    private long lastModified;
    private boolean completed;
    private String completionReason;
    private boolean interrupted;
    private double averageExecutionTime;

    @Override
    public long getTotalUrlCount() {
        return totalUrlCount;
    }

    public void setTotalUrlCount(long totalUrlCount) {
        this.totalUrlCount = totalUrlCount;
    }

    @Override
    public long getHandledUrlCount() {
        return handledUrlCount;
    }

    public void setHandledUrlCount(long handledUrlCount) {
        this.handledUrlCount = handledUrlCount;
    }

    @Override
    public long getInvalidUrlCount() {
        return invalidUrlCount;
    }

    public void setInvalidUrlCount(long invalidUrlCount) {
        this.invalidUrlCount = invalidUrlCount;
    }

    @Override
    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(int consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    @Override
    public String getLastFailure() {
        return lastFailure;
    }

    public void setLastFailure(String lastFailure) {
        this.lastFailure = lastFailure;
    }

    @Override
    public long getExistingUrlCount() {
        return existingUrlCount;
    }

    public void setExistingUrlCount(long existingUrlCount) {
        this.existingUrlCount = existingUrlCount;
    }

    @Override
    public long getFilteredUrlCount() {
        return filteredUrlCount;
    }

    public void setFilteredUrlCount(long filteredUrlCount) {
        this.filteredUrlCount = filteredUrlCount;
    }

    @Override
    public long getSavedResourceCount() {
        return savedResourceCount;
    }

    public void setSavedResourceCount(long savedResourceCount) {
        this.savedResourceCount = savedResourceCount;
    }

    @Override
    public long getIndexedResourceCount() {
        return indexedResourceCount;
    }

    public void setIndexedResourceCount(long indexedResourceCount) {
        this.indexedResourceCount = indexedResourceCount;
    }

    @Override
    public long getVectoredResourceCount() {
        return vectoredResourceCount;
    }

    public void setVectoredResourceCount(long vectoredResourceCount) {
        this.vectoredResourceCount = vectoredResourceCount;
    }

    @Override
    public long getSavedImageCount() {
        return savedImageCount;
    }

    public void setSavedImageCount(long savedImageCount) {
        this.savedImageCount = savedImageCount;
    }

    @Override
    public long getDuplicatedContentCount() {
        return duplicatedContentCount;
    }

    public void setDuplicatedContentCount(long duplicatedContentCount) {
        this.duplicatedContentCount = duplicatedContentCount;
    }

    @Override
    public long getAbandonedUrlCount() {
        return abandonedUrlCount;
    }

    public void setAbandonedUrlCount(long abandonedUrlCount) {
        this.abandonedUrlCount = abandonedUrlCount;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    @Override
    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    @Override
    public long getElapsedTime() {
        return elapsedTime;
    }

    public void setElapsedTime(long elapsedTime) {
        this.elapsedTime = elapsedTime;
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    @Override
    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    @Override
    public String getCompletionReason() {
        return completionReason;
    }

    public void setCompletionReason(String completionReason) {
        this.completionReason = completionReason;
    }

    @Override
    public boolean isInterrupted() {
        return interrupted;
    }

    public void setInterrupted(boolean interrupted) {
        this.interrupted = interrupted;
    }

    @Override
    public double getAverageExecutionTime() {
        return averageExecutionTime;
    }

    public void setAverageExecutionTime(double averageExecutionTime) {
        this.averageExecutionTime = averageExecutionTime;
    }

    private CatalogSnapshot catalogDetails;

    @Override
    public CatalogDetails getCatalogDetails() {
        return catalogDetails;
    }

    public void setCatalogDetails(CatalogSnapshot catalogDetails) {
        this.catalogDetails = catalogDetails;
    }

    /** A copy of the counters as they stand, with the catalog they belong to. */
    public static DashboardSnapshot of(Dashboard dashboard) {
        if (dashboard == null) {
            return null;
        }
        DashboardSnapshot copy = new DashboardSnapshot();
        copy.totalUrlCount = dashboard.getTotalUrlCount();
        copy.handledUrlCount = dashboard.getHandledUrlCount();
        copy.invalidUrlCount = dashboard.getInvalidUrlCount();
        copy.consecutiveFailures = dashboard.getConsecutiveFailures();
        copy.lastFailure = dashboard.getLastFailure();
        copy.existingUrlCount = dashboard.getExistingUrlCount();
        copy.filteredUrlCount = dashboard.getFilteredUrlCount();
        copy.savedResourceCount = dashboard.getSavedResourceCount();
        copy.indexedResourceCount = dashboard.getIndexedResourceCount();
        copy.vectoredResourceCount = dashboard.getVectoredResourceCount();
        copy.savedImageCount = dashboard.getSavedImageCount();
        copy.duplicatedContentCount = dashboard.getDuplicatedContentCount();
        copy.abandonedUrlCount = dashboard.getAbandonedUrlCount();
        copy.startTime = dashboard.getStartTime();
        copy.endTime = dashboard.getEndTime();
        copy.elapsedTime = dashboard.getElapsedTime();
        copy.lastModified = dashboard.getLastModified();
        copy.completed = dashboard.isCompleted();
        copy.completionReason = dashboard.getCompletionReason();
        copy.interrupted = dashboard.isInterrupted();
        copy.averageExecutionTime = dashboard.getAverageExecutionTime();
        copy.catalogDetails = CatalogSnapshot.of(dashboard.getCatalogDetails());
        return copy;
    }

}
