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
import java.util.Set;
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
import com.github.greenfinger.cluster.leader.CatalogCatchUp;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.catalog.CatalogDetailsNotFoundException;
import com.github.greenfinger.core.engine.CrawlCoordinator;
import com.github.greenfinger.core.engine.CrawlCoordinatorFactory;
import com.github.greenfinger.core.engine.CrawlRegistry;
import com.github.greenfinger.core.engine.WebCrawlerCompletionEvent;
import com.github.greenfinger.core.engine.CrawlRun;
import com.github.greenfinger.core.engine.WebCrawlerExecutionContext;
import com.github.greenfinger.core.model.DeleteLayer;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.service.CrawlerLauncher;
import com.github.greenfinger.service.DeletionService;
import com.github.greenfinger.service.ReplayService;
import lombok.extern.slf4j.Slf4j;

/**
 * Who joins a crawl.
 *
 * <p>
 * It is started on one node, which announces it; every other node opens its own half -- same
 * components, same output channels, no entry point. Without that, a url dispatched to a peer
 * arrives at a process with no frontier to put it on.
 *
 * <p>
 * Nobody decides it is over, here or on the leader: the limits are questions about shared counters,
 * so every node reaches the same answer and the first to notice writes the flag. The leader's only
 * part in a crawl is publishing the search version, which is idempotent and merely avoids three
 * nodes writing one row; administrative writes are separate -- see {@code LeaderCatalogStore}.
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

    /** Also looked up late, and for the same reason: it is downstream of this bean. */
    private final ObjectProvider<DeletionService> deletionService;

    /** Asked when a crawl is announced for a catalog this node has never heard of. */
    private final ObjectProvider<CatalogCatchUp> catchUp;

    /** This node's own table: read to announce a catalog, written to accept one. */
    private final ObjectProvider<CatalogStore> catalogStore;

    /** Where a finished crawl is announced to whatever this process has listening. */
    private final ApplicationEventPublisher eventPublisher;

    private final Map<String, ClusterCrawlCoordinator> coordinators = new ConcurrentHashMap<>();

    private ExecutorService joiners;

    public CrawlCluster(GossipCluster cluster, CrawlTaskChannel crawlChannel,
            CrawlRegistry crawlRegistry, ObjectProvider<CrawlerLauncher> launcher,
            ObjectProvider<ReplayService> replayService,
            ObjectProvider<DeletionService> deletionService,
            ObjectProvider<CatalogCatchUp> catchUp, ObjectProvider<CatalogStore> catalogStore,
            ApplicationEventPublisher eventPublisher) {
        this.cluster = cluster;
        this.crawlChannel = crawlChannel;
        this.crawlRegistry = crawlRegistry;
        this.launcher = launcher;
        this.replayService = replayService;
        this.deletionService = deletionService;
        this.catchUp = catchUp;
        this.catalogStore = catalogStore;
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
                    catalogDetails.getVersion(), run.refresh(), rowOf(catalogId)));
        }
        return coordinator;
    }

    // ---- the control channel ----------------------------------------------------------------

    private void onControl(ControlMessage message) {
        switch (message.type()) {
            case STARTED -> joinLater(message);
            case COMPLETED -> publishCompletion(message);
            case RESTORE_FILES -> restoreFilesHere(message);
            case PURGE_LOCAL -> purgeHere(message);
        }
    }

    /** How long a node keeps trying to open its half before giving the run up. */
    private static final int JOIN_ATTEMPTS = 10;

    private static final long JOIN_RETRY_MS = 3000L;

    /**
     * Tries again rather than sitting the crawl out.
     *
     * <p>
     * The refusal worth waiting for is "another crawl is already running": the previous run is
     * winding down here while the node that started the next one has already finished its own
     * half. Giving up on the first refusal leaves this node out of the whole crawl, and the urls
     * it was sent expire on its staging queue -- which the cluster reports, correctly but
     * uselessly, as a node that stopped answering.
     */
    private void joinWithRetries(ControlMessage message) {
        for (int attempt = 1; attempt <= JOIN_ATTEMPTS; attempt++) {
            try {
                join(message);
                return;
            } catch (Exception e) {
                if (attempt == JOIN_ATTEMPTS) {
                    log.error("Could not join the crawl of catalog {} after {} attempt(s): {}",
                            message.catalogId(), attempt, e.getMessage(), e);
                    return;
                }
                log.info("Cannot open catalog {} here yet ({}); trying again", message.catalogId(),
                        e.getMessage());
                try {
                    Thread.sleep(JOIN_RETRY_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** The row behind a catalog, for an announcement to carry. */
    private Catalog rowOf(String catalogId) {
        CatalogStore store = catalogStore.getIfAvailable();
        return store != null ? store.findById(catalogId).orElse(null) : null;
    }

    /**
     * A crawl of a catalog this node has never heard of is a race, not a failure: the
     * announcement is small, immediate and unbatched, and the row that follows it waits for a
     * replication flush. The announcement therefore carries the row, and opening this node's half
     * costs nothing but the write.
     *
     * <p>
     * Asking the leader is the fallback, for a message from an older build that carries no row. It
     * is a round trip on a channel with a thirty second timeout, and a crawl of a small site can
     * be over before it answers -- which is why it is not the first move.
     */
    private void join(ControlMessage message) throws Exception {
        try {
            launcher.getObject().join(message.catalogId(), message.action(), message.refresh());
        } catch (CatalogDetailsNotFoundException e) {
            if (!acceptCarriedCatalog(message) && !askTheLeaderFor(message.catalogId())) {
                throw e;
            }
            launcher.getObject().join(message.catalogId(), message.action(), message.refresh());
        }
    }

    /**
     * Writes the row the announcement carried, unless replication has already landed it. The two
     * arrive on different channels and either can be first, so this is a race by construction:
     * what matters is that the row is here, not who put it there.
     */
    private boolean acceptCarriedCatalog(ControlMessage message) {
        CatalogStore store = catalogStore.getIfAvailable();
        if (message.catalog() == null || store == null) {
            return false;
        }
        if (store.findById(message.catalogId()).isPresent()) {
            return true;
        }
        try {
            store.save(message.catalog());
            log.info("Catalog {} arrived with the announcement of its crawl", message.catalogId());
        } catch (RuntimeException e) {
            if (store.findById(message.catalogId()).isEmpty()) {
                throw e;
            }
            log.debug("Catalog {} was replicated while its announcement was being applied",
                    message.catalogId());
        }
        return true;
    }

    private boolean askTheLeaderFor(String catalogId) {
        CatalogCatchUp available = catchUp.getIfAvailable();
        if (available == null) {
            return false;
        }
        log.info("Catalog {} is not here yet; asking the leader for it before joining", catalogId);
        available.catchUp();
        return true;
    }

    private void joinLater(ControlMessage message) {
        if (crawlRegistry.getContext(message.catalogId()) != null) {
            // already running here: this is the node that started it, hearing its own message
            return;
        }
        // never on the dispatch thread: opening a run touches the database, the blob store and,
        // when the vector output is on, a model that takes seconds to load
        joiners.execute(() -> joinWithRetries(message));
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
                        Set.of(OutputType.FILE), 0, Integer.MAX_VALUE);
            } catch (Exception e) {
                log.error("Could not restore the files of catalog {} here: {}",
                        message.catalogId(), e.getMessage(), e);
            }
        });
    }

    /**
     * Asks every other node to remove its own copy of the index and the crawl state directories.
     *
     * <p>
     * Sent after the delete here has finished, not before: the two layers this covers are the
     * ones no store copies, and everything else in the same delete has already replicated itself
     * through the store that performed it.
     */
    public void announcePurge(String catalogId, Integer version, String layers,
            boolean dropIndex) {
        controlChannel.announce(ControlMessage.purgeLocal(catalogId, version, layers, dropIndex,
                cluster.self().id()));
    }

    /**
     * Removes this node's own index documents and RocksDB directories for the version named.
     *
     * <p>
     * The node that asked has already done its own, and says so, so it does not repeat the work.
     * Everybody else does it against their own paths -- which is the whole point, since the paths
     * differ per node and there is nothing to copy across.
     */
    private void purgeHere(ControlMessage message) {
        if (cluster.self().id().equals(message.reason())) {
            return;
        }
        Integer version = message.version() == ControlMessage.EVERY_VERSION ? null
                : Integer.valueOf(message.version());
        // never on the dispatch thread: emptying a Lucene index and walking three directory
        // trees is disk work, and every other message is queued behind this one
        joiners.execute(() -> {
            try {
                long documents = deletionService.getObject().purgeNodeLocal(message.catalogId(),
                        version, DeleteLayer.parse(message.layers()), message.dropIndex());
                log.info("Removed this node's own copy of catalog {} {}: {} document(s)",
                        message.catalogId(),
                        version != null ? "v" + version : "(every version)", documents);
            } catch (Exception e) {
                log.error("Could not remove this node's copy of catalog {}: {}",
                        message.catalogId(), e.getMessage(), e);
            }
        });
    }

    /** Released when the run ends, whichever way it ended. */
    public void forget(String catalogId) {
        coordinators.remove(catalogId);
    }

}
