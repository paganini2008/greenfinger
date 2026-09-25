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

import java.util.List;
import java.util.concurrent.TimeUnit;
import com.github.greenfinger.core.ManagedBeanLifeCycle;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.WebCrawlerComponent;
import java.util.Map;

/**
 * Owns the counters and the completion flag for one crawl. The standalone edition keeps them in
 * memory; the member list is retained so the distributed edition can plug in without reshaping the
 * interface.
 * 
 * @Description: GlobalStateManager
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public interface GlobalStateManager extends WebCrawlerComponent, ManagedBeanLifeCycle {

    void addMember(String instanceId);

    void removeMember(String instanceId);

    List<String> getMembers();

    boolean isCompleted();

    /**
     * Ends the crawl, everywhere. The flag and the reason live where the counters live, so the
     * node that notices first writes and the rest read the same answer. The first reason wins: a
     * crawl that stopped at a limit should report the limit, not whatever wound it down.
     *
     * @param reason in the words of whoever ended it; it is what the run report shows.
     * @param interrupted false when a {@code CompletionChecker} decided this -- the crawl reached
     *        its own limit and what it saved is whole, so the search version is published. True
     *        for an intervention: Ctrl+C, the {@code interrupt} command, or the watchdog finding
     *        the counters frozen. Those publish nothing.
     */
    void setCompleted(boolean completed, String reason, boolean interrupted);

    /** A legal completion: one of the two {@code CompletionChecker}s reached its limit. */
    default void setCompleted(boolean completed, String reason) {
        setCompleted(completed, reason, false);
    }

    /** An intervention. Ends the crawl and leaves the previous search version standing. */
    default void interrupt(String reason) {
        setCompleted(true, reason, true);
    }

    /**
     * Overwrites a reason already written, for the one fact that outranks every other: the crawl
     * read nothing. A site behind a challenge drains the frontier with the counters agreeing, so
     * the watchdog correctly reports a site that ran out of urls -- and an empty version is
     * published over a good one. What was read wins over how it stopped.
     *
     * @param reason replaces whatever is recorded, and the crawl becomes an intervention.
     */
    /**
     * Record a fetch that came back with nothing. Shared rather than per node: a site refusing
     * this crawler refuses all of it, and per-node counting would need every node to reach the
     * limit before anything said so.
     */
    void noteFetchFailure(String reason);

    /** A page arrived, so whatever run of failures was building is over. */
    void noteFetchSuccess();

    void overrideAsUnproductive(String reason);

    default long incrementCount(long startTime, CountingType countingType) {
        return incrementCount(startTime, countingType, 1);
    }

    long incrementCount(long startTime, CountingType countingType, int delta);

    boolean isTimeout(long delay, TimeUnit timeUnit);

    /**
     * Makes every increment taken so far visible to whoever reads the dashboard.
     *
     * <p>
     * A no-op when the counters are this process's own, and the point of the interface when they
     * are not: a cluster batches increments to keep them off the network, so a report rendered the
     * instant a crawl ends would otherwise show numbers up to one flush interval stale. Which is
     * exactly the moment the numbers are read.
     */
    default void flush() {}

    /**
     * What each node did, by counter: {@code {"handled": {"a1b2": 18, "c3d4": 21}, ...}}.
     *
     * <p>
     * Empty for a single process, where the answer would be the totals repeated. Across a cluster
     * it is the part of a run report that cannot be reconstructed afterwards -- the totals say the
     * crawl saved 44 pages, and only this says whether one node did forty of them because the
     * other two were unreachable.
     */
    default Map<String, Map<String, Long>> perNodeCounters() {
        return Map.of();
    }

    Dashboard getDashboard();

    CatalogDetails getCatalogDetails();

}
