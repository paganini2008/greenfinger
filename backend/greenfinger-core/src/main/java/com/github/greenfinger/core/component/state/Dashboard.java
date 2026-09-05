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
 * The live counters behind the Monitor view: url outcomes, resources produced, and the clock.
 * 
 * @Description: Dashboard
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public interface Dashboard {

    /** Urls dispatched: counted when a url is accepted and handed to a frontier, local or not. */
    long getTotalUrlCount();

    /**
     * Urls finished with, successfully or not. Never greater than {@link #getTotalUrlCount()},
     * and equal to it exactly when no url is queued or in flight anywhere in the cluster.
     */
    long getHandledUrlCount();

    long getInvalidUrlCount();

    long getExistingUrlCount();

    long getFilteredUrlCount();

    long getSavedResourceCount();

    long getIndexedResourceCount();

    /** Pages handed to the vector store. Zero whenever the vector output is off. */
    long getVectoredResourceCount();

    long getSavedImageCount();

    long getDuplicatedContentCount();

    /** Urls dropped because a limit fired while they were in flight. See the counting type. */
    long getAbandonedUrlCount();

    long getStartTime();

    long getEndTime();

    long getElapsedTime();

    long getLastModified();

    boolean isCompleted();

    /**
     * Why this crawl ended, in the words of whoever ended it, or null while it runs.
     *
     * <p>
     * Written once -- the first writer wins -- because a crawl that reached {@code maxFetchSize}
     * and is then told to stop should report the limit that ended it rather than the wind-down
     * that followed.
     */
    default String getCompletionReason() {
        return null;
    }

    /**
     * Whether the end was a legal completion or an intervention.
     *
     * <p>
     * A completion is what the two {@code CompletionChecker}s decide: the catalog's
     * {@code maxFetchSize} or its {@code fetchDuration} was reached, and what was crawled is
     * whole on the crawl's own terms. An intervention is everything else -- Ctrl+C, the
     * {@code interrupt} command, or the watchdog finding the counters frozen -- and it is the
     * reason the search version is not published: half a version is worse than the previous one.
     */
    default boolean isInterrupted() {
        return false;
    }

    double getAverageExecutionTime();

    CatalogDetails getCatalogDetails();

    /**
     * Fraction of the configured limit already consumed, in the range 0..1, taking whichever of
     * fetch size and fetch duration has advanced further. Drives the progress bar.
     */
    default double getProgress() {
        CatalogDetails catalogDetails = getCatalogDetails();
        if (catalogDetails == null) {
            return 0d;
        }
        double sizeRatio = 0d;
        Integer maxFetchSize = catalogDetails.getMaxFetchSize();
        if (maxFetchSize != null && maxFetchSize > 0) {
            sizeRatio = (double) catalogDetails.getCountingType().getValue(this) / maxFetchSize;
        }
        double timeRatio = 0d;
        Long fetchDuration = catalogDetails.getFetchDuration();
        if (fetchDuration != null && fetchDuration > 0) {
            timeRatio = (double) getElapsedTime() / (fetchDuration * 60_000L);
        }
        return Math.min(1d, Math.max(sizeRatio, timeRatio));
    }

    /**
     * Milliseconds left before the fetch duration runs out, never negative.
     */
    default long getRemainingTime() {
        long remaining = getEndTime() - System.currentTimeMillis();
        return Math.max(0L, remaining);
    }

}
