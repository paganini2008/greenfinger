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

package com.github.greenfinger.cluster.replication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.github.greenfinger.cluster.Channels;
import com.github.greenfinger.cluster.ClusterProperties;

/**
 * What happens when a write does not reach everybody.
 *
 * <p>
 * The transport acknowledges and retries on its own and then gives up, and it says so by
 * returning how many members took the frame. That number used to be discarded, which is what made
 * a write nobody received look exactly like one everybody received: a delete that reached one node
 * of three, a search version published on the node that published it and nowhere else. These are
 * the cases that number now drives.
 *
 * <p>
 * The cluster is a double here rather than real nodes, because what is under test is the
 * behaviour on a short delivery, and a real cluster on one machine delivers everything.
 *
 * @Description: ReplicationDeliveryTest
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
class ReplicationDeliveryTest {

    private GossipCluster cluster;
    private AtomicInteger delivered;
    private AtomicInteger sends;

    @BeforeEach
    void setUp() {
        cluster = mock(GossipCluster.class);
        delivered = new AtomicInteger(0);
        sends = new AtomicInteger(0);
        Node self = mock(Node.class);
        Node other = mock(Node.class);
        Node third = mock(Node.class);
        // the crawlers, which is what the channel counts: a terminal in the same cluster is a
        // member and is not one of these
        when(self.name()).thenReturn("greenfinger");
        when(cluster.self()).thenReturn(self);
        when(cluster.members()).thenReturn(List.of(self, other, third));
        when(cluster.membersOf("greenfinger")).thenReturn(List.of(self, other, third));
        when(cluster.multicastOn(eq(Channels.RECORD), any(), any(byte[].class), eq(false)))
                .thenAnswer(invocation -> {
                    sends.incrementAndGet();
                    return delivered.get();
                });
    }

    private ReplicationChannel channel() {
        ClusterProperties.Replication config = new ClusterProperties.Replication();
        config.setBatchSize(1);
        config.setMaxRetries(2);
        return new ReplicationChannel(cluster, Channels.RECORD, "gf-test", config, entry -> {});
    }

    private static ReplicationBatch.Entry aRow(String key) {
        return ReplicationBatch.Entry.of(ReplicatedCatalogStore.OP_CATALOG, key, key,
                "{}".getBytes());
    }

    @Test
    @DisplayName("a write that reached everybody is not held")
    void keepsNothingWhenItLands() {
        delivered.set(2);
        ReplicationChannel channel = channel();

        channel.replicate(aRow("c1"));

        assertThat(channel.sentCount()).isEqualTo(1);
        assertThat(channel.pendingCount()).isZero();
        assertThat(channel.underDeliveredCount()).isZero();
        assertThat(channel.lostCount()).isZero();
    }

    @Test
    @DisplayName("a write that reached nobody is held, and counted per node that missed it")
    void holdsAShortDelivery() {
        delivered.set(0);
        ReplicationChannel channel = channel();

        channel.replicate(aRow("c1"));

        assertThat(channel.pendingCount()).isEqualTo(1);
        // one row, two nodes that have not got it
        assertThat(channel.underDeliveredCount()).isEqualTo(2);
        assertThat(channel.lostCount()).isZero();
    }

    @Test
    @DisplayName("a held write is offered again, and let go once it lands")
    void retriesUntilItLands() {
        delivered.set(1);
        ReplicationChannel channel = channel();
        channel.replicate(aRow("c1"));
        assertThat(channel.pendingCount()).isEqualTo(1);

        delivered.set(2);
        channel.retryPending();

        assertThat(channel.pendingCount()).isZero();
        assertThat(channel.lostCount()).isZero();
        assertThat(sends.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("a write that keeps falling short is given up on, loudly and once")
    void givesUpAfterTheRetries() {
        delivered.set(0);
        ReplicationChannel channel = channel();
        channel.replicate(aRow("c1"));

        channel.retryPending();
        channel.retryPending();
        channel.retryPending();

        assertThat(channel.lostCount()).isEqualTo(1);
        assertThat(channel.pendingCount()).isZero();
        // the first send plus two retries, and then no more
        assertThat(sends.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("alone, there is nobody to be short of")
    void queuesNothingAlone() {
        Node self = mock(Node.class);
        when(cluster.members()).thenReturn(List.of(self));
        when(cluster.membersOf("greenfinger")).thenReturn(List.of(self));
        ReplicationChannel channel = channel();

        channel.replicate(aRow("c1"));

        assertThat(channel.sentCount()).isZero();
        assertThat(channel.pendingCount()).isZero();
    }

    @Test
    @DisplayName("nothing is offered again while this node is alone")
    void doesNotRetryAlone() {
        delivered.set(0);
        ReplicationChannel channel = channel();
        channel.replicate(aRow("c1"));
        Node self = mock(Node.class);
        when(cluster.members()).thenReturn(List.of(self));
        when(cluster.membersOf("greenfinger")).thenReturn(List.of(self));

        channel.retryPending();

        assertThat(channel.pendingCount()).isEqualTo(1);
        assertThat(sends.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("the retry queue has a ceiling: replication must not fill the heap")
    void capsWhatItHolds() {
        delivered.set(0);
        ClusterProperties.Replication config = new ClusterProperties.Replication();
        config.setBatchSize(1);
        config.setMaxRetries(5);
        config.setMaxPendingFrames(2);
        ReplicationChannel channel =
                new ReplicationChannel(cluster, Channels.RECORD, "gf-test", config, entry -> {});

        channel.replicate(aRow("c1"));
        channel.replicate(aRow("c2"));
        channel.replicate(aRow("c3"));

        assertThat(channel.pendingCount()).isEqualTo(2);
        assertThat(channel.lostCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a member leaving between the read and the send is not a short delivery")
    void toleratesAMemberLeaving() {
        ReplicationChannel channel = channel();
        when(cluster.multicastOn(anyString(), any(), any(byte[].class), eq(false)))
                .thenAnswer(invocation -> {
                    sends.incrementAndGet();
                    // more than expected, which is what a stale membership read looks like
                    return 5;
                });

        channel.replicate(aRow("c1"));

        assertThat(channel.pendingCount()).isZero();
        assertThat(channel.underDeliveredCount()).isZero();
    }

}
