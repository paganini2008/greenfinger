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
import static org.assertj.core.api.Assertions.assertThatCode;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import com.github.greenfinger.cluster.channel.CrawlTaskChannel;
import com.github.greenfinger.cluster.state.ClusterGlobalStateManager;
import com.github.greenfinger.cluster.support.TestCluster;
import com.github.greenfinger.cluster.support.TestRun;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.engine.CrawlCoordinator;
import com.github.greenfinger.core.engine.CrawlRegistry;
import com.github.greenfinger.core.engine.WebCrawlerCompletionEvent;
import com.github.greenfinger.core.engine.CrawlRun;
import com.github.greenfinger.core.engine.CrawlerEngine;
import java.util.Set;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.service.CrawlerLauncher;
import com.github.greenfinger.service.ReplayService;

/**
 * Who joins a crawl, who decides it is over, and who does the things that must happen once.
 *
 * <p>
 * Two real nodes, because all three answers are about what one node does when another one speaks.
 * 
 * @Description: CrawlClusterTest
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
class CrawlClusterTest {

    /** Every completion event any node in the fixture published, in arrival order. */
    private final List<WebCrawlerCompletionEvent> completions = new CopyOnWriteArrayList<>();

    /** Turned on by the case that checks a listener cannot take the announcement down with it. */
    private volatile boolean listenersThrow;

    /** One for the class: see the note in ClusterMessagingTest. */
    private static TestCluster cluster;

    private final List<String> joined = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void startCluster() {
        cluster = TestCluster.start(2);
    }

    @AfterAll
    static void stopCluster() {
        cluster.close();
    }

    @BeforeEach
    void setUp() {
        joined.clear();
    }

    @Test
    @DisplayName("the node a crawl starts on tells the others, and they open their half of it")
    void othersJoin() throws Exception {
        Node a = node(0);
        Node b = node(1);
        try {
            TestRun run = new TestRun("cat-1", "books");
            a.registry.register("cat-1", run);
            a.crawlCluster.create(new CrawlRun(run, "crawl", false, true));

            TestCluster.await(() -> joined.contains("cat-1"), 10_000L,
                    "the other node was never told");
        } finally {
            a.close();
            b.close();
        }
    }

    @Test
    @DisplayName("the end of a run reaches every node, the one that announced it included")
    void completionReachesEveryone() throws Exception {
        Node a = node(0);
        Node b = node(1);
        try {
            a.crawlCluster.announceCompleted("cat-done", 3, "the site is exhausted", false);

            // two nodes, one announcement, one event each: the announcement comes back to its
            // sender like any other, which is what lets it be sent from one place only
            TestCluster.await(() -> completions.size() >= 2, 10_000L,
                    () -> "only " + completions.size() + " node(s) heard it");
            assertThat(completions).allSatisfy(event -> {
                assertThat(event.getCatalogId()).isEqualTo("cat-done");
                assertThat(event.getVersion()).isEqualTo(3);
                assertThat(event.getReason()).isEqualTo("the site is exhausted");
                assertThat(event.isInterrupted()).isFalse();
            });
        } finally {
            a.close();
            b.close();
        }
    }

    @Test
    @DisplayName("a run that was cut short says so, so a listener can tell the two apart")
    void completionCarriesTheInterruption() throws Exception {
        Node a = node(0);
        try {
            a.crawlCluster.announceCompleted("cat-stopped", 0, "interrupted by request", true);

            TestCluster.await(() -> !completions.isEmpty(), 10_000L, "nothing was published");
            assertThat(completions.get(0).isInterrupted()).isTrue();
            assertThat(completions.get(0).getReason()).isEqualTo("interrupted by request");
        } finally {
            a.close();
        }
    }

    @Test
    @DisplayName("a listener that throws is somebody else's problem, not the crawl's")
    void aFailingListenerDoesNotPropagate() throws Exception {
        Node a = node(0);
        listenersThrow = true;
        try {
            // by the time this is sent the run is finished and its version published, so there is
            // nothing here for a listener to break -- and it must not look as though there were
            assertThatCode(() -> a.crawlCluster.announceCompleted("cat-boom", 1, "done", false))
                    .doesNotThrowAnyException();
        } finally {
            listenersThrow = false;
            a.close();
        }
    }

    @Test
    @DisplayName("a node that is already running the catalog does not open it a second time")
    void theInitiatorDoesNotJoinItself() throws Exception {
        Node a = node(0);
        try {
            TestRun run = new TestRun("cat-1", "books");
            a.registry.register("cat-1", run);
            a.crawlCluster.create(new CrawlRun(run, "crawl", false, true));

            Thread.sleep(500L);
            assertThat(joined).isEmpty();
        } finally {
            a.close();
        }
    }

    @Test
    @DisplayName("a node that joined says nothing, so a cluster of four does not announce four times")
    void joiningIsSilent() throws Exception {
        Node a = node(0);
        Node b = node(1);
        try {
            TestRun run = new TestRun("cat-2", "books");
            b.registry.register("cat-2", run);
            b.crawlCluster.create(new CrawlRun(run, "crawl", false, false));

            Thread.sleep(500L);
            assertThat(joined).isEmpty();
        } finally {
            a.close();
            b.close();
        }
    }

    @Test
    void forgettingARunReleasesIt() throws Exception {
        Node a = node(0);
        try {
            TestRun run = new TestRun("cat-5", "books");
            a.registry.register("cat-5", run);
            CrawlCoordinator coordinator =
                    a.crawlCluster.create(new CrawlRun(run, "crawl", false, false));

            // close is what the engine calls in its finally
            coordinator.close();
            a.crawlCluster.forget("cat-5");
        } finally {
            a.close();
        }
    }

    @Test
    @DisplayName("a file restore goes to every node: each one repairs its own copy, once")
    void restoringFilesAsksEveryNodeButTheOneThatAsked() throws Exception {
        List<String> restoredOn = new CopyOnWriteArrayList<>();
        Node a = nodeAt(0, restoredOn);
        Node b = nodeAt(1, restoredOn);
        try {
            a.crawlCluster.announceRestoreFiles("cat-6", 2);

            // b repairs itself; a already did its own before announcing, so it ignores the echo
            TestCluster.await(() -> restoredOn.size() == 1, 10_000L,
                    "the other node was never asked to repair its files");
            assertThat(restoredOn).containsExactly("cat-6@2");
        } finally {
            a.close();
            b.close();
        }
    }

    // ---- fixtures -----------------------------------------------------------------------------

    private Node node(int index) throws Exception {
        return nodeAt(index, new CopyOnWriteArrayList<>());
    }

    private Node nodeAt(int index) throws Exception {
        return nodeAt(index, new CopyOnWriteArrayList<>());
    }

    private Node nodeAt(int index, List<String> restoredOn) throws Exception {
        ClusterProperties properties = new ClusterProperties();
        properties.getCounters().setFlushIntervalMs(50L);

        CrawlRegistry registry = new CrawlRegistry();
        CrawlTaskChannel channel = new CrawlTaskChannel(cluster.node(index).cluster(), registry,
                properties.getDispatch());
        CrawlCluster crawlCluster = new CrawlCluster(cluster.node(index).cluster(), channel,
                registry, launcherThatRecords(), replayThatRecords(restoredOn),
                event -> {
                    if (listenersThrow) {
                        throw new IllegalStateException("a listener of somebody else's");
                    }
                    completions.add((WebCrawlerCompletionEvent) event);
                });
        crawlCluster.afterPropertiesSet();
        return new Node(registry, crawlCluster, cluster.node(index).cache());
    }

    /**
     * A launcher that only records that it was asked. Opening a real run needs a database, a blob
     * store and an output channel, none of which this module owns -- what is under test is
     * whether the message arrives and who acts on it.
     */
    private ObjectProvider<CrawlerLauncher> launcherThatRecords() {
        CrawlerLauncher launcher = new CrawlerLauncher(null, null, null, null, null, null, null,
                null, null, null, null, null, null) {

            @Override
            public CrawlerEngine.Result join(String catalogId, String action, boolean refresh) {
                joined.add(catalogId);
                return null;
            }
        };
        return new ObjectProvider<>() {

            @Override
            public CrawlerLauncher getObject() {
                return launcher;
            }

            @Override
            public CrawlerLauncher getObject(Object... args) {
                return launcher;
            }

            @Override
            public CrawlerLauncher getIfAvailable() {
                return launcher;
            }

            @Override
            public CrawlerLauncher getIfUnique() {
                return launcher;
            }
        };
    }

    /**
     * A replay service that only records that it was asked to restore a version's files.
     */
    private ObjectProvider<ReplayService> replayThatRecords(List<String> restoredOn) {
        ReplayService replayService = new ReplayService(null, null, null, null, null) {

            @Override
            public long replaySlice(String catalogId, int version, Set<OutputType> layers,
                    int offset, int limit) {
                restoredOn.add(catalogId + "@" + version);
                return 0L;
            }
        };
        return new ObjectProvider<>() {

            @Override
            public ReplayService getObject() {
                return replayService;
            }

            @Override
            public ReplayService getObject(Object... args) {
                return replayService;
            }

            @Override
            public ReplayService getIfAvailable() {
                return replayService;
            }

            @Override
            public ReplayService getIfUnique() {
                return replayService;
            }
        };
    }

    /**
     * 
     * @Description: Node
     * @Author: Fred Feng
     * @Date: 02/09/2026
     * @Version 2.0.0
     */
    private record Node(CrawlRegistry registry, CrawlCluster crawlCluster,
            com.chaconneai.openspreader.cache.ProcessingCache cache) {

        void close() {
            try {
                crawlCluster.destroy();
            } catch (Exception ignored) {
                // a node that already failed must not have the failure hidden by its cleanup
            }
        }
    }

}
