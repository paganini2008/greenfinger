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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.cluster.channel.CrawlTaskChannel;
import com.github.greenfinger.cluster.support.TestCluster;
import com.github.greenfinger.cluster.support.TestRun;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.engine.CrawlRegistry;
import com.github.greenfinger.core.engine.CrawlTask;

/**
 * The recursive call, from the side that makes it.
 *
 * <p>
 * One node, deliberately: alone is the case where a dispatch finds nobody, and what it does then
 * -- keep the work rather than lose it -- is the property that makes a single process behave
 * exactly as it did before any of this existed.
 * 
 * @Description: ClusterCrawlCoordinatorTest
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
class ClusterCrawlCoordinatorTest {

    /**
     * One cluster for the class rather than one per test. Starting a node binds ports and runs a
     * discovery round; sixty of those in one jvm is enough port churn to make the module's tests
     * fail for reasons that have nothing to do with the code. Each test still gets its own
     * channel, registry and catalog, which is what it is actually asserting about.
     */
    private static TestCluster cluster;

    private CrawlRegistry registry;
    private CrawlTaskChannel channel;

    private final List<String> announced = new CopyOnWriteArrayList<>();
    private TestRun run;
    private String catalogId;

    @BeforeAll
    static void startCluster() {
        cluster = TestCluster.start(1);
    }

    @AfterAll
    static void stopCluster() {
        cluster.close();
    }

    @BeforeEach
    void setUp(org.junit.jupiter.api.TestInfo info) {
        catalogId = "cat-" + info.getTestMethod().map(java.lang.reflect.Method::getName)
                .orElse("x");
        registry = new CrawlRegistry();
        run = new TestRun(catalogId, "books");
        registry.register(catalogId, run);
        channel = new CrawlTaskChannel(cluster.node(0).cluster(), registry,
                new ClusterProperties().getDispatch());
        channel.start();
    }

    @AfterEach
    void tearDown() {
        channel.stop();
    }

    @Test
    @DisplayName("a url is counted the moment it is dispatched, before anybody can finish it")
    void dispatchCountsFirst() throws Exception {
        ClusterCrawlCoordinator coordinator = coordinator(false);

        coordinator.dispatch(task("https://example.com/a"));

        assertThat(run.getGlobalStateManager().getDashboard().getTotalUrlCount()).isEqualTo(1);
        assertThat(coordinator.dispatchedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("alone, every url still gets here -- delivered locally or kept outright")
    void aloneItKeepsTheWork() throws Exception {
        ClusterCrawlCoordinator coordinator = coordinator(false);

        for (int i = 0; i < 5; i++) {
            coordinator.dispatch(task("https://example.com/" + i));
        }

        // Either the balancer chose this node and spreader delivered locally, or there was nobody
        // to choose and the coordinator queued it directly. Both end in this frontier.
        //
        // At least, not exactly: delivery is at least once even to this node, so a url can land
        // twice. It costs a wasted fetch and nothing else, because every id downstream is derived
        // from the url and the second pass overwrites the first.
        // Distinct, not a count: a url delivered twice makes five arrivals out of four urls, and
        // waiting on the count then stops one short and blames the assertion below for it.
        TestCluster.await(() -> Set.copyOf(run.frontier().accepted()).size() >= 5, 10_000L,
                () -> "only " + Set.copyOf(run.frontier().accepted()).size() + " of 5: sent="
                        + channel.sentCount() + " received=" + channel.receivedCount()
                        + " kept=" + coordinator.keptLocallyCount() + " staged="
                        + channel.stagingDepth() + " orphaned=" + channel.orphanedCount());
        assertThat(run.frontier().accepted()).contains("https://example.com/0",
                "https://example.com/4");
    }

    @Test
    void handlingIsCountedSeparately() throws Exception {
        ClusterCrawlCoordinator coordinator = coordinator(false);
        CrawlTask task = task("https://example.com/a");

        coordinator.dispatch(task);
        coordinator.afterHandled(task);

        assertThat(run.getGlobalStateManager().getDashboard().getHandledUrlCount()).isEqualTo(1);
    }

    @Test
    void theLeaderIsTheOneThatPublishes() {
        assertThat(coordinator(true).shouldPublish()).isTrue();
        assertThat(coordinator(false).shouldPublish()).isFalse();
    }

    @Test
    void closingReleasesTheRun() {
        AtomicBoolean released = new AtomicBoolean();
        ClusterCrawlCoordinator coordinator = new ClusterCrawlCoordinator(channel,
                run.frontier(), run.getGlobalStateManager(), catalogId, () -> false, () -> "n1",
                () -> released.set(true), this::recordAnnouncement);

        coordinator.close();

        assertThat(released).isTrue();
        assertThat(coordinator.getCatalogId()).isEqualTo(catalogId);
        assertThat(coordinator.nodeId()).isEqualTo("n1");
        assertThat(coordinator.keptLocallyCount()).isZero();
    }

    private ClusterCrawlCoordinator coordinator(boolean leader) {
        return new ClusterCrawlCoordinator(channel, run.frontier(), run.getGlobalStateManager(),
                catalogId, () -> leader, () -> "n1", () -> {}, this::recordAnnouncement);
    }

    @Test
    @DisplayName("the end of a run goes to the cluster rather than being published here")
    void announcesTheEndOfTheRun() {
        ClusterCrawlCoordinator coordinator = coordinator(true);

        // true means the cluster took it, which is what tells the launcher not to publish the
        // event itself -- every node will publish it on hearing the announcement
        assertThat(coordinator.announceCompleted(catalogId, 2, "the site is exhausted", false))
                .isTrue();
        assertThat(announced).containsExactly(catalogId + " v2 the site is exhausted");
    }

    /** Stands in for the control channel: what a real one does is covered by CrawlClusterTest. */
    private boolean recordAnnouncement(String catalogId, int version, String reason,
            boolean interrupted) {
        announced.add(catalogId + " v" + version + " " + reason + (interrupted ? " (cut short)" : ""));
        return true;
    }

    private CrawlTask task(String url) {
        CrawlTask task = CrawlTask.seed(catalogId, CrawlTask.ACTION_CRAWL, "https://example.com",
                url, "default", "UTF-8", 0);
        // a timestamp of zero would skip the rolling average, and this exercises that it does not
        task.setTimestamp(System.currentTimeMillis());
        return task;
    }

    /** Kept so the counting types are named where somebody reading the test can see them. */
    @Test
    void theTwoCountersAreTheOnesCompletionCompares() {
        assertThat(CountingType.TOTAL_URL_COUNT.getRepr()).isEqualTo("totalUrlCount");
        assertThat(CountingType.HANDLED_URL_COUNT.getRepr()).isEqualTo("handledUrlCount");
    }

}
