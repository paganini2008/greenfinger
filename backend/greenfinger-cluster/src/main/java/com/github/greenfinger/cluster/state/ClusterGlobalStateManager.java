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

package com.github.greenfinger.cluster.state;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import com.chaconneai.openspreader.cache.ProcessingCache;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.component.state.Dashboard;
import com.github.greenfinger.core.component.state.GlobalStateManager;
import lombok.extern.slf4j.Slf4j;
import java.util.LinkedHashMap;

/**
 * The counters for a crawl several processes share.
 *
 * <p>
 * Increments accumulate locally and one scheduled write carries the delta -- {@code incr(key, n)}
 * rather than n calls. A remote cache write is a round trip and the cluster's ceiling is in the low
 * thousands a second, shared by every component, so sending each increment would make a progress
 * display the bottleneck of the crawl. The dashboard is at most one flush behind.
 *
 * <p>
 * That batching is also why completion works: child urls are dispatched before their page is
 * reported handled, and both land in one batch, so no observer sees a page finished while the urls
 * it found are unaccounted for.
 * 
 * @Description: ClusterGlobalStateManager
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class ClusterGlobalStateManager implements GlobalStateManager {

    private static final long MEMBER_TTL_HOURS = 24L;

    private final ProcessingCache cache;
    private final CatalogDetails catalogDetails;
    private final ClusterDashboard dashboard;
    private final long flushIntervalMs;
    private final String membersKey;

    private final Map<CountingType, AtomicLong> pending = new EnumMap<>(CountingType.class);

    private ScheduledExecutorService flusher;

    /** What the shared counters last read as, and when they last read differently. */
    private volatile long lastFingerprint = Long.MIN_VALUE;
    private volatile long lastChangedAt = System.currentTimeMillis();

    private final String nodeId;

    public ClusterGlobalStateManager(ProcessingCache cache, CatalogDetails catalogDetails,
            String nodeId, long flushIntervalMs, boolean initiator) {
        this.nodeId = nodeId;
        this.cache = cache;
        this.catalogDetails = catalogDetails;
        this.dashboard = new ClusterDashboard(cache, catalogDetails, initiator);
        this.flushIntervalMs = flushIntervalMs;
        this.membersKey = "gf:members:" + catalogDetails.getId();
        for (CountingType countingType : CountingType.values()) {
            pending.put(countingType, new AtomicLong());
        }
    }

    @Override
    public void noteFetchFailure(String reason) {
        dashboard.noteFetchFailure(reason);
    }

    @Override
    public void noteFetchSuccess() {
        dashboard.noteFetchSuccess();
    }

    @Override
    public String getName() {
        return "cluster";
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        dashboard.afterPropertiesSet();
        flusher = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "greenfinger-counters");
            thread.setDaemon(true);
            return thread;
        });
        flusher.scheduleWithFixedDelay(this::flushQuietly, flushIntervalMs, flushIntervalMs,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() throws Exception {
        if (flusher != null) {
            flusher.shutdownNow();
        }
        // the last few increments matter more than any of the others: they are the ones that
        // decide whether the two url counters meet
        flushQuietly();
    }

    private void flushQuietly() {
        try {
            flush();
        } catch (RuntimeException e) {
            log.warn("Could not publish counters: {}", e.getMessage());
        }
    }

    /**
     * Dispatched before handled, always. See the class comment: the pair is read as an invariant,
     * and writing them the other way round would let a reader see the invariant broken.
     */
    @Override
    public void flush() {
        drain(CountingType.TOTAL_URL_COUNT);
        for (CountingType countingType : CountingType.values()) {
            if (countingType != CountingType.TOTAL_URL_COUNT
                    && countingType != CountingType.HANDLED_URL_COUNT) {
                drain(countingType);
            }
        }
        drain(CountingType.HANDLED_URL_COUNT);
    }

    private void drain(CountingType countingType) {
        long delta = pending.get(countingType).getAndSet(0);
        if (delta != 0) {
            dashboard.add(countingType, delta);
            // and again against this node, which is the only way a report can say who did what.
            // Two writes rather than one derived from the other: the plain total is read once a
            // second by the completion check and has to stay a single cheap lookup
            dashboard.addForNode(countingType, nodeId, delta);
        }
    }

    @Override
    public Map<String, Map<String, Long>> perNodeCounters() {
        Map<String, Map<String, Long>> byCounter =
                new LinkedHashMap<>();
        for (CountingType countingType : CountingType.values()) {
            Map<String, Long> nodes = dashboard.byNode(countingType);
            if (!nodes.isEmpty()) {
                byCounter.put(countingType.getRepr(), nodes);
            }
        }
        return byCounter;
    }

    @Override
    public long incrementCount(long startTime, CountingType countingType, int delta) {
        long value = pending.get(countingType).addAndGet(delta);
        if (startTime > 0) {
            dashboard.record(countingType, System.currentTimeMillis() - startTime);
        }
        // what this node has not published yet plus what the cluster has: an approximation, and
        // the only caller that reads it is a log line
        return value + countingType.getValue(dashboard);
    }

    @Override
    public void addMember(String instanceId) {
        try {
            cache.hset(membersKey, instanceId,
                    String.valueOf(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
            cache.expire(membersKey, MEMBER_TTL_HOURS, TimeUnit.HOURS);
        } catch (RuntimeException e) {
            log.warn("Could not register {} on catalog {}: {}", instanceId,
                    catalogDetails.getName(), e.getMessage());
        }
    }

    @Override
    public void removeMember(String instanceId) {
        try {
            cache.hdel(membersKey, instanceId);
        } catch (RuntimeException e) {
            log.warn("Could not deregister {}: {}", instanceId, e.getMessage());
        }
    }

    @Override
    public List<String> getMembers() {
        try {
            return List.copyOf(cache.hgetAll(membersKey).keySet());
        } catch (RuntimeException e) {
            log.debug("Could not read the member list: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public boolean isCompleted() {
        return dashboard.isCompleted();
    }

    @Override
    public void setCompleted(boolean completed, String reason, boolean interrupted) {
        // this node's own increments first, or the counters the reason is about would be up to one
        // flush behind the flag that ends the crawl
        flushQuietly();
        dashboard.setCompleted(completed, reason, interrupted);
    }

    @Override
    public void overrideAsUnproductive(String reason) {
        flushQuietly();
        dashboard.overrideAsUnproductive(reason);
    }

    /**
     * Whether the crawl has stopped moving across the cluster. Timing a node's own writes would be
     * wrong -- a node waiting for work writes nothing while its peer fetches happily -- so this
     * remembers when the shared counters last changed.
     */
    @Override
    public boolean isTimeout(long delay, TimeUnit timeUnit) {
        long fingerprint =
                dashboard.getTotalUrlCount() * 31 + dashboard.getHandledUrlCount();
        long now = System.currentTimeMillis();
        if (fingerprint != lastFingerprint) {
            lastFingerprint = fingerprint;
            lastChangedAt = now;
            return false;
        }
        return now - lastChangedAt > timeUnit.toMillis(delay);
    }

    @Override
    public Dashboard getDashboard() {
        return dashboard;
    }

    @Override
    public CatalogDetails getCatalogDetails() {
        return catalogDetails;
    }

}
