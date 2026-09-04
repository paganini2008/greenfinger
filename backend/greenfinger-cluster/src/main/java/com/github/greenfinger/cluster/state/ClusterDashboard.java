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
import java.text.DateFormat;
import java.util.Date;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.time.DurationFormatUtils;
import com.chaconneai.openspreader.cache.ProcessingCache;
import com.github.greenfinger.core.ManagedBeanLifeCycle;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.component.state.Dashboard;
import lombok.extern.slf4j.Slf4j;

/**
 * One crawl's counters, held in the cluster cache so that every node's Monitor page shows the
 * crawl rather than its own share of it.
 *
 * <h2>Reads are free, writes are not</h2>
 * Every process holds a full replica of the cache, so a read never leaves the JVM -- which is what
 * makes it reasonable for a page that refreshes every second to ask for eight counters. A write is
 * a round trip to whichever node holds the cluster port, measured in the low thousands per second
 * for the whole cluster, which a crawl would saturate on its own. That is why increments are
 * accumulated in {@link ClusterGlobalStateManager} and arrive here already summed.
 *
 * <h2>What stays local</h2>
 * The rolling average execution time. It is a property of this node's network and disk, and
 * averaging four nodes' averages would describe none of them. Everything a decision is made on --
 * the counters, the start time, the completion flag -- is shared.
 * 
 * @Description: ClusterDashboard
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class ClusterDashboard implements Dashboard, ManagedBeanLifeCycle {

    /** Long enough to outlive a crawl, short enough that abandoned runs do not accumulate. */
    private static final long TTL_HOURS = 24L;

    private final ProcessingCache cache;
    private final CatalogDetails catalogDetails;
    private final String prefix;
    /** Only the node the run was started on clears what the last run left behind. */
    private final boolean initiator;

    private final Map<CountingType, ElapsedWindow> elapsed = new EnumMap<>(CountingType.class);

    private volatile long lastModified = System.currentTimeMillis();

    ClusterDashboard(ProcessingCache cache, CatalogDetails catalogDetails, boolean initiator) {
        this.cache = cache;
        this.catalogDetails = catalogDetails;
        this.initiator = initiator;
        this.prefix = "gf:dash:" + catalogDetails.getId() + ":" + catalogDetails.getVersion() + ":";
    }

    /**
     * Every node in the crawl calls this, and what it does depends on which node.
     *
     * <p>
     * The node the run was started on clears everything first. These keys outlive the process that
     * wrote them -- they are in the shared cache, with a day's expiry -- so a second crawl of the
     * same catalog at the same version would otherwise inherit the first one's counters and, worse,
     * its completion flag: the run would be over before it began. 1.x reset the whole dashboard on
     * the initiating node for exactly this reason, and only on that node, because a node joining a
     * crawl in progress must not zero the counters it is about to add to.
     *
     * <p>
     * A joining node only sets the clock if nobody has: {@code setIfAbsent} is what stops the
     * second node restarting the elapsed time.
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        long now = System.currentTimeMillis();
        if (initiator) {
            reset();
        }
        setIfAbsent(key("startTime"), now);
        setIfAbsent(key("endTime"),
                now + TimeUnit.MINUTES.toMillis(catalogDetails.getFetchDuration()));
        lastModified = now;
    }

    /** Everything this crawl wrote last time it ran at this version. */
    private void reset() {
        long now = System.currentTimeMillis();
        set(key("startTime"), String.valueOf(now));
        set(key("endTime"), String.valueOf(
                now + TimeUnit.MINUTES.toMillis(catalogDetails.getFetchDuration())));
        // deleted rather than blanked: the reason is written with setIfAbsent, so a blank left
        // lying here would be "already written" and the real reason would never land
        delete(key("completed"));
        delete(key("completionReason"));
        delete(key("interrupted"));
        for (CountingType countingType : CountingType.values()) {
            delete(key(countingType.getRepr()));
            delete(byNodeKey(countingType));
        }
        elapsed.clear();
    }

    private void delete(String key) {
        try {
            cache.delete(key);
        } catch (RuntimeException e) {
            log.debug("Could not clear {}: {}", key, e.getMessage());
        }
    }

    private void set(String key, String value) {
        try {
            cache.set(key, value.getBytes(StandardCharsets.UTF_8), TTL_HOURS, TimeUnit.HOURS);
        } catch (RuntimeException e) {
            log.warn("Could not write {}: {}", key, e.getMessage());
        }
    }

    String key(String name) {
        return prefix + name;
    }

    /** The per node breakdown of one counter, as a hash keyed by node. */
    java.util.Map<String, Long> byNode(CountingType countingType) {
        java.util.Map<String, Long> result = new java.util.LinkedHashMap<>();
        try {
            cache.hgetAll(byNodeKey(countingType)).forEach((node, value) -> result.put(node,
                    Long.parseLong(new String(value, StandardCharsets.UTF_8))));
        } catch (RuntimeException e) {
            log.debug("Could not read the per node breakdown of {}: {}", countingType,
                    e.getMessage());
        }
        return result;
    }

    String byNodeKey(CountingType countingType) {
        return key(countingType.getRepr() + ":by-node");
    }

    void addForNode(CountingType countingType, String node, long delta) {
        try {
            cache.hincrby(byNodeKey(countingType), node, delta);
            cache.expire(byNodeKey(countingType), TTL_HOURS, TimeUnit.HOURS);
        } catch (RuntimeException e) {
            log.debug("Could not record {} for {}: {}", countingType, node, e.getMessage());
        }
    }

    long add(CountingType countingType, long delta) {
        lastModified = System.currentTimeMillis();
        if (delta == 0) {
            return read(countingType);
        }
        return cache.incr(key(countingType.getRepr()), delta);
    }

    void record(CountingType countingType, long elapsedMillis) {
        elapsed.computeIfAbsent(countingType, k -> new ElapsedWindow()).add(elapsedMillis);
    }

    private long read(CountingType countingType) {
        return readLong(key(countingType.getRepr()));
    }

    private long readLong(String key) {
        try {
            byte[] value = cache.get(key);
            return value == null ? 0L : Long.parseLong(new String(value, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            // a counter that cannot be read is worth a zero and a note, never an exception: this
            // is on the path of the page that would have shown the problem
            log.debug("Could not read {}: {}", key, e.getMessage());
            return 0L;
        }
    }

    private String readString(String key) {
        try {
            byte[] value = cache.get(key);
            return value == null ? null : new String(value, StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            log.debug("Could not read {}: {}", key, e.getMessage());
            return null;
        }
    }

    private void setIfAbsent(String key, long value) {
        setIfAbsent(key, String.valueOf(value));
    }

    private void setIfAbsent(String key, String value) {
        try {
            cache.setIfAbsent(key, value.getBytes(StandardCharsets.UTF_8), TTL_HOURS,
                    TimeUnit.HOURS);
        } catch (RuntimeException e) {
            log.warn("Could not initialise {}: {}", key, e.getMessage());
        }
    }

    void setCompleted(boolean completed, String reason, boolean interrupted) {
        // The reason before the flag, and only if nobody has written one: a node that reads the
        // flag reads the reason with it, and a crawl that stopped at a limit keeps the limit as
        // its reason rather than whatever wound it down afterwards.
        if (completed) {
            setIfAbsent(key("completionReason"), reason == null ? "" : reason);
            setIfAbsent(key("interrupted"), interrupted ? "1" : "0");
        }
        try {
            cache.set(key("completed"), String.valueOf(completed ? 1 : 0)
                    .getBytes(StandardCharsets.UTF_8), TTL_HOURS, TimeUnit.HOURS);
        } catch (RuntimeException e) {
            log.warn("Could not record completion: {}", e.getMessage());
        }
        lastModified = System.currentTimeMillis();
    }

    @Override
    public boolean isCompleted() {
        return readLong(key("completed")) == 1L;
    }

    @Override
    public String getCompletionReason() {
        String reason = readString(key("completionReason"));
        return reason == null || reason.isEmpty() ? null : reason;
    }

    @Override
    public boolean isInterrupted() {
        return readLong(key("interrupted")) == 1L;
    }

    @Override
    public long getTotalUrlCount() {
        return read(CountingType.URL_TOTAL_COUNT);
    }

    @Override
    public long getHandledUrlCount() {
        return read(CountingType.HANDLED_URL_COUNT);
    }

    @Override
    public long getInvalidUrlCount() {
        return read(CountingType.INVALID_URL_COUNT);
    }

    @Override
    public long getExistingUrlCount() {
        return read(CountingType.EXISTING_URL_COUNT);
    }

    @Override
    public long getFilteredUrlCount() {
        return read(CountingType.FILTERED_URL_COUNT);
    }

    @Override
    public long getSavedResourceCount() {
        return read(CountingType.SAVED_RESOURCE_COUNT);
    }

    @Override
    public long getIndexedResourceCount() {
        return read(CountingType.INDEXED_RESOURCE_COUNT);
    }

    @Override
    public long getSavedImageCount() {
        return read(CountingType.SAVED_IMAGE_COUNT);
    }

    @Override
    public long getDuplicatedContentCount() {
        return read(CountingType.DUPLICATED_CONTENT_COUNT);
    }

    @Override
    public long getStartTime() {
        return readLong(key("startTime"));
    }

    @Override
    public long getEndTime() {
        return readLong(key("endTime"));
    }

    @Override
    public long getElapsedTime() {
        return System.currentTimeMillis() - getStartTime();
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    @Override
    public double getAverageExecutionTime() {
        return elapsed.values().stream().mapToDouble(ElapsedWindow::average).average().orElse(0d);
    }

    @Override
    public CatalogDetails getCatalogDetails() {
        return catalogDetails;
    }

    @Override
    public String toString() {
        DateFormat format = DateFormat.getDateTimeInstance();
        return String.format(
                "Catalog: %s, Started: %s, Urls: %d dispatched / %d handled, Saved: %d page(s),"
                        + " %d image(s), Elapsed: %s, Completed: %s",
                catalogDetails.getName(), format.format(new Date(getStartTime())),
                getTotalUrlCount(), getHandledUrlCount(), getSavedResourceCount(),
                getSavedImageCount(),
                DurationFormatUtils.formatDuration(getElapsedTime(), "H'h' m'm' s's'"),
                isCompleted());
    }

    /**
     * A bounded window of recent durations, so the average does not drift with the whole history
     * of a long crawl.
     * 
     * @Description: ElapsedWindow
     * @Author: Fred Feng
     * @Date: 02/09/2026
     * @Version 2.0.0
     */
    static class ElapsedWindow {

        private static final int CAPACITY = 256;

        private final long[] values = new long[CAPACITY];
        private final AtomicLong cursor = new AtomicLong();

        void add(long value) {
            values[(int) (cursor.getAndIncrement() % CAPACITY)] = value;
        }

        double average() {
            long count = Math.min(cursor.get(), CAPACITY);
            if (count == 0) {
                return 0d;
            }
            long sum = 0;
            for (int i = 0; i < count; i++) {
                sum += values[i];
            }
            return (double) sum / count;
        }
    }

}
