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
import com.github.greenfinger.cluster.leader.CatalogCatchUp;
import com.github.greenfinger.cluster.support.FakeCatalogStore;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.catalog.CatalogDetailsNotFoundException;
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
import com.github.greenfinger.core.model.DeleteLayer;
import com.github.greenfinger.service.CrawlerLauncher;
import com.github.greenfinger.service.DeletionService;
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

    /** Set by the case for the race: the first join refuses, as it does when the row is late. */
    private final java.util.concurrent.atomic.AtomicBoolean refuseFirstJoin =
            new java.util.concurrent.atomic.AtomicBoolean();

    /** How many times a node asked the leader for its table before joining. */
    private final java.util.concurrent.atomic.AtomicInteger catchUps =
            new java.util.concurrent.atomic.AtomicInteger();

    /** The row the announcement carries, when the case under test puts one there. */
    private final java.util.concurrent.atomic.AtomicReference<Catalog> carriedCatalog =
            new java.util.concurrent.atomic.AtomicReference<>();

    /** What a node wrote to its own table. */
    private final List<Catalog> saved = new CopyOnWriteArrayList<>();

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
        refuseFirstJoin.set(false);
        catchUps.set(0);
        carriedCatalog.set(null);
        saved.clear();
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
    @DisplayName("the announcement carries the catalog, so joining costs no round trip")
    void joinsOnTheCarriedCatalog() throws Exception {
        Node a = node(0);
        Node b = node(1);
        try {
            refuseFirstJoin.set(true);
            carriedCatalog.set(aCatalog("cat-carried"));
            TestRun run = new TestRun("cat-carried", "books");
            a.registry.register("cat-carried", run);
            a.crawlCluster.create(new CrawlRun(run, "crawl", false, true));

            TestCluster.await(() -> joined.contains("cat-carried"), 10_000L,
                    "the other node never opened its half");
            // the row was written from the message rather than fetched: asking the leader is a
            // channel with a thirty second timeout, and a small crawl is over before it answers
            assertThat(saved).extracting(Catalog::getId).contains("cat-carried");
            assertThat(catchUps.get()).isZero();
        } finally {
            a.close();
            b.close();
        }
    }

    @Test
    @DisplayName("a crawl of a catalog this node has not got yet is asked for, not given up on")
    void joinsAfterAskingTheLeaderForTheCatalog() throws Exception {
        Node a = node(0);
        Node b = node(1);
        try {
            // the announcement travels on the control channel, small and immediate; the row
            // travels on the replication channel and waits for a flush, so the other node can
            // hear about a catalog it has never seen
            refuseFirstJoin.set(true);
            TestRun run = new TestRun("cat-race", "books");
            a.registry.register("cat-race", run);
            a.crawlCluster.create(new CrawlRun(run, "crawl", false, true));

            TestCluster.await(() -> joined.contains("cat-race"), 10_000L,
                    "the other node gave up instead of asking for the catalog");
            assertThat(catchUps.get()).isEqualTo(1);
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

    @Test
    @DisplayName("a purge goes to every node but the one that ran the delete")
    void purgeReachesTheOtherNodes() throws Exception {
        List<String> purgedHere = new CopyOnWriteArrayList<>();
        Node a = nodeAt(0, new CopyOnWriteArrayList<>(), purgedHere);
        Node b = nodeAt(1, new CopyOnWriteArrayList<>(), purgedHere);
        try {
            a.crawlCluster.announcePurge("cat-7", 3, "db,index", false);

            // b removes its own index documents and RocksDB directories; a did its own as part of
            // the delete that prompted this, so it steps over the echo
            TestCluster.await(() -> purgedHere.size() == 1, 10_000L,
                    "the other node was never asked to remove its own copy");
            assertThat(purgedHere).containsExactly("cat-7@v3 [db, index]");
        } finally {
            a.close();
            b.close();
        }
    }

    @Test
    @DisplayName("a purge of the whole catalog says so, and says whether the index is dropped")
    void purgeOfAWholeCatalog() throws Exception {
        List<String> purgedHere = new CopyOnWriteArrayList<>();
        Node a = nodeAt(0, new CopyOnWriteArrayList<>(), purgedHere);
        Node b = nodeAt(1, new CopyOnWriteArrayList<>(), purgedHere);
        try {
            a.crawlCluster.announcePurge("cat-8", null, "db,file,index,vector", true);

            TestCluster.await(() -> purgedHere.size() == 1, 10_000L,
                    "the other node was never asked to remove the whole catalog");
            assertThat(purgedHere)
                    .containsExactly("cat-8@all [db, file, index, vector] drop");
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
        return nodeAt(index, restoredOn, new CopyOnWriteArrayList<>());
    }

    private Node nodeAt(int index, List<String> restoredOn, List<String> purgedHere)
            throws Exception {
        ClusterProperties properties = new ClusterProperties();
        properties.getCounters().setFlushIntervalMs(50L);

        CrawlRegistry registry = new CrawlRegistry();
        CrawlTaskChannel channel = new CrawlTaskChannel(cluster.node(index).cluster(), registry,
                properties.getDispatch());
        CrawlCluster crawlCluster = new CrawlCluster(cluster.node(index).cluster(), channel,
                registry, launcherThatRecords(), replayThatRecords(restoredOn),
                deletionThatRecords(purgedHere), catchUpThatRecords(),
                catalogStoreThatRecords(index == 0),
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
    /**
     * A catch-up that only records that it was asked. What it would really do -- take the
     * leader's table -- is {@code CatalogCatchUpTest}'s subject.
     */
    private ObjectProvider<CatalogCatchUp> catchUpThatRecords() {
        CatalogCatchUp catchUp = new CatalogCatchUp(null, null, null, Long.MAX_VALUE) {

            @Override
            public long catchUp() {
                catchUps.incrementAndGet();
                return 1L;
            }
        };
        return new ObjectProvider<>() {

            @Override
            public CatalogCatchUp getObject() {
                return catchUp;
            }

            @Override
            public CatalogCatchUp getIfAvailable() {
                return catchUp;
            }
        };
    }

    /**
     * A table that answers with whatever the case put in {@code carriedCatalog} and records what
     * was written to it.
     */
    /**
     * @param holdsTheRow true for the node that starts the crawl, which is where the row already
     *                    is. Everywhere else the table is empty until something writes to it --
     *                    which is the case under test.
     */
    private ObjectProvider<CatalogStore> catalogStoreThatRecords(boolean holdsTheRow) {
        // empty until something is written to it, as a table is: the case under test is a row
        // that is not here yet arriving with the announcement
        CatalogStore store = new FakeCatalogStore("test") {

            @Override
            public java.util.Optional<Catalog> findById(String id) {
                java.util.Optional<Catalog> mine =
                        saved.stream().filter(c -> id.equals(c.getId())).findFirst();
                if (mine.isPresent() || !holdsTheRow) {
                    return mine;
                }
                Catalog carried = carriedCatalog.get();
                return carried != null && id.equals(carried.getId())
                        ? java.util.Optional.of(carried)
                        : java.util.Optional.empty();
            }

            @Override
            public Catalog save(Catalog catalog) {
                saved.add(catalog);
                return catalog;
            }
        };
        return new ObjectProvider<>() {

            @Override
            public CatalogStore getObject() {
                return store;
            }

            @Override
            public CatalogStore getIfAvailable() {
                return store;
            }
        };
    }

    private static Catalog aCatalog(String id) {
        Catalog catalog = new Catalog();
        catalog.setId(id);
        catalog.setName(id);
        catalog.setUrl("https://books.toscrape.com");
        return catalog;
    }

    /** Nothing to provide: the catch-up is absent in this slice, as it is on a lone node. */
    private <T> ObjectProvider<T> none() {
        return new ObjectProvider<T>() {

            @Override
            public T getObject() {
                throw new UnsupportedOperationException();
            }

            @Override
            public T getIfAvailable() {
                return null;
            }
        };
    }

    private ObjectProvider<CrawlerLauncher> launcherThatRecords() {
        CrawlerLauncher launcher = new CrawlerLauncher(null, null, null, null, null, null, null,
                null, null, null, null, null, null) {

            @Override
            public CrawlerEngine.Result join(String catalogId, String action, boolean refresh) {
                if (refuseFirstJoin.compareAndSet(true, false)) {
                    throw new CatalogDetailsNotFoundException("No catalog with id: " + catalogId);
                }
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
     * A deletion service that only records what it was asked to remove from this node.
     *
     * <p>
     * What is under test is the instruction: that it reaches every node but the one that sent it,
     * and that the catalog, the version and the layers survive the trip. Actually emptying a
     * Lucene index and three RocksDB directories is {@code DeletionService}'s own test.
     */
    private ObjectProvider<DeletionService> deletionThatRecords(List<String> purgedHere) {
        DeletionService deletionService =
                new DeletionService(null, null, null, null, null, null, null, null) {

                    @Override
                    public long purgeNodeLocal(String catalogId, Integer version,
                            Set<DeleteLayer> layers, boolean dropIndex) {
                        purgedHere.add(catalogId + "@"
                                + (version != null ? "v" + version : "all") + " "
                                + layers.stream().map(DeleteLayer::getRepr).sorted().toList()
                                + (dropIndex ? " drop" : ""));
                        return 0L;
                    }
                };
        return new ObjectProvider<>() {

            @Override
            public DeletionService getObject() {
                return deletionService;
            }

            @Override
            public DeletionService getObject(Object... args) {
                return deletionService;
            }

            @Override
            public DeletionService getIfAvailable() {
                return deletionService;
            }

            @Override
            public DeletionService getIfUnique() {
                return deletionService;
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
