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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import com.chaconneai.spreader.GossipCluster;
import com.github.greenfinger.cluster.channel.ControlChannel;
import com.github.greenfinger.cluster.channel.ControlMessage;
import com.github.greenfinger.cluster.channel.CrawlTaskChannel;
import com.github.greenfinger.core.ManagedBeanLifeCycle;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawlCoordinator;
import com.github.greenfinger.core.engine.CrawlCoordinatorFactory;
import com.github.greenfinger.core.engine.CrawlRegistry;
import com.github.greenfinger.core.engine.WebCrawlerCompletionEvent;
import com.github.greenfinger.core.engine.CrawlRun;
import com.github.greenfinger.core.engine.WebCrawlerExecutionContext;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.service.CrawlerLauncher;
import com.github.greenfinger.service.ReplayService;
import lombok.extern.slf4j.Slf4j;

/**
 * The one thing about a crawl that is nobody's business but the cluster's: who joins it.
 *
 * <h2>Joining</h2>
 * A crawl is started on one node by a command. That node announces it, and every other node opens
 * its own half -- the same components, the same output channels, the same worker loop, just
 * without the entry point. Without this, urls dispatched to a peer would arrive at a process that
 * has no frontier to put them on.
 *
 * <h2>Nobody decides it is over</h2>
 * Not here, and not on the leader. Whether the crawl has reached {@code maxFetchSize} or run out
 * of {@code fetchDuration} is a question about the shared counters, so every node asks it of the
 * same numbers and reaches the same answer; the first to notice writes the flag and the reason
 * beside those counters, and the others read it on their next tick. That is how 1.x worked, with
 * Redis where this has the cluster cache, and it is why this class has no supervisor: a leader
 * that judged completion would be a single point of failure for a decision that does not need
 * one, and it would only ever see the crawls it happened to be taking part in.
 *
 * <h2>What the leader is still for</h2>
 * Publishing the search version, and that is all -- see {@code ClusterCrawlCoordinator}. It is not
 * a correctness mechanism either: publishing is idempotent, and doing it on the leader only avoids
 * three nodes writing the same row three times.
 * 
 * @Description: CrawlCluster
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class CrawlCluster implements CrawlCoordinatorFactory, ManagedBeanLifeCycle {

    private final GossipCluster cluster;
    private final CrawlTaskChannel crawlChannel;
    private final ControlChannel controlChannel;
    private final CrawlRegistry crawlRegistry;

    /** Lazy: the launcher needs this class as its coordinator factory, so the two are circular. */
    private final ObjectProvider<CrawlerLauncher> launcher;
    /** Looked up late: it is a bean this one is a dependency of. */
    private final ObjectProvider<ReplayService> replayService;

    /** Where a finished crawl is announced to whatever this process has listening. */
    private final ApplicationEventPublisher eventPublisher;

    private final Map<String, ClusterCrawlCoordinator> coordinators = new ConcurrentHashMap<>();

    private ExecutorService joiners;

    public CrawlCluster(GossipCluster cluster, CrawlTaskChannel crawlChannel,
            CrawlRegistry crawlRegistry, ObjectProvider<CrawlerLauncher> launcher,
            ObjectProvider<ReplayService> replayService,
            ApplicationEventPublisher eventPublisher) {
        this.cluster = cluster;
        this.crawlChannel = crawlChannel;
        this.crawlRegistry = crawlRegistry;
        this.launcher = launcher;
        this.replayService = replayService;
        this.eventPublisher = eventPublisher;
        this.controlChannel = new ControlChannel(cluster, this::onControl);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        crawlChannel.start();
        controlChannel.start();
        joiners = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "greenfinger-join");
            thread.setDaemon(true);
            return thread;
        });
        log.info("Crawl cluster ready as {}{}", cluster.self().label(),
                cluster.isLeader() ? " (leader)" : "");
    }

    @Override
    public void destroy() throws Exception {
        if (joiners != null) {
            joiners.shutdownNow();
        }
        controlChannel.stop();
        crawlChannel.stop();
    }

    // ---- starting ---------------------------------------------------------------------------

    /**
     * The node a crawl was started on seeds the entry point and tells the others; every node,
     * that one included, then dispatches and receives urls on equal terms. There is no
     * coordinator that only coordinates -- the leader fetches pages like everybody else, and the
     * only things reserved to it are the two judgements that must be made once.
     */
    @Override
    public CrawlCoordinator create(CrawlRun run) {
        WebCrawlerExecutionContext context = run.context();
        CatalogDetails catalogDetails = context.getCatalogDetails();
        String catalogId = catalogDetails.getId();
        ClusterCrawlCoordinator coordinator = new ClusterCrawlCoordinator(crawlChannel,
                context.getCrawlFrontier(), context.getGlobalStateManager(), catalogId,
                cluster::isLeader, () -> cluster.self().shortId(), () -> forget(catalogId),
                this::announceCompleted);
        coordinators.put(catalogId, coordinator);
        context.getGlobalStateManager().addMember(cluster.self().id());
        if (run.initiator()) {
            // said before the first url is dispatched, so the others are opening their half while
            // this node is still fetching the entry page
            controlChannel.announce(ControlMessage.started(catalogId, run.action(),
                    catalogDetails.getVersion(), run.refresh()));
        }
        return coordinator;
    }

    // ---- the control channel ----------------------------------------------------------------

    private void onControl(ControlMessage message) {
        switch (message.type()) {
            case STARTED -> joinLater(message);
            case COMPLETED -> publishCompletion(message);
            case RESTORE_FILES -> restoreFilesHere(message);
        }
    }

    private void joinLater(ControlMessage message) {
        if (crawlRegistry.getContext(message.catalogId()) != null) {
            // already running here: this is the node that started it, hearing its own message
            return;
        }
        // never on the dispatch thread: opening a run touches the database, the blob store and,
        // when the vector output is on, a model that takes seconds to load
        joiners.execute(() -> {
            try {
                launcher.getObject().join(message.catalogId(), message.action(),
                        message.refresh());
            } catch (Exception e) {
                log.error("Could not join the crawl of catalog {}: {}", message.catalogId(),
                        e.getMessage(), e);
            }
        });
    }

    /**
     * Tells every node, this one included, that the run is over.
     *
     * <p>
     * Called only where the run wound down, because the announcement comes back to the sender like
     * any other: announcing it on each node would give every node one event per node.
     */
    public boolean announceCompleted(String catalogId, int version, String reason,
            boolean interrupted) {
        controlChannel.announce(ControlMessage.completed(catalogId, version, reason, interrupted));
        return true;
    }

    /**
     * Hands the announcement to whatever is listening in this process, and no further.
     *
     * <p>
     * Nothing in the crawler listens: by the time this is sent the run has finished, its version
     * is published and its stores are closed. A listener that throws is logged and stepped over,
     * because a failure in somebody's notification is not a failure of the crawl that prompted it.
     */
    private void publishCompletion(ControlMessage message) {
        try {
            eventPublisher.publishEvent(new WebCrawlerCompletionEvent(this, message.catalogId(),
                    message.version(), message.reason(), message.interrupted()));
        } catch (RuntimeException e) {
            log.warn("A listener of the completion of catalog {} failed: {}", message.catalogId(),
                    e.getMessage(), e);
        }
    }

    /** Asks every other node to put back whatever of this version is missing from its own copy. */
    public void announceRestoreFiles(String catalogId, int version) {
        controlChannel.announce(
                ControlMessage.restoreFiles(catalogId, version, cluster.self().id()));
    }

    /**
     * Puts back whatever of this version is missing from this node's own file store.
     *
     * <p>
     * Every node hears this, including the one that asked -- which has already done its own and
     * says so, so it does not fetch everything twice.
     */
    private void restoreFilesHere(ControlMessage message) {
        if (cluster.self().id().equals(message.reason())) {
            return;
        }
        // never on the dispatch thread: this fetches pages from the internet, one at a time and
        // politely, which is minutes rather than milliseconds
        joiners.execute(() -> {
            try {
                replayService.getObject().replaySlice(message.catalogId(), message.version(),
                        java.util.Set.of(OutputType.FILE), 0, Integer.MAX_VALUE);
            } catch (Exception e) {
                log.error("Could not restore the files of catalog {} here: {}",
                        message.catalogId(), e.getMessage(), e);
            }
        });
    }

    /** Released when the run ends, whichever way it ended. */
    public void forget(String catalogId) {
        coordinators.remove(catalogId);
    }

}
