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

package com.github.greenfinger.cluster.leader;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import com.chaconneai.openspreader.cluster.SelfRegisteringListener;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.chaconneai.spreader.event.GossipListener;
import com.github.greenfinger.cluster.replication.ReplicatedCatalogStore;
import com.github.greenfinger.core.ManagedBeanLifeCycle;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.model.Catalog;
import lombok.extern.slf4j.Slf4j;

/**
 * Makes this node's catalog table match the leader's.
 *
 * <h2>Why this is the whole of the repair</h2>
 * Every change to a catalog is performed by one node, so "what the leader holds" is not an opinion
 * to be reconciled against other opinions -- it is the state, and a node that fell behind has
 * nothing to contribute, only something to take. Missing rows are copied, differing rows are
 * overwritten, and rows the leader does not have are removed because there is no other way for
 * them to exist.
 *
 * <p>
 * That last line is worth sitting with, because it is what a single writer buys. Without one,
 * telling a delete apart from a row the leader has not heard about yet is impossible from the rows
 * themselves: a node that missed a delete looks exactly like the node that made the row. It takes
 * remembering every deletion, with its moment, and keeping those memories alive long enough to
 * reach a node that was down -- a tombstone table, with a lifetime, and a gap in it if every node
 * restarts at once. With one writer, all of that collapses into "the leader does not have it".
 *
 * <h2>When</h2>
 * On joining, because a node that was away is exactly the node that is behind; when a member
 * joins, because it may be that node; when leadership changes, because the answer now comes from
 * somewhere else; and on a slow timer as a backstop for a write whose broadcast was lost.
 *
 * <p>
 * The leader skips all of it. Asking itself what it holds and then agreeing is a round trip to
 * prove a table agrees with itself.
 *
 * @Description: CatalogCatchUp
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class CatalogCatchUp
        implements GossipListener, SelfRegisteringListener, ManagedBeanLifeCycle {

    private final GossipCluster cluster;
    private final LeaderCatalogStore store;

    /** This node's own table, written directly: what arrives is already the decided state. */
    private final CatalogStore local;

    private final long intervalMs;

    private final AtomicLong rounds = new AtomicLong();
    private final AtomicLong copied = new AtomicLong();
    private final AtomicLong removed = new AtomicLong();

    /** Long enough for the membership to converge, short enough not to be noticed. */
    private static final long FIRST_RUN_MS = 3_000L;

    private ScheduledExecutorService ticker;

    public CatalogCatchUp(GossipCluster cluster, LeaderCatalogStore store, CatalogStore local,
            long intervalMs) {
        this.cluster = cluster;
        this.store = store;
        this.local = local;
        this.intervalMs = intervalMs;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        cluster.addListener(this);
        ticker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "greenfinger-catchup");
            thread.setDaemon(true);
            return thread;
        });
        // The first run is soon rather than an interval away. A node that has just started is
        // exactly the node most likely to be behind, and the membership callbacks do not cover
        // it: the cluster is started by the auto-configuration before this bean exists, so this
        // node has already joined by the time the listener is registered and its own
        // onClusterJoined never fires. Waiting for the timer meant a restarted node served a
        // catalog that had been deleted for up to a minute -- measured, not supposed.
        //
        // Not immediately either: membership takes a moment to converge, and a catch-up run
        // while this node still believes it is alone does nothing and uses up the moment.
        ticker.scheduleWithFixedDelay(this::catchUpQuietly, Math.min(intervalMs, FIRST_RUN_MS),
                intervalMs, TimeUnit.MILLISECONDS);
        // Long.MAX_VALUE is how "no timer" arrives here, and it is the ordinary case on a shared
        // database: the catalog table is one table and there is nothing to catch up with. Said in
        // those words rather than printed as a number, which read as a misconfiguration.
        if (intervalMs == Long.MAX_VALUE) {
            log.info("Catalog catch-up on membership changes only: the catalog table is shared,"
                    + " so there are no copies to reconcile");
        } else {
            log.info("Catalog catch-up every {}ms, first run in {}ms, and whenever the membership"
                    + " changes", intervalMs, Math.min(intervalMs, FIRST_RUN_MS));
        }
    }

    @Override
    public void destroy() throws Exception {
        if (ticker != null) {
            ticker.shutdownNow();
        }
        cluster.removeListener(this);
    }

    @Override
    public void onClusterJoined(Node self, boolean alone) {
        if (!alone) {
            catchUpQuietly();
        }
    }

    @Override
    public void onNodeJoined(Node node) {
        catchUpQuietly();
    }

    @Override
    public void onLeaderChanged(Node previous, Node current, boolean selfIsLeader) {
        if (!selfIsLeader) {
            catchUpQuietly();
        }
    }

    private void catchUpQuietly() {
        try {
            catchUp();
        } catch (RuntimeException e) {
            // the next membership change or the next tick will try again
            log.warn("Could not catch up with the leader: {}", e.getMessage());
        }
    }

    /**
     * @return how many rows this node was behind by, copied plus removed
     */
    long catchUp() {
        if (cluster.isLeader() || cluster.members().size() < 2) {
            return 0L;
        }
        rounds.incrementAndGet();
        return align(store.fromLeader());
    }

    /**
     * Makes this node's table say exactly what the leader's does.
     *
     * <p>
     * Separate from fetching it so that what it does to a real table can be tested without a
     * cluster: the fetch is one request and has its own test against real nodes, and this is the
     * part with the rules in it.
     *
     * @param authoritative every catalog the leader holds
     * @return how many rows this node was behind by, copied plus removed
     */
    public long align(List<Catalog> authoritative) {
        Set<String> kept = new HashSet<>();
        for (Catalog incoming : authoritative) {
            kept.add(incoming.getId());
        }
        long changed = 0L;

        // Removals first, and the order is not a detail. A catalog is unique by name, and the row
        // on its way out is very often the one holding the name the incoming row wants -- that is
        // exactly the shape of the failure this whole mechanism exists to repair: a node kept a
        // stale row and refused the catalog that replaced it. Copying first walks straight back
        // into it, on a node that is in the middle of being put right.
        for (Catalog mine : local.findAll()) {
            if (!kept.contains(mine.getId())) {
                // the leader performs every change, so a row it does not have is a row that was
                // deleted while this node was not listening
                local.deleteById(mine.getId());
                removed.incrementAndGet();
                changed++;
                log.warn("Removed catalog '{}' here: the leader does not have it", mine.getName());
            }
        }

        for (Catalog incoming : authoritative) {
            Catalog mine = local.findById(incoming.getId()).orElse(null);
            if (mine == null || !ReplicatedCatalogStore.sameAs(mine, incoming)) {
                local.save(incoming);
                copied.incrementAndGet();
                changed++;
            }
        }
        if (changed > 0) {
            log.info("Caught up with the leader: {} row(s) were out of step", changed);
        }
        return changed;
    }

    /** How many times this node has asked. */
    public long roundCount() {
        return rounds.get();
    }

    /** Rows taken from the leader because this node was missing them or had them wrong. */
    public long copiedCount() {
        return copied.get();
    }

    /** Rows removed here because the leader does not have them. */
    public long removedCount() {
        return removed.get();
    }

}
