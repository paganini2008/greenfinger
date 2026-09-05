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

package com.github.greenfinger.api.web;

import java.util.Map;
import org.apache.commons.lang3.time.DurationFormatUtils;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.state.Dashboard;
import lombok.Getter;
import lombok.ToString;

/**
 * What the Monitor page shows: the counters of a run, live if one is going and from the last one if
 * not.
 *
 * <p>
 * The two sources are deliberately the same shape. A run that has just finished should not make
 * the page it was being watched on go blank, so the settings file each version writes is read back
 * into exactly the fields the live dashboard filled a second earlier.
 *
 * @Description: CatalogSummary
 * @Author: Fred Feng
 * @Date: 31/08/2026
 * @Version 2.0.0
 */
@Getter
@ToString
public class CatalogSummary {

    /** True while a crawl is in flight: the page polls faster when it is. */
    private final boolean live;

    private final String catalogId;
    private final String catalogName;
    private final Integer version;
    private final Integer searchVersion;

    private final long startTime;
    private final long endTime;
    private final boolean completed;
    private final long totalUrlCount;

    /** Urls finished with. Below totalUrlCount by exactly what is still queued or in flight. */
    private final long handledUrlCount;
    private final long existingUrlCount;
    private final long filteredUrlCount;
    private final long invalidUrlCount;
    private final long savedResourceCount;
    private final long indexedResourceCount;
    private final long vectoredResourceCount;
    private final long savedImageCount;
    private final long duplicatedContentCount;
    private final long abandonedUrlCount;
    private final long remainingUrlCount;
    private final long elapsedMillis;
    private final String elapsedTime;
    private final double progress;

    /**
     * The two limits, separately.
     *
     * <p>
     * A crawl ends on whichever arrives first, and one number that is the nearer of them cannot
     * say which -- a bar at 60% leaves the reader unable to tell 60% of the pages from 60% of the
     * time. {@code progress} stays as the nearer of the two so nothing that reads it breaks.
     */
    private final double sizeProgress;
    private final double timeProgress;

    /** Why the run stopped early, or null when it finished on its own terms. */
    private final String completionReason;

    /** Whether the run was cut short rather than reaching one of its own limits. */
    private final boolean interrupted;

    public CatalogSummary(Dashboard dashboard) {
        this(dashboard, dashboard.getCatalogDetails());
    }

    /**
     * The counters from the run and the versions from the catalog as it stands now. The two are
     * separate on purpose: a crawl carries the catalog it started with, so its own copy still says
     * the version it was about to write is unpublished long after it published it -- and a page
     * that polls until searchable catches up with version would poll for ever.
     */
    public CatalogSummary(Dashboard dashboard, CatalogDetails catalogDetails) {
        this.live = !dashboard.isCompleted();
        this.catalogId = catalogDetails != null ? catalogDetails.getId() : null;
        this.catalogName = catalogDetails != null ? catalogDetails.getName() : null;
        this.version = catalogDetails != null ? catalogDetails.getVersion() : null;
        this.searchVersion = catalogDetails != null ? catalogDetails.getSearchVersion() : null;
        this.startTime = dashboard.getStartTime();
        this.endTime = dashboard.getEndTime();
        this.completed = dashboard.isCompleted();
        this.totalUrlCount = dashboard.getTotalUrlCount();
        this.handledUrlCount = dashboard.getHandledUrlCount();
        this.existingUrlCount = dashboard.getExistingUrlCount();
        this.filteredUrlCount = dashboard.getFilteredUrlCount();
        this.invalidUrlCount = dashboard.getInvalidUrlCount();
        this.savedResourceCount = dashboard.getSavedResourceCount();
        this.indexedResourceCount = dashboard.getIndexedResourceCount();
        this.vectoredResourceCount = dashboard.getVectoredResourceCount();
        this.savedImageCount = dashboard.getSavedImageCount();
        this.duplicatedContentCount = dashboard.getDuplicatedContentCount();
        this.abandonedUrlCount = dashboard.getAbandonedUrlCount();
        this.remainingUrlCount = 0L;
        this.completionReason = dashboard.getCompletionReason();
        this.interrupted = dashboard.isInterrupted();
        this.elapsedMillis = dashboard.getElapsedTime();
        this.elapsedTime = format(dashboard.getElapsedTime());
        this.progress = dashboard.getProgress();
        this.sizeProgress = sizeRatio(catalogDetails, dashboard);
        this.timeProgress = timeRatio(catalogDetails, dashboard.getElapsedTime());
    }

    private static double sizeRatio(CatalogDetails catalogDetails, Dashboard dashboard) {
        if (catalogDetails == null || catalogDetails.getMaxFetchSize() == null
                || catalogDetails.getMaxFetchSize() <= 0) {
            return 0d;
        }
        return Math.min(1d, (double) catalogDetails.getCountingType().getValue(dashboard)
                / catalogDetails.getMaxFetchSize());
    }

    private static double timeRatio(CatalogDetails catalogDetails, long elapsedMillis) {
        if (catalogDetails == null || catalogDetails.getFetchDuration() == null
                || catalogDetails.getFetchDuration() <= 0) {
            return 0d;
        }
        return Math.min(1d,
                (double) elapsedMillis / (catalogDetails.getFetchDuration() * 60_000L));
    }

    /**
     * The counters as the finished version wrote them, read back out of its {@code settings.json}.
     *
     * <p>
     * That file records the run under a {@code lastRun} key and an interrupted run leaves a reason
     * beside the counters, which is how a finished-and-complete run is told from one that was cut
     * short. Keys an older file does not have read as zero rather than throwing: an early version's
     * settings are still worth showing.
     */
    @SuppressWarnings("unchecked")
    public CatalogSummary(CatalogDetails catalogDetails, Map<String, Object> settings) {
        Map<String, Object> lastRun = settings.get("lastRun") instanceof Map
                ? (Map<String, Object>) settings.get("lastRun")
                : Map.of();
        this.live = false;
        this.catalogId = catalogDetails.getId();
        this.catalogName = catalogDetails.getName();
        this.version = catalogDetails.getVersion();
        this.searchVersion = catalogDetails.getSearchVersion();
        this.startTime = number(lastRun, "startTime");
        this.elapsedMillis = number(lastRun, "elapsedMillis");
        this.endTime = this.startTime > 0 ? this.startTime + this.elapsedMillis : 0L;
        // an older settings.json wrote the reason under interruptionReason and only when the run
        // was cut short; a newer one records the reason either way and says which it was
        Object reason = lastRun.get("completionReason") != null
                ? lastRun.get("completionReason")
                : lastRun.get("interruptionReason");
        this.interrupted = Boolean.TRUE.equals(lastRun.get("interrupted"))
                || (lastRun.get("completionReason") == null
                        && lastRun.get("interruptionReason") != null);
        this.completed = !this.interrupted;
        this.totalUrlCount = number(lastRun, "totalUrlCount");
        this.handledUrlCount = number(lastRun, "handledUrlCount");
        this.existingUrlCount = number(lastRun, "existingUrlCount");
        this.filteredUrlCount = number(lastRun, "filteredUrlCount");
        this.invalidUrlCount = number(lastRun, "invalidUrlCount");
        this.savedResourceCount = number(lastRun, "savedResourceCount");
        this.indexedResourceCount = number(lastRun, "indexedResourceCount");
        this.vectoredResourceCount = number(lastRun, "vectoredResourceCount");
        this.savedImageCount = number(lastRun, "savedImageCount");
        this.duplicatedContentCount = number(lastRun, "duplicatedContentCount");
        this.abandonedUrlCount = number(lastRun, "abandonedUrlCount");
        this.remainingUrlCount = number(lastRun, "remainingUrlCount");
        this.completionReason = reason instanceof String text ? text : null;
        this.elapsedTime = format(this.elapsedMillis);
        this.progress = this.completed ? 1d : 0d;
        this.sizeProgress = this.progress;
        this.timeProgress = this.progress;
    }

    private static long number(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static String format(long millis) {
        return DurationFormatUtils.formatDuration(Math.max(0L, millis), "H'h' m'm' s's'");
    }

}
