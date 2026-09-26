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

package com.github.greenfinger.cluster.replication;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import com.chaconneai.openspreader.cluster.SelfRegisteringListener;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.chaconneai.spreader.event.BufferedGossipListener;
import com.github.greenfinger.cluster.Channels;
import com.github.greenfinger.cluster.ClusterProperties;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

/**
 * One kind of write, copied to every other node.
 *
 * <p>
 * For the three stores that keep a copy per process -- a file database, the RocksDB filters, a
 * local blob directory. A shared server needs none of it, and {@code StoreType} decides.
 *
 * <p>
 * Sending is a batched multicast excluding self; receiving is a single consumer thread, because two
 * updates to one row have an order two threads could invert. What arrives is applied as "make sure
 * this is here", never as an overwrite, since delivery is at least once -- which is also why a
 * frame that missed a member is re-offered to everybody, and logged at error if it still misses.
 * 
 * @Description: ReplicationChannel
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class ReplicationChannel extends BufferedGossipListener
        implements SelfRegisteringListener, ReplicationSink {

    /** Comfortably inside one frame, and small enough that a batch never waits long to fill. */
    private static final int MAX_BATCH_BYTES = 1 << 20;

    private final GossipCluster cluster;
    private final String channel;
    private final Consumer<ReplicationBatch.Entry> applier;
    private final int batchSize;
    private final long flushIntervalMs;
    private final long retryIntervalMs;
    private final int maxRetries;
    private final int maxPending;

    private final BlockingQueue<ReplicationBatch.Entry> outbox = new LinkedBlockingQueue<>();

    /** Frames that did not reach every member, waiting to be offered again. */
    private final BlockingQueue<Pending> retries = new LinkedBlockingQueue<>();

    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong applied = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong underDelivered = new AtomicLong();
    private final AtomicLong lost = new AtomicLong();

    private ScheduledExecutorService flusher;

    /**
     * A frame the transport could not place everywhere, and how many times it has been offered.
     * The encoded bytes are kept rather than the entries, so it goes back out untouched.
     */
    private record Pending(byte[] frame, int entries, int attempts) {}

    public ReplicationChannel(GossipCluster cluster, String channel, String name,
            ClusterProperties.Replication config, Consumer<ReplicationBatch.Entry> applier) {
        // one consumer: two updates to the same row have an order, and two threads would lose it
        super(name, config.getBufferCapacity(), 1,
                LoggerFactory.getLogger("greenfinger.replication." + name));
        this.cluster = cluster;
        this.channel = channel;
        this.applier = applier;
        this.batchSize = config.getBatchSize();
        this.flushIntervalMs = config.getFlushIntervalMs();
        this.retryIntervalMs = config.getRetryIntervalMs();
        this.maxRetries = config.getMaxRetries();
        this.maxPending = config.getMaxPendingFrames();
    }

    public void start() {
        cluster.addListener(channel, this);
        startDispatch();
        flusher = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "greenfinger-repl-" + bufferName());
            thread.setDaemon(true);
            return thread;
        });
        flusher.scheduleWithFixedDelay(this::flushQuietly, flushIntervalMs, flushIntervalMs,
                TimeUnit.MILLISECONDS);
        // spaced further apart than the flush: a member that did not take the frame is usually
        // busy or restarting, and offering it again immediately asks the same question of the
        // same node before anything can have changed
        flusher.scheduleWithFixedDelay(this::retryQuietly, retryIntervalMs, retryIntervalMs,
                TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (flusher != null) {
            flusher.shutdownNow();
        }
        // whatever is still queued describes writes that already happened here, so the others
        // would be missing them for good
        flushQuietly();
        retryQuietly();
        if (!retries.isEmpty()) {
            lost.addAndGet(retries.stream().mapToLong(Pending::entries).sum());
            log.error("'{}' is stopping with {} frame(s) still undelivered. The nodes that missed"
                    + " them will not be told again.", bufferName(), retries.size());
        }
        stopDispatch();
        cluster.removeListener(this);
    }

    /**
     * Queues one write for the other nodes. Never blocks the caller: replication is not on the
     * critical path of the write it describes.
     */
    @Override
    public void replicate(ReplicationBatch.Entry entry) {
        if (crawlers() < 2) {
            // alone: nobody to tell, and queuing would only grow
            return;
        }
        outbox.add(entry);
        if (outbox.size() >= batchSize) {
            flushQuietly();
        }
    }

    private void flushQuietly() {
        try {
            flush();
        } catch (RuntimeException e) {
            log.warn("Could not replicate: {}", e.getMessage());
        }
    }

    void flush() {
        while (!outbox.isEmpty()) {
            List<ReplicationBatch.Entry> batch = new ArrayList<>(batchSize);
            int bytes = 0;
            while (batch.size() < batchSize && bytes < MAX_BATCH_BYTES) {
                ReplicationBatch.Entry entry = outbox.poll();
                if (entry == null) {
                    break;
                }
                batch.add(entry);
                bytes += ReplicationBatch.sizeOf(entry);
            }
            if (batch.isEmpty()) {
                return;
            }
            send(new ReplicationBatch(batch).encode(), batch.size(), 0);
        }
    }

    /**
     * One frame, to everybody but this node, with the delivery count actually read.
     *
     * @param attempts how many times this frame has already been offered, so a frame that keeps
     *                 falling short is given up on rather than retried for ever
     */
    /**
     * How many crawler nodes there are, which is not how many members there are: a terminal is a
     * member with nowhere to put a copy, so counting it would have every frame reported as having
     * missed somebody and retried until it was given up on.
     */
    private int crawlers() {
        return cluster.membersOf(Channels.crawlers(cluster)).size();
    }

    private void send(byte[] frame, int entries, int attempts) {
        // read before the send: a member that leaves between the two would otherwise make a
        // complete delivery look short
        int expected = Math.max(0, crawlers() - 1);
        // never to self: this node is where the write came from
        int delivered = cluster.multicastOn(channel, Channels.crawlers(cluster), frame, false);
        sent.addAndGet(entries);
        if (delivered >= expected) {
            return;
        }
        underDelivered.addAndGet((long) entries * (expected - delivered));
        if (attempts >= maxRetries) {
            lost.addAndGet(entries);
            log.error("{} write(s) on '{}' reached {} of {} node(s) after {} attempt(s) and are"
                    + " being dropped. Those nodes' copies are now behind.", entries,
                    bufferName(), delivered, expected, attempts + 1);
            return;
        }
        if (retries.size() >= maxPending) {
            lost.addAndGet(entries);
            log.error("The retry queue of '{}' is full at {} frame(s); {} write(s) that reached"
                    + " {} of {} node(s) are being dropped.", bufferName(), maxPending, entries,
                    delivered, expected);
            return;
        }
        retries.add(new Pending(frame, entries, attempts + 1));
    }

    private void retryQuietly() {
        try {
            retryPending();
        } catch (RuntimeException e) {
            log.warn("Could not retry replication: {}", e.getMessage());
        }
    }

    /**
     * Offers every held frame once more. Drained into a list first: {@link #send} puts a short
     * frame straight back, so reading the queue while writing it would retry one frame to
     * exhaustion inside a single pass.
     */
    void retryPending() {
        if (retries.isEmpty() || crawlers() < 2) {
            return;
        }
        List<Pending> due = new ArrayList<>(retries.size());
        retries.drainTo(due);
        for (Pending pending : due) {
            send(pending.frame(), pending.entries(), pending.attempts());
        }
    }

    @Override
    protected void handlePayload(Node sender, byte[] content) {
        ReplicationBatch batch = ReplicationBatch.decode(content);
        if (batch == null) {
            log.warn("Discarded a malformed replication frame from {}", sender.label());
            return;
        }
        for (ReplicationBatch.Entry entry : batch.entries()) {
            try {
                applier.accept(entry);
                applied.incrementAndGet();
            } catch (RuntimeException e) {
                failed.incrementAndGet();
                // one bad row must not stop the rest of the batch
                log.warn("Could not apply {} '{}': {}", entry.op(), entry.key(), e.getMessage());
            }
        }
    }

    @Override
    protected void onOverflow(Node sender, byte[] content) {
        // Unlike a dropped url, a dropped replication frame is silent divergence: this node's
        // copy is simply missing rows nobody will ever mention again. Loud, and counted.
        failed.incrementAndGet();
        log.error("Replication buffer '{}' is full, {} frame(s) lost from {}. This node's copy is"
                + " now behind and a rebuild is the only way back.", bufferName(), 1,
                sender.label());
    }

    public long sentCount() {
        return sent.get();
    }

    public long appliedCount() {
        return applied.get();
    }

    /** Writes that could not be applied. Non-zero means this node's copy has diverged. */
    public long failedCount() {
        return failed.get();
    }

    /**
     * Deliveries that fell short, counted per write per node rather than per frame: one frame of
     * sixty rows that missed two of three nodes is a hundred and twenty writes somebody has not
     * got, and that is the number worth seeing.
     */
    public long underDeliveredCount() {
        return underDelivered.get();
    }

    /**
     * Writes given up on. Unlike {@link #underDeliveredCount()}, which a retry can still put
     * right, this one only goes up when a copy elsewhere is permanently behind.
     */
    public long lostCount() {
        return lost.get();
    }

    /** Frames waiting to be offered again. */
    public int pendingCount() {
        return retries.size();
    }

}
