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

package com.github.greenfinger.cluster.channel;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import com.chaconneai.openspreader.cluster.SelfRegisteringListener;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.chaconneai.spreader.loadbalance.LoadBalancer;
import com.chaconneai.spreader.event.BufferedGossipListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.cluster.Channels;
import com.github.greenfinger.cluster.ClusterProperties;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.component.state.GlobalStateManager;
import com.github.greenfinger.core.engine.CrawlFrontier;
import com.github.greenfinger.core.engine.CrawlRegistry;
import com.github.greenfinger.core.engine.CrawlTask;
import com.github.greenfinger.core.engine.WebCrawlerExecutionContext;
import lombok.extern.slf4j.Slf4j;

/**
 * The recursive call, as it crosses a process boundary.
 *
 * <p>
 * Sending is one unicast per url: the balancer picks a node, this one included, and a url that
 * lands here never touches the network -- spreader dispatches to the local listener directly. So
 * with one node the whole thing degenerates to a queue write, which is exactly what a crawl on a
 * laptop should cost.
 *
 * <p>
 * Receiving puts the url on this node's frontier and returns. The frontier, not a queue in memory:
 * a url accepted here is a url this node has promised to fetch, and a process that dies holding
 * promises has to be able to keep them when it comes back.
 *
 * <h2>Why this buffers</h2>
 * {@code onPayload} runs on spreader's dispatch thread, shared by every component. Writing to
 * RocksDB there would put the cluster's locks and cache replication behind this crawl's disk.
 *
 * <h2>A url can arrive twice</h2>
 * Delivery is at least once. The transport acknowledges and retries, and the receiver deduplicates
 * by sender and sequence, but the guarantee that survives all of that is still "at least", and a
 * duplicate does turn up.
 *
 * <p>
 * It costs one wasted fetch and nothing else, which is why it is left alone rather than defended
 * against here. Everything downstream is keyed by the url: the resource id is a name based uuid of
 * the url and the version, the file paths are derived from that id, and the vector point ids from
 * the same -- so the second pass writes the same row, the same files and the same points, over the
 * top of the first. The obvious defence, checking the url filter on the way in, cannot be used: a
 * refresh deliberately bypasses that filter, and applying it here would make a refresh fetch
 * nothing at all.
 *
 * <h2>Urls that arrive before this node is ready</h2>
 * A node is told a crawl has started and then has to open its half of it -- database, blob store,
 * output channels, and when the vector output is on, an embedding model that takes seconds to
 * load. The node that started the crawl does not wait for that; it begins fetching immediately.
 * So the first urls can land here before there is a frontier to put them on, and dropping them
 * would silently lose whole pages at the one moment the crawl is at its most branching.
 *
 * <p>
 * They are staged instead, and delivered as soon as the run appears. The staging area is bounded
 * in both size and age: a url still homeless after that is one whose crawl is never going to open
 * here, and holding it forever would be a leak rather than a rescue.
 *
 * <h2>What happens when the buffer fills</h2>
 * The base class drops, deliberately, because a full buffer means consumption is losing to
 * production and blocking the producer only spreads the problem. That answer is wrong here: a
 * dropped url is a page that will never be fetched and nothing will ever say so. So overflow is
 * absorbed instead -- the url goes straight onto this node's frontier, on the dispatch thread,
 * which is slower but loses nothing. Overload turns into "this node keeps the work" rather than
 * "this work disappears".
 * 
 * <h2>It registers itself</h2>
 * {@link SelfRegisteringListener} is not decoration. Without it the auto-registrar also puts this
 * listener on the <em>default</em> channel, and every message then arrives twice -- which shows up
 * not as an error but as a crawl that fetches every page a second time.
 *
 * @Description: CrawlTaskChannel
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class CrawlTaskChannel extends BufferedGossipListener
        implements SelfRegisteringListener {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** How often staged urls are offered to a run that may have opened since. */
    private static final long STAGING_SWEEP_MS = 200L;

    /** Past this a crawl is not opening here, and the urls belong to whoever still has them. */
    private static final long STAGING_MAX_AGE_MS = 120_000L;

    /** A cap so that a catalog nobody is running cannot fill the heap one url at a time. */
    private static final int STAGING_CAPACITY = 20_000;

    private final GossipCluster cluster;
    private final CrawlRegistry crawlRegistry;

    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong received = new AtomicLong();
    private final AtomicLong keptLocally = new AtomicLong();
    private final AtomicLong absorbed = new AtomicLong();
    private final AtomicLong orphaned = new AtomicLong();

    /** Tasks the frontier already had: the duplicates this exists to stop. */
    private final AtomicLong duplicated = new AtomicLong();
    private final AtomicLong staged = new AtomicLong();

    /** Urls whose crawl has not opened on this node yet, by catalog. */
    private final Map<String, Queue<StagedTask>> staging = new ConcurrentHashMap<>();

    private ScheduledExecutorService sweeper;

    public CrawlTaskChannel(GossipCluster cluster, CrawlRegistry crawlRegistry,
            ClusterProperties.Dispatch dispatch) {
        super("gf-crawl", dispatch.getBufferCapacity(), dispatch.getConsumers(),
                org.slf4j.LoggerFactory.getLogger(CrawlTaskChannel.class));
        this.cluster = cluster;
        this.crawlRegistry = crawlRegistry;
    }

    public void start() {
        cluster.addListener(Channels.CRAWL, this);
        startDispatch();
        sweeper = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "greenfinger-staging");
            thread.setDaemon(true);
            return thread;
        });
        sweeper.scheduleWithFixedDelay(this::sweepStaging, STAGING_SWEEP_MS, STAGING_SWEEP_MS,
                TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (sweeper != null) {
            sweeper.shutdownNow();
        }
        stopDispatch();
        cluster.removeListener(this);
        log.info("Crawl channel: {} sent, {} received, {} kept locally, {} staged, {} absorbed,"
                + " {} orphaned", sent.get(), received.get(), keptLocally.get(), staged.get(),
                absorbed.get(), orphaned.get());
    }

    /**
     * Hands one url to whichever node the balancer picks.
     *
     * <p>
     * No routing key. Round robin is what spreads a crawl evenly, and a key would defeat that:
     * hashing by url would be a lottery over an uneven keyspace, and hashing by host would pin a
     * whole site to one node. Politeness towards the site is handled where it belongs, in the
     * fetch interval, not by pretending the cluster is one machine.
     *
     * @return false when nothing took it, and the caller has to keep it.
     */
    public boolean dispatch(CrawlTask task) {
        byte[] payload;
        try {
            payload = OBJECT_MAPPER.writeValueAsBytes(task);
        } catch (Exception e) {
            log.error("Could not encode '{}': {}", task.getUrl(), e.getMessage());
            return false;
        }
        Node target;
        try {
            // includeSelf: this node is a worker like any other, and being picked costs it
            // nothing -- spreader dispatches locally without serialising or leaving the process.
            //
            // Routed by the url rather than round robin, which is what 1.x did too: every packet
            // it sent carried partitioner=hash over catalogId, refer, path and version. Round
            // robin sends the same url wherever the counter happens to point, so two nodes that
            // discover the same link within the replication window each fetch it, and neither
            // frontier can see that the other has it. Hashed, the same url always reaches the
            // same node, and that node's frontier refuses it the second time.
            //
            // Consistent rather than the plain modulo 1.x used: a node joining moves about 1/N of
            // the urls instead of nearly all of them, so the affinity survives the cluster
            // changing shape mid-crawl. Refer is deliberately not in the key, though it was in
            // 1.x's: the same url found on two different pages is precisely what this exists to
            // send to one place.
            target = cluster.unicastOn(Channels.CRAWL, null, routingKey(task), payload, true,
                    LoadBalancer.consistentHash());
        } catch (RuntimeException e) {
            log.warn("Could not dispatch '{}': {}", task.getUrl(), e.getMessage());
            return false;
        }
        if (target == null) {
            // one of: alone in the cluster, resting, or the send failed. Every one of them means
            // the same thing here -- this node does it
            keptLocally.incrementAndGet();
            return false;
        }
        sent.incrementAndGet();
        return true;
    }

    /**
     * What decides which node fetches a url. Everything in it is stable for the life of a task:
     * the catalog it belongs to, the version being written, and the url itself.
     */
    private static String routingKey(CrawlTask task) {
        return task.getCatalogId() + "|" + task.getVersion() + "|" + task.getUrl();
    }

    @Override
    protected void handlePayload(Node sender, byte[] content) {
        accept(content, false);
    }

    @Override
    protected void onOverflow(Node sender, byte[] content) {
        // never dropped: see the class comment
        absorbed.incrementAndGet();
        accept(content, true);
    }

    private void accept(byte[] content, boolean onDispatchThread) {
        CrawlTask task;
        try {
            task = OBJECT_MAPPER.readValue(content, CrawlTask.class);
        } catch (Exception e) {
            log.warn("Discarded an unreadable crawl task: {}", e.getMessage());
            return;
        }
        WebCrawlerExecutionContext context = crawlRegistry.getContext(task.getCatalogId());
        CrawlFrontier frontier = context != null ? context.getCrawlFrontier() : null;
        if (frontier == null) {
            // the run has not opened here yet, or it has already closed. The first is normal and
            // brief, the second means the url is genuinely homeless -- stage it and let the
            // sweeper tell the two apart
            stage(task);
            return;
        }
        queue(context, frontier, task, onDispatchThread);
    }

    /**
     * Hands a task to the frontier, and accounts for it when the frontier already had it.
     *
     * <p>
     * Every url that was dispatched has to be answered for exactly once, because the crawl ends
     * when nothing is still owed. A url the frontier turns away will never be reported handled by
     * anybody -- no worker will ever see it -- so it is reported here instead, and counted as one
     * that had been seen before rather than as work anyone did.
     */
    private void queue(WebCrawlerExecutionContext context, CrawlFrontier frontier, CrawlTask task,
            boolean onDispatchThread) {
        try {
            if (frontier.put(task)) {
                received.incrementAndGet();
                return;
            }
            duplicated.incrementAndGet();
            GlobalStateManager stateManager = context.getGlobalStateManager();
            stateManager.incrementCount(task.getTimestamp(), CountingType.HANDLED_URL_COUNT);
            stateManager.incrementCount(task.getTimestamp(), CountingType.EXISTING_URL_COUNT);
        } catch (Exception e) {
            log.error("Could not queue '{}'{}: {}", task.getUrl(),
                    onDispatchThread ? " (absorbed from a full buffer)" : "", e.getMessage());
        }
    }

    private void stage(CrawlTask task) {
        Queue<StagedTask> queue =
                staging.computeIfAbsent(task.getCatalogId(), k -> new ConcurrentLinkedQueue<>());
        if (queue.size() >= STAGING_CAPACITY) {
            orphaned.incrementAndGet();
            log.warn("Staging for catalog {} is full, dropping '{}'", task.getCatalogId(),
                    task.getUrl());
            return;
        }
        queue.add(new StagedTask(task, System.currentTimeMillis()));
        staged.incrementAndGet();
    }

    private void sweepStaging() {
        for (Map.Entry<String, Queue<StagedTask>> entry : staging.entrySet()) {
            Queue<StagedTask> queue = entry.getValue();
            if (queue.isEmpty()) {
                staging.remove(entry.getKey(), queue);
                continue;
            }
            WebCrawlerExecutionContext context = crawlRegistry.getContext(entry.getKey());
            CrawlFrontier frontier = context != null ? context.getCrawlFrontier() : null;
            long now = System.currentTimeMillis();
            List<CrawlTask> expired = new java.util.ArrayList<>();
            StagedTask head;
            while ((head = queue.poll()) != null) {
                if (frontier != null) {
                    queue(context, frontier, head.task(), false);
                } else if (now - head.stagedAt() > STAGING_MAX_AGE_MS) {
                    expired.add(head.task());
                } else {
                    // still young and still homeless: put it back and stop, the rest are newer
                    queue.add(head);
                    break;
                }
            }
            if (!expired.isEmpty()) {
                orphaned.addAndGet(expired.size());
                log.error("Catalog {} never opened on this node: {} url(s) given up on, first"
                        + " was '{}'. They are still on the frontier of whoever sent them.",
                        entry.getKey(), expired.size(), expired.get(0).getUrl());
            }
        }
    }

    /**
     * 
     * @Description: StagedTask
     * @Author: Fred Feng
     * @Date: 02/09/2026
     * @Version 2.0.0
     */
    private record StagedTask(CrawlTask task, long stagedAt) {
    }

    private CrawlFrontier frontierOf(CrawlTask task) {
        WebCrawlerExecutionContext context = crawlRegistry.getContext(task.getCatalogId());
        return context != null ? context.getCrawlFrontier() : null;
    }

    // ---- what to watch ----------------------------------------------------------------------

    /** Urls handed to another node. Near zero with peers present means the crawl is not spreading. */
    public long sentCount() {
        return sent.get();
    }

    /** How many arrivals were copies of something already queued. */
    public long duplicatedCount() {
        return duplicated.get();
    }

    public long receivedCount() {
        return received.get();
    }

    /** Urls nobody could be found for, so this node did them itself. */
    public long keptLocallyCount() {
        return keptLocally.get();
    }

    /** Urls that arrived while the buffer was full and went straight to the frontier. */
    public long absorbedCount() {
        return absorbed.get();
    }

    /** Urls for a catalog this node is not crawling. Non-zero means a page was lost. */
    public long orphanedCount() {
        return orphaned.get();
    }

    /** Urls that arrived before this node had opened the crawl and waited for it. */
    public long stagedCount() {
        return staged.get();
    }

    /** How many are waiting right now. Should be zero within a second of a crawl starting. */
    public int stagingDepth() {
        return staging.values().stream().mapToInt(Queue::size).sum();
    }

}
