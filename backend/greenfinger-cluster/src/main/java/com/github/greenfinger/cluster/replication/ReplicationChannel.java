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

    private final BlockingQueue<ReplicationBatch.Entry> outbox = new LinkedBlockingQueue<>();

    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong applied = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    private ScheduledExecutorService flusher;

    public ReplicationChannel(GossipCluster cluster, String channel, String name,
            ClusterProperties.Replication config, Consumer<ReplicationBatch.Entry> applier) {
        // one consumer: two updates to the same row have an order, and two threads would lose it
        super(name, config.getBufferCapacity(), 1,
                org.slf4j.LoggerFactory.getLogger("greenfinger.replication." + name));
        this.cluster = cluster;
        this.channel = channel;
        this.applier = applier;
        this.batchSize = config.getBatchSize();
        this.flushIntervalMs = config.getFlushIntervalMs();
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
    }

    public void stop() {
        if (flusher != null) {
            flusher.shutdownNow();
        }
        // whatever is still queued describes writes that already happened here, so the others
        // would be missing them for good
        flushQuietly();
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
            // never to self: this node is where the write came from
            cluster.multicastOn(channel, null, new ReplicationBatch(batch).encode(), false);
            sent.addAndGet(batch.size());
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

}
