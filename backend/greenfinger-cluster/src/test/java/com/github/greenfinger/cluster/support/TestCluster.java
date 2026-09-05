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

package com.github.greenfinger.cluster.support;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import com.chaconneai.openspreader.cache.CacheOptions;
import com.chaconneai.openspreader.cache.CacheService;
import com.chaconneai.openspreader.cache.EvictionPolicy;
import com.chaconneai.openspreader.cache.MultiProcessingCache;
import com.chaconneai.openspreader.cache.ProcessingCache;
import com.chaconneai.openspreader.concurrent.ExecutorServiceHolder;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.GossipConfig;
import com.chaconneai.spreader.transport.TransportProvider;
import com.chaconneai.spreader.transport.TransportType;

/**
 * Several real nodes in one jvm.
 *
 * <p>
 * Real, not simulated: actual sockets, actual gossip, actual leader election. The alternative --
 * a fake {@code GossipCluster} that delivers messages by calling the listener directly -- would
 * pass while every one of the things that actually went wrong in this module was broken. A
 * listener registered twice, a message echoing back to its sender, a node dispatching to a peer
 * that had not opened the crawl yet: none of them is visible without a network.
 *
 * <p>
 * Ports are taken from a range well away from both the operating system's ephemeral range and the
 * ports the http server uses, and the cluster port is a fresh free one per instance, so test
 * classes can run beside each other.
 * 
 * @Description: TestCluster
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
public final class TestCluster implements AutoCloseable {

    /** Everything shortened: these tests wait on membership converging, and a second is a age. */
    private static final long FAST_SUSPECT_MS = 800L;
    private static final long FAST_QUIET_MS = 300L;
    private static final long FAST_GOSSIP_MS = 200L;
    private static final long FAST_TAKEOVER_MS = 100L;

    private static final int WORK_PORT_MIN = 30_000;
    private static final int WORK_PORT_MAX = 40_000;

    private final String clusterName;
    private final int clusterPort;
    private final List<Node> nodes = new ArrayList<>();

    private TestCluster(String clusterName, int clusterPort) {
        this.clusterName = clusterName;
        this.clusterPort = clusterPort;
    }

    public static TestCluster start(int size) {
        TestCluster cluster = new TestCluster("gf-test-" + System.nanoTime(), freePort());
        for (int i = 0; i < size; i++) {
            cluster.add();
        }
        cluster.awaitReady();
        return cluster;
    }

    public Node add() {
        try {
            GossipConfig config = GossipConfig.builder().clusterName(clusterName)
                    .nodeName("greenfinger").bindHost("127.0.0.1").advertiseHost("127.0.0.1")
                    .clusterPort(clusterPort).transportType(TransportType.TCP)
                    .transportProvider(TransportProvider.NIO)
                    .workPortRange(WORK_PORT_MIN, WORK_PORT_MAX).ipAddresses("127.0.0.1")
                    .suspectTimeoutMs(FAST_SUSPECT_MS).leaderQuietPeriodMs(FAST_QUIET_MS)
                    .gossipIntervalMs(FAST_GOSSIP_MS).takeoverDelayMs(FAST_TAKEOVER_MS)
                    .probeTimeoutMs(300).connectTimeoutMs(300).build();
            GossipCluster gossip = GossipCluster.create(config);
            gossip.start();
            gossip.awaitJoin(10, TimeUnit.SECONDS);
            Node node = new Node(gossip);
            nodes.add(node);
            return node;
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Could not start a test node", e);
        }
    }

    /**
     * Waits until every node sees every other and they agree who the leader is.
     *
     * <p>
     * All three conditions, because a node whose view still holds only itself believes it is the
     * leader, and a test that started before the views converged would be asserting against a
     * cluster that briefly had two.
     */
    public void awaitReady() {
        await(() -> {
            if (nodes.stream().noneMatch(node -> node.cluster().isLeader())) {
                return false;
            }
            String leader = nodes.get(0).cluster().leader() == null ? null
                    : nodes.get(0).cluster().leader().id();
            return leader != null && nodes.stream()
                    .allMatch(node -> node.cluster().members().size() == nodes.size()
                            && node.cluster().leader() != null
                            && leader.equals(node.cluster().leader().id()));
        }, 20_000L, "the cluster did not converge");
    }

    public List<Node> nodes() {
        return List.copyOf(nodes);
    }

    public Node node(int index) {
        return nodes.get(index);
    }

    public Node leader() {
        return nodes.stream().filter(node -> node.cluster().isLeader()).findFirst()
                .orElseThrow(() -> new IllegalStateException("no leader"));
    }

    @Override
    public void close() {
        for (Node node : nodes) {
            node.close();
        }
        nodes.clear();
    }

    /** Polls rather than sleeps: these are eventual, and a fixed sleep is either slow or flaky. */
    public static void await(BooleanSupplier condition, long timeoutMs, String message) {
        await(condition, timeoutMs, () -> message);
    }

    /**
     * The message is built when it fails, so it can report the numbers as they were at that
     * moment. A fixed string on an eventual assertion says only that it did not happen.
     */
    public static void await(BooleanSupplier condition, long timeoutMs,
            java.util.function.Supplier<String> message) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(25L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("Timed out after " + timeoutMs + "ms: " + message.get());
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("no free port", e);
        }
    }

    /**
     * One node: its cluster membership and its share of the replicated cache.
     * 
     * @Description: Node
     * @Author: Fred Feng
     * @Date: 02/09/2026
     * @Version 2.0.0
     */
    public static final class Node implements AutoCloseable {

        private final GossipCluster cluster;
        private final ExecutorServiceHolder executors;
        private final CacheService cacheService;
        private final ProcessingCache cache;

        private Node(GossipCluster cluster) {
            this.cluster = cluster;
            this.executors = new ExecutorServiceHolder(4, 2, 4, 1024, 2);
            this.executors.afterPropertiesSet();
            CacheOptions options = new CacheOptions("greenfinger", 5_000L, 20L, 500L, 300L,
                    10_000L, 0L, 1024 * 1024, 512, 0, 100_000, 4, 100_000L, -1L,
                    EvictionPolicy.LRU, 5, 1_000, 300L, 1, 10_000);
            this.cacheService = new CacheService(cluster, options, executors, null);
            this.cacheService.start();
            this.cache = new MultiProcessingCache(cacheService);
        }

        public GossipCluster cluster() {
            return cluster;
        }

        public ProcessingCache cache() {
            return cache;
        }

        public String shortId() {
            return cluster.self().shortId();
        }

        @Override
        public void close() {
            try {
                cacheService.close();
            } catch (RuntimeException ignored) {
                // stopping a node that already failed must not hide the failure
            }
            try {
                cluster.close();
            } catch (Exception ignored) {
                // same
            }
            executors.destroy();
        }
    }

}
