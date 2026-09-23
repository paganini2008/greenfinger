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
import com.github.greenfinger.cluster.ClusterProperties;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

/**
 * One kind of write, copied to every other node.
 *
 * <p>
 * Used for the three stores that give each process its own copy of the data: a file-backed
 * database, the RocksDB dedup filters, and a blob directory on local disk. A shared server --
 * MySQL, MinIO, Elasticsearch -- needs none of this, and {@code StoreType} is what decides.
 *
 * <h2>Sending</h2>
 * Multicast, excluding self, because the write has already happened here -- that is what there is
 * to tell the others about. Batched, because these are small and frequent.
 *
 * <h2>Receiving: one consumer, and only ever additive</h2>
 * A single consumer thread, unlike the crawl channel: urls have no order between them but two
 * updates to the same row do, and two threads applying them concurrently could leave the older
 * one last.
 *
 * <p>
 * What arrives is applied as "make sure this is here", never as "overwrite whatever is there".
 * Delivery is at least once -- a frame whose acknowledgement was lost is sent again, and the
 * receiver's own deduplication has a window rather than a memory -- so an applier that is not
 * idempotent will eventually write the same thing twice. Checking first costs one lookup.
 *
 * <h2>Under-delivery is not silence any more</h2>
 * The transport acknowledges and retries on its own, and gives up after
 * {@code GossipConfig.payloadRetries()}. It says so by returning how many members took the frame
 * -- a number this class used to discard, which is what made a lost write look exactly like a
 * successful one. A frame that did not reach everybody is now held and sent again, a bounded
 * number of times, and what is still missing at the end of that is counted and logged at error
 * rather than forgotten.
 *
 * <p>
 * Re-sending goes to everybody, not to whoever was missing: the transport does not say which
 * member refused it, and every applier here is idempotent by construction, so the cost of telling
 * a node something it already knows is one lookup.
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
     *
     * <p>
     * The encoded bytes are kept rather than the entries: re-encoding would be the same work
     * twice, and a frame that is already framed is one that can go back out without touching the
     * batching rules it was built under.
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
        if (cluster.members().size() < 2) {
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
    private void send(byte[] frame, int entries, int attempts) {
        // read before the send: a member that leaves between the two would otherwise make a
        // complete delivery look short
        int expected = Math.max(0, cluster.members().size() - 1);
        // never to self: this node is where the write came from
        int delivered = cluster.multicastOn(channel, null, frame, false);
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
     * Offers every held frame once more.
     *
     * <p>
     * Drained into a list first: {@link #send} puts a frame that is still short straight back on
     * the queue, and reading the queue while writing to it would retry the same frame until it
     * either succeeded or ran out of attempts, inside one pass.
     */
    void retryPending() {
        if (retries.isEmpty() || cluster.members().size() < 2) {
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
