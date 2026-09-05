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

package com.github.greenfinger.core.engine;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.rocksdb.RocksIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.core.component.dedup.RocksDbStore;
import lombok.extern.slf4j.Slf4j;

/**
 * A frontier stored as an ordered RocksDB key range.
 *
 * <p>
 * Tasks are written under a zero-padded sequence key, so iterating the keyspace yields them in
 * insertion order -- breadth-first, the order a crawl wants. A read cursor only ever moves forward
 * within a run, and completion deletes the key. Anything still present when the process exits was
 * either never taken or taken and interrupted; on the next open both are recovered, which is
 * exactly the set that should be retried.
 * 
 * @Description: RocksDbCrawlFrontier
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class RocksDbCrawlFrontier implements CrawlFrontier {

    private static final String KEY_PREFIX = "f:";
    private static final String KEY_FORMAT = KEY_PREFIX + "%016x";

    /**
     * The second key space in the same store: one key per url that has been queued.
     *
     * <p>
     * Sorts after {@code f:} in RocksDB's byte order, so the iterations over the queue walk off
     * the end of their own prefix and stop, exactly as they did when this was the only key space.
     */
    private static final String SEEN_PREFIX = "u:";

    private static final byte[] PRESENT = new byte[] {1};

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RocksDbStore store;
    private final AtomicLong writeSequence = new AtomicLong(0);
    private final AtomicLong outstanding = new AtomicLong(0);
    private long readCursor = 0L;
    private long recoveredCount = 0L;

    public RocksDbCrawlFrontier(String directory) {
        this.store = new RocksDbStore(directory);
    }

    @Override
    public String getName() {
        return "rocksdb-frontier";
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        store.afterPropertiesSet();
        recover();
    }

    /**
     * Counts what a previous run left behind and positions the write sequence past it, so recovered
     * tasks are handed out before anything newly discovered.
     *
     * <p>
     * The urls the last run remembered queuing are forgotten here, and only the ones still in the
     * queue are remembered again. What {@code u:} answers is "has this run queued it", not "has
     * this version ever held it" -- a refresh writes the same version into the same store and
     * re-queues the very pages the run before it fetched, which is the whole point of a refresh.
     * Kept across the restart, that memory would refuse every one of them and a refresh would
     * crawl nothing.
     */
    private void recover() throws Exception {
        forgetQueuedUrls();
        long count = 0;
        long maxSequence = -1L;
        try (RocksIterator iterator = store.newIterator()) {
            for (iterator.seek(KEY_PREFIX.getBytes(StandardCharsets.UTF_8)); iterator
                    .isValid(); iterator.next()) {
                String key = new String(iterator.key(), StandardCharsets.UTF_8);
                if (!key.startsWith(KEY_PREFIX)) {
                    break;
                }
                count++;
                maxSequence = Math.max(maxSequence, parseSequence(key));
                // still queued, so still remembered: a redelivery of one of these after the
                // restart is the same duplicate it would have been before it
                store.putIfAbsent(seenKey(objectMapper.readValue(iterator.value(), CrawlTask.class)),
                        PRESENT);
            }
        }
        recoveredCount = count;
        outstanding.set(count);
        writeSequence.set(maxSequence + 1);
        readCursor = 0L;
        if (count > 0 && log.isInfoEnabled()) {
            log.info("Recovered {} outstanding url(s) from the previous run.", count);
        }
    }

    /**
     * Queues a task, unless this url has been queued before.
     *
     * <p>
     * Delivery through the cluster is at-least-once, so the same task can arrive twice, and every
     * path into the queue meets here -- a task from a peer, one the dispatcher kept locally, a
     * sitemap seed, one recovered from the last run. Queuing it again costs a second fetch, a
     * second parse and a second request the site did not need to serve, and is then refused by the
     * database's unique constraint, which is where this used to be discovered.
     *
     * <p>
     * Not the same question as {@code ExistingUrlPathFilter}, which cannot answer it: that one is
     * set before the task is dispatched, so by the time a duplicate arrives it says "seen" to the
     * duplicate and to the original alike. This asks whether the queue has already taken it.
     *
     * <p>
     * The store is scoped to this catalog and version, so the key space is this run's and goes
     * when it does. It is a persisted key rather than a set in memory for the same reason the
     * queue is: a crawl of a large site holds more urls than a heap wants to.
     */
    @Override
    public boolean put(CrawlTask task) throws Exception {
        if (store.putIfAbsent(seenKey(task), PRESENT)) {
            if (log.isDebugEnabled()) {
                log.debug("Already queued, not queued again: {}", task.getUrl());
            }
            return false;
        }
        long sequence = writeSequence.getAndIncrement();
        store.put(key(sequence), objectMapper.writeValueAsBytes(task));
        outstanding.incrementAndGet();
        return true;
    }

    @Override
    public synchronized CrawlTask poll() throws Exception {
        try (RocksIterator iterator = store.newIterator()) {
            iterator.seek(key(readCursor).getBytes(StandardCharsets.UTF_8));
            if (!iterator.isValid()) {
                return null;
            }
            String key = new String(iterator.key(), StandardCharsets.UTF_8);
            if (!key.startsWith(KEY_PREFIX)) {
                return null;
            }
            readCursor = parseSequence(key) + 1;
            CrawlTask task = objectMapper.readValue(iterator.value(), CrawlTask.class);
            task.setSequence(parseSequence(key));
            return task;
        }
    }

    @Override
    public void complete(CrawlTask task) throws Exception {
        store.delete(key(task.getSequence()).getBytes(StandardCharsets.UTF_8));
        outstanding.decrementAndGet();
    }

    @Override
    public long remaining() {
        return Math.max(0L, outstanding.get());
    }

    @Override
    public long recoveredCount() {
        return recoveredCount;
    }

    /**
     * Removes everything under {@code u:}, leaving the queue itself alone.
     *
     * <p>
     * Collected first and deleted afterwards rather than deleted while iterating: the iterator is
     * a snapshot and would tolerate it, but a loop that mutates what it is walking is a loop
     * somebody has to reason about.
     */
    private void forgetQueuedUrls() throws Exception {
        List<byte[]> keys = new ArrayList<>();
        try (RocksIterator iterator = store.newIterator()) {
            for (iterator.seek(SEEN_PREFIX.getBytes(StandardCharsets.UTF_8)); iterator
                    .isValid(); iterator.next()) {
                String key = new String(iterator.key(), StandardCharsets.UTF_8);
                if (!key.startsWith(SEEN_PREFIX)) {
                    break;
                }
                keys.add(iterator.key());
            }
        }
        for (byte[] key : keys) {
            store.delete(key);
        }
    }

    /** Version included so the key says which run it belongs to, as every other key here does. */
    private String seenKey(CrawlTask task) {
        return SEEN_PREFIX + task.getVersion() + "|" + task.getUrl();
    }

    private String key(long sequence) {
        return String.format(KEY_FORMAT, sequence);
    }

    private long parseSequence(String key) {
        return Long.parseLong(key.substring(KEY_PREFIX.length()), 16);
    }

    @Override
    public void clean() throws Exception {
        store.clean();
    }

    @Override
    public void destroy() throws Exception {
        store.destroy();
    }

}
