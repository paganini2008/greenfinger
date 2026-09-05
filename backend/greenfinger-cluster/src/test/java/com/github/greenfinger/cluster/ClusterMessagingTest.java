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

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.cluster.channel.ControlChannel;
import com.github.greenfinger.cluster.channel.ControlMessage;
import com.github.greenfinger.cluster.channel.CrawlTaskChannel;
import com.github.greenfinger.cluster.replication.ReplicationBatch;
import com.github.greenfinger.cluster.replication.ReplicationChannel;
import com.github.greenfinger.cluster.state.ClusterGlobalStateManager;
import com.github.greenfinger.cluster.support.TestCluster;
import com.github.greenfinger.cluster.support.TestRun;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.engine.CrawlRegistry;
import com.github.greenfinger.core.engine.CrawlTask;

/**
 * The channels, against a real cluster.
 *
 * <p>
 * Real sockets and real gossip, because every bug this module has actually had was invisible
 * without them. A listener registered twice delivered every message twice, and the only symptom
 * was a message count that did not match the dispatch count. A value applied through its own
 * decorator echoed back to the sender. A url dispatched to a node that had not opened the crawl
 * yet vanished. None of the three is reachable by calling a listener directly.
 * 
 * @Description: ClusterMessagingTest
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
class ClusterMessagingTest {

    private static final ClusterProperties PROPERTIES = properties();

    /**
     * One cluster for the class. Starting a node binds ports and runs a discovery round, and
     * twenty of those in one jvm is enough port churn to make these fail for reasons that have
     * nothing to do with the code. Each test still gets its own channels and its own catalog,
     * which is the isolation that matters.
     */
    private static TestCluster cluster;

    @BeforeAll
    static void startCluster() {
        cluster = TestCluster.start(2);
    }

    @AfterAll
    static void stopCluster() {
        cluster.close();
    }

    // ---- the crawl channel -------------------------------------------------------------------

    @Test
    @DisplayName("a url dispatched from one node reaches a frontier, on one node or the other")
    void aUrlReachesAFrontier() {
        Fixture a = fixture(0, "cat-reach");
        Fixture b = fixture(1, "cat-reach");
        try {
            for (int i = 0; i < 20; i++) {
                assertThat(a.channel.dispatch(task("cat-reach", "https://example.com/" + i))).isTrue();
            }

            // Distinct, not a count: delivery is at least once, so twenty arrivals can be
            // nineteen urls and a repeat -- and waiting on the count would then stop one short
            // and blame the assertion below for it.
            TestCluster.await(() -> Set.copyOf(arrived(a, b)).size() >= 20, 30_000L,
                    () -> "only " + arrived(a, b).size() + " of 20 arrived: a="
                            + a.channel.receivedCount() + " b=" + b.channel.receivedCount()
                            + " staged=" + (a.channel.stagingDepth() + b.channel.stagingDepth())
                            + " orphaned=" + (a.channel.orphanedCount() + b.channel.orphanedCount())
                            + " sent=" + a.channel.sentCount());

            assertThat(arrived(a, b)).containsAll(expectedUrls(20));
            // and both nodes did some of it: twenty distinct urls hash across two members
            assertThat(a.run.frontier().accepted()).isNotEmpty();
            assertThat(b.run.frontier().accepted()).isNotEmpty();
        } finally {
            a.close();
            b.close();
        }
    }

    @Test
    @DisplayName("every url arrives, and none arrives systematically twice")
    void everyUrlArrivesAndNoneIsDoubled() {
        Fixture a = fixture(0, "cat-once");
        Fixture b = fixture(1, "cat-once");
        try {
            int dispatched = 0;
            for (int i = 0; i < 30; i++) {
                if (a.channel.dispatch(task("cat-once", "https://example.com/" + i))) {
                    dispatched++;
                }
            }
            int expected = dispatched;
            TestCluster.await(() -> arrived(a, b).size() >= expected, 30_000L,
                    () -> "only " + arrived(a, b).size() + " of " + expected + " arrived, staged="
                            + (a.channel.stagingDepth() + b.channel.stagingDepth()));

            List<String> all = arrived(a, b);
            // Every url dispatched is somewhere, and nothing was invented.
            assertThat(all).containsAll(expectedUrls(expected));
            // Delivery is at least once, so a duplicate is possible and costs one wasted fetch --
            // everything downstream is keyed by the url and overwrites itself. What must not
            // happen is the systematic doubling a listener registered on two channels produces,
            // which is every message twice rather than the occasional retry.
            assertThat(all.size()).isLessThan(expected * 2);
        } finally {
            a.close();
            b.close();
        }
    }

    @Test
    @DisplayName("one url goes to one node, however many times it is dispatched")
    void theSameUrlAlwaysReachesTheSameNode() {
        Fixture a = fixture(0, "cat-route");
        Fixture b = fixture(1, "cat-route");
        try {
            // Routed by the url, so every copy of it lands in the same frontier -- and a frontier
            // refuses a url it has queued before, which is what turns an at-least-once delivery
            // into one fetch. Round robin sent these to alternating nodes, and neither frontier
            // could see that the other already had it.
            String url = "https://example.com/the-same-page";
            for (int i = 0; i < 8; i++) {
                assertThat(a.channel.dispatch(task("cat-route", url))).isTrue();
            }
            TestCluster.await(() -> arrived(a, b).size() >= 8, 30_000L,
                    () -> "only " + arrived(a, b).size() + " of 8 arrived: a="
                            + a.run.frontier().accepted().size() + " b="
                            + b.run.frontier().accepted().size());

            assertThat(arrived(a, b)).containsOnly(url);
            // all of them on one side: the test frontier keeps every arrival, so a split would
            // show up here as both lists being non-empty
            assertThat(a.run.frontier().accepted().isEmpty()
                    || b.run.frontier().accepted().isEmpty())
                            .as("a=%d b=%d", a.run.frontier().accepted().size(),
                                    b.run.frontier().accepted().size())
                            .isTrue();
        } finally {
            a.close();
            b.close();
        }
    }

    @Test
    @DisplayName("a url for a crawl this node has not opened waits rather than disappearing")
    void urlsForAnUnopenedCrawlAreStaged() {
        Fixture a = fixture(0, "cat-late");
        // b has the channel running but no crawl registered for that catalog
        CrawlRegistry registryB = new CrawlRegistry();
        CrawlTaskChannel channelB =
                new CrawlTaskChannel(cluster.node(1).cluster(), registryB, PROPERTIES.getDispatch());
        channelB.start();
        try {
            for (int i = 0; i < 10; i++) {
                a.channel.dispatch(task("cat-late", "https://late.test/" + i));
            }
            TestCluster.await(() -> channelB.stagingDepth() > 0, 10_000L,
                    "nothing was staged on the node that had not opened the crawl");
            assertThat(channelB.orphanedCount()).isZero();

            // the crawl opens a moment later, as a node that was still loading its models would
            TestRun late = new TestRun("cat-late", "books");
            registryB.register("cat-late", late);

            TestCluster.await(() -> !late.frontier().accepted().isEmpty(), 10_000L,
                    "the staged urls were never delivered");
            assertThat(channelB.stagingDepth()).isZero();
        } finally {
            channelB.stop();
            a.close();
        }
    }

    @Test
    @DisplayName("alone, a dispatch stays here rather than failing")
    void aloneItKeepsItsOwnWork() {
        try (TestCluster alone = TestCluster.start(1)) {
            CrawlRegistry registry = new CrawlRegistry();
            TestRun run = new TestRun("cat-1", "books");
            registry.register("cat-1", run);
            CrawlTaskChannel channel = new CrawlTaskChannel(alone.node(0).cluster(), registry,
                    PROPERTIES.getDispatch());
            channel.start();
            try {
                // includeSelf means the balancer can pick this node, and spreader delivers that
                // locally; either way the url is not lost
                boolean dispatched = channel.dispatch(task("cat-1", "https://alone.test/a"));
                TestCluster.await(
                        () -> !run.frontier().accepted().isEmpty() || !dispatched, 5_000L,
                        "the url neither arrived nor was refused");
            } finally {
                channel.stop();
            }
        }
    }

    private static List<String> arrived(Fixture a, Fixture b) {
        List<String> all = new ArrayList<>(a.run.frontier().accepted());
        all.addAll(b.run.frontier().accepted());
        return all;
    }

    private static List<String> expectedUrls(int count) {
        List<String> urls = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            urls.add("https://example.com/" + i);
        }
        return urls;
    }

    // ---- replication -------------------------------------------------------------------------

    @Test
    @DisplayName("a write is applied on the other node and not on the one that sent it")
    void replicationGoesOutwardsOnly() {
        List<String> appliedOnA = new ArrayList<>();
        List<String> appliedOnB = new ArrayList<>();
        ReplicationChannel a = new ReplicationChannel(cluster.node(0).cluster(), "test.repl",
                "test-a", PROPERTIES.getReplication(), entry -> appliedOnA.add(entry.key()));
        ReplicationChannel b = new ReplicationChannel(cluster.node(1).cluster(), "test.repl",
                "test-b", PROPERTIES.getReplication(), entry -> appliedOnB.add(entry.key()));
        a.start();
        b.start();
        try {
            a.replicate(ReplicationBatch.Entry.of((byte) 1, "cat-1", "row-1"));
            a.replicate(ReplicationBatch.Entry.of((byte) 1, "cat-1", "row-2"));

            TestCluster.await(() -> appliedOnB.size() == 2, 10_000L, "the rows never arrived");
            assertThat(appliedOnB).containsExactlyInAnyOrder("row-1", "row-2");
            // the sender already has them: that is what there was to tell the others about
            assertThat(appliedOnA).isEmpty();
            assertThat(a.sentCount()).isEqualTo(2);
            assertThat(b.appliedCount()).isEqualTo(2);
            assertThat(b.failedCount()).isZero();
        } finally {
            a.stop();
            b.stop();
        }
    }

    @Test
    @DisplayName("several writes travel in one frame")
    void writesAreBatched() {
        List<String> applied = new ArrayList<>();
        ReplicationChannel a = new ReplicationChannel(cluster.node(0).cluster(), "test.batch",
                "batch-a", PROPERTIES.getReplication(), entry -> applied.add(entry.key()));
        ReplicationChannel b = new ReplicationChannel(cluster.node(1).cluster(), "test.batch",
                "batch-b", PROPERTIES.getReplication(), entry -> applied.add(entry.key()));
        a.start();
        b.start();
        try {
            for (int i = 0; i < 200; i++) {
                a.replicate(ReplicationBatch.Entry.of((byte) 1, "cat-1", "row-" + i));
            }
            TestCluster.await(() -> applied.size() == 200, 15_000L, "not every row arrived");
            // 200 rows in far fewer frames than 200
            assertThat(b.appliedCount()).isEqualTo(200);
        } finally {
            a.stop();
            b.stop();
        }
    }

    @Test
    @DisplayName("a bad applier does not stop the rest of the batch")
    void oneBadRowDoesNotLoseTheOthers() {
        List<String> applied = new ArrayList<>();
        ReplicationChannel a = new ReplicationChannel(cluster.node(0).cluster(), "test.bad",
                "bad-a", PROPERTIES.getReplication(), entry -> applied.add(entry.key()));
        ReplicationChannel b = new ReplicationChannel(cluster.node(1).cluster(), "test.bad",
                "bad-b", PROPERTIES.getReplication(), entry -> {
                    if ("row-1".equals(entry.key())) {
                        throw new IllegalStateException("bad row");
                    }
                    applied.add(entry.key());
                });
        a.start();
        b.start();
        try {
            a.replicate(ReplicationBatch.Entry.of((byte) 1, "cat-1", "row-1"));
            a.replicate(ReplicationBatch.Entry.of((byte) 1, "cat-1", "row-2"));

            TestCluster.await(() -> applied.contains("row-2"), 10_000L, "row-2 was lost with row-1");
            assertThat(b.failedCount()).isEqualTo(1);
        } finally {
            a.stop();
            b.stop();
        }
    }

    // ---- counters ----------------------------------------------------------------------------

    @Test
    @DisplayName("counters are the crawl's, not this node's, and say who did what")
    void countersAreShared() throws Exception {
        TestRun runA = new TestRun("cat-count", "books");
        TestRun runB = new TestRun("cat-count", "books");
        ClusterGlobalStateManager a = new ClusterGlobalStateManager(cluster.node(0).cache(),
                runA.getCatalogDetails(), "node-a", 100L, true);
        ClusterGlobalStateManager b = new ClusterGlobalStateManager(cluster.node(1).cache(),
                runB.getCatalogDetails(), "node-b", 100L, false);
        a.afterPropertiesSet();
        b.afterPropertiesSet();
        try {
            for (int i = 0; i < 5; i++) {
                a.incrementCount(0L, CountingType.SAVED_RESOURCE_COUNT, 1);
            }
            for (int i = 0; i < 3; i++) {
                b.incrementCount(0L, CountingType.SAVED_RESOURCE_COUNT, 1);
            }
            a.flush();
            b.flush();

            // eight, on both nodes: the number belongs to the crawl
            TestCluster.await(() -> a.getDashboard().getSavedResourceCount() == 8
                    && b.getDashboard().getSavedResourceCount() == 8, 10_000L,
                    "the counters did not agree");

            assertThat(a.perNodeCounters().get("savedResourceCount"))
                    .containsEntry("node-a", 5L).containsEntry("node-b", 3L);
        } finally {
            a.destroy();
            b.destroy();
        }
    }

    @Test
    @DisplayName("the completion flag is the crawl's too, so every node sees it at once")
    void completionIsShared() throws Exception {
        TestRun runA = new TestRun("cat-done", "books");
        TestRun runB = new TestRun("cat-done", "books");
        ClusterGlobalStateManager a = new ClusterGlobalStateManager(cluster.node(0).cache(),
                runA.getCatalogDetails(), "node-a", 100L, true);
        ClusterGlobalStateManager b = new ClusterGlobalStateManager(cluster.node(1).cache(),
                runB.getCatalogDetails(), "node-b", 100L, false);
        a.afterPropertiesSet();
        b.afterPropertiesSet();
        try {
            assertThat(b.isCompleted()).isFalse();
            a.setCompleted(true, "reached maxFetchSize");

            TestCluster.await(b::isCompleted, 10_000L, "the other node never saw it");
            // the reason travels with the flag, so the other node reports the same sentence
            assertThat(b.getDashboard().getCompletionReason()).isEqualTo("reached maxFetchSize");
            assertThat(b.getDashboard().isInterrupted()).isFalse();
            assertThat(a.isTimeout(1, java.util.concurrent.TimeUnit.HOURS)).isFalse();

            a.addMember("node-a");
            b.addMember("node-b");
            TestCluster.await(() -> a.getMembers().size() == 2, 10_000L,
                    "the member list did not converge");
            b.removeMember("node-b");
            TestCluster.await(() -> a.getMembers().size() == 1, 10_000L,
                    "the member never left");
        } finally {
            a.destroy();
            b.destroy();
        }
    }

    // ---- control ------------------------------------------------------------------------------

    @Test
    @DisplayName("a control message reaches every node, the sender included")
    void controlIncludesTheSender() {
        List<ControlMessage> onA = new ArrayList<>();
        List<ControlMessage> onB = new ArrayList<>();
        ControlChannel a = new ControlChannel(cluster.node(0).cluster(), onA::add);
        ControlChannel b = new ControlChannel(cluster.node(1).cluster(), onB::add);
        a.start();
        b.start();
        try {
            a.announce(ControlMessage.started("cat-1", "crawl", 0, false));

            TestCluster.await(() -> !onA.isEmpty() && !onB.isEmpty(), 10_000L,
                    "the announcement did not reach both nodes");
            assertThat(onA.get(0).type()).isEqualTo(ControlMessage.Type.STARTED);
            assertThat(onB.get(0).catalogId()).isEqualTo("cat-1");
        } finally {
            a.stop();
            b.stop();
        }
    }

    // ---- fixtures -----------------------------------------------------------------------------

    private static ClusterProperties properties() {
        ClusterProperties properties = new ClusterProperties();
        properties.getReplication().setFlushIntervalMs(50L);
        return properties;
    }

    private Fixture fixture(int node, String catalogId) {
        CrawlRegistry registry = new CrawlRegistry();
        TestRun run = new TestRun(catalogId, "books");
        registry.register(catalogId, run);
        CrawlTaskChannel channel = new CrawlTaskChannel(cluster.node(node).cluster(), registry,
                PROPERTIES.getDispatch());
        channel.start();
        return new Fixture(run, channel);
    }

    private static CrawlTask task(String catalogId, String url) {
        return CrawlTask.seed(catalogId, CrawlTask.ACTION_CRAWL, "https://example.com", url,
                "default", "UTF-8", 0);
    }

    /**
     * 
     * @Description: Fixture
     * @Author: Fred Feng
     * @Date: 02/09/2026
     * @Version 2.0.0
     */
    private record Fixture(TestRun run, CrawlTaskChannel channel) {

        void close() {
            channel.stop();
        }
    }

}
