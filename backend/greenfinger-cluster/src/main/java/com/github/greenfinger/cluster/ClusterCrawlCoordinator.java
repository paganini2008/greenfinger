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

package com.github.greenfinger.cluster;

import java.util.concurrent.atomic.AtomicLong;
import com.github.greenfinger.cluster.channel.CrawlTaskChannel;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.component.state.GlobalStateManager;
import com.github.greenfinger.core.engine.CrawlCoordinator;
import com.github.greenfinger.core.engine.CrawlFrontier;
import com.github.greenfinger.core.engine.CrawlTask;
import lombok.extern.slf4j.Slf4j;

/**
 * The recursive call, made across the cluster.
 *
 * <p>
 * Every url found on a page goes out as one unicast, round robin, this node included. The engine
 * on the other side receives it and calls the same function -- that is the whole of the
 * distribution, and it is why there is no join anywhere: the parent page has no use for what its
 * children found.
 *
 * <h2>Nothing is ever dropped for want of a peer</h2>
 * A dispatch that finds nobody -- alone in the cluster, resting, a send that failed -- falls back
 * to this node's own frontier. So a single process behaves exactly as it did before this class
 * existed, and a cluster that loses every peer mid-crawl keeps going rather than stalling.
 *
 * <h2>When it is over is not this node's decision</h2>
 * An empty frontier here says nothing about the crawl: a peer may be holding a thousand urls. The
 * answer comes from the leader over the control channel, and until it arrives the loop waits. What
 * the leader compares is the pair of counters this class maintains -- one url counted the moment
 * it is dispatched, and counted again when somebody has finished with it.
 * 
 * @Description: ClusterCrawlCoordinator
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class ClusterCrawlCoordinator implements CrawlCoordinator {

    private final CrawlTaskChannel channel;
    private final CrawlFrontier frontier;
    private final GlobalStateManager stateManager;
    private final String catalogId;
    private final java.util.function.BooleanSupplier leadership;
    private final java.util.function.Supplier<String> nodeId;
    private final Runnable onClose;

    /** How this coordinator tells the cluster the run is over. See {@link CompletionAnnouncer}. */
    private final CompletionAnnouncer completionAnnouncer;

    private final AtomicLong dispatched = new AtomicLong();
    private final AtomicLong keptLocally = new AtomicLong();

    public ClusterCrawlCoordinator(CrawlTaskChannel channel, CrawlFrontier frontier,
            GlobalStateManager stateManager, String catalogId,
            java.util.function.BooleanSupplier leadership,
            java.util.function.Supplier<String> nodeId, Runnable onClose,
            CompletionAnnouncer completionAnnouncer) {
        this.channel = channel;
        this.frontier = frontier;
        this.stateManager = stateManager;
        this.catalogId = catalogId;
        this.leadership = leadership;
        this.nodeId = nodeId;
        this.onClose = onClose;
        this.completionAnnouncer = completionAnnouncer;
    }

    /**
     * Announcing the end of a run, kept as a callback for the same reason leadership and the node
     * id are: this coordinator is given what it needs by the cluster rather than reaching back
     * into it, and a test can hand it something that records instead.
     */
    @FunctionalInterface
    public interface CompletionAnnouncer {

        boolean announce(String catalogId, int version, String reason, boolean interrupted);
    }

    public String getCatalogId() {
        return catalogId;
    }

    @Override
    public void dispatch(CrawlTask task) throws Exception {
        // Counted before it is sent, never after. The node that receives it may finish it and
        // report that within a millisecond, and a handled that arrived before its own dispatch
        // would make the two counters cross -- which is the one thing the completion test relies
        // on never happening.
        stateManager.incrementCount(task.getTimestamp(), CountingType.TOTAL_URL_COUNT);
        dispatched.incrementAndGet();
        if (!channel.dispatch(task)) {
            keptLocally.incrementAndGet();
            if (!frontier.put(task)) {
                // see CrawlTaskChannel.queue: a url the frontier already has is one nothing will
                // report handled, so it is settled here instead
                afterHandled(task);
                stateManager.incrementCount(task.getTimestamp(), CountingType.EXISTING_URL_COUNT);
            }
        }
    }

    @Override
    public void afterHandled(CrawlTask task) {
        stateManager.incrementCount(task.getTimestamp(), CountingType.HANDLED_URL_COUNT);
    }

    /**
     * The run is over: said once, over the control channel, and heard by every node including
     * this one. See {@link ControlMessage.Type#COMPLETED}.
     */
    @Override
    public boolean announceCompleted(String catalogId, int version, String reason,
            boolean interrupted) {
        return completionAnnouncer.announce(catalogId, version, reason, interrupted);
    }

    /**
     * The leader publishes, and it is asked now rather than when the crawl started: leadership can
     * change part way through, and the node that is holding the cluster port at the end is the one
     * every node agrees on.
     */
    @Override
    public boolean shouldPublish() {
        return leadership.getAsBoolean();
    }

    @Override
    public void close() {
        if (log.isInfoEnabled()) {
            log.info("Catalog {} on this node: {} url(s) dispatched ({} kept locally), counters"
                    + " report {} dispatched / {} handled", catalogId, dispatched.get(),
                    keptLocally.get(),
                    stateManager.getDashboard().getTotalUrlCount(),
                    stateManager.getDashboard().getHandledUrlCount());
        }
        onClose.run();
    }

    @Override
    public String nodeId() {
        return nodeId.get();
    }

    public long dispatchedCount() {
        return dispatched.get();
    }

    /** Urls this node dispatched and then had to do itself. High means the cluster is not helping. */
    public long keptLocallyCount() {
        return keptLocally.get();
    }

}
