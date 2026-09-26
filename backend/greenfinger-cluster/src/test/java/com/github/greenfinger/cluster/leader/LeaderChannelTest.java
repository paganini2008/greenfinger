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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.cluster.Channels;
import com.github.greenfinger.core.WebCrawlerException;

/**
 * Where an administrative write goes, and what happens when it cannot get there.
 *
 * <p>
 * The cluster is a double here: what is under test is the routing -- run it locally, forward it,
 * offer it again, give the caller the leader's refusal -- and on one machine every send succeeds,
 * which is the one case that needs no test.
 *
 * @Description: LeaderChannelTest
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
class LeaderChannelTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private GossipCluster cluster;
    private Node self;
    private Node other;
    private LeaderChannel channel;

    @BeforeEach
    void setUp() {
        cluster = mock(GossipCluster.class);
        self = mock(Node.class);
        other = mock(Node.class);
        when(self.id()).thenReturn("self");
        when(other.id()).thenReturn("other");
        when(other.label()).thenReturn("127.0.0.1:1234");
        when(cluster.self()).thenReturn(self);
        channel = new LeaderChannel(cluster, 200L, 3);
    }

    private void leaderIsSelf() {
        when(cluster.leader()).thenReturn(self);
        when(cluster.isLeader()).thenReturn(true);
    }

    private void leaderIsSomebodyElse() {
        when(cluster.leader()).thenReturn(other);
        when(cluster.isLeader()).thenReturn(false);
    }

    // ---- on the leader -------------------------------------------------------------------------

    @Test
    @DisplayName("on the leader it is a plain call: no network, no serialising")
    void runsLocallyOnTheLeader() {
        leaderIsSelf();
        channel.handle("double", Integer.class, value -> value * 2);

        assertThat(channel.onLeader("double", 21, Integer.class)).isEqualTo(42);
        verify(cluster, never()).unicastOn(anyString(), any(Node.class), any(byte[].class));
    }

    @Test
    @DisplayName("an operation that returns nothing returns nothing")
    void handlesAVoidOperation() {
        leaderIsSelf();
        AtomicInteger calls = new AtomicInteger();
        channel.handle("touch", String.class, id -> {
            calls.incrementAndGet();
            return null;
        });

        assertThat(channel.onLeader("touch", "c1", Void.class)).isNull();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("what the handler refuses is what the caller is told, without a retry")
    void passesTheHandlersRefusalOn() {
        leaderIsSelf();
        AtomicInteger attempts = new AtomicInteger();
        channel.handle("save", String.class, name -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("uk_catalog_name '" + name + "' is taken");
        });

        assertThatThrownBy(() -> channel.onLeader("save", "Alpha", String.class))
                .isInstanceOf(WebCrawlerException.class).hasMessageContaining("uk_catalog_name");
        // asking the same node the same question again would get the same answer
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("an operation this node has never heard of is refused, and says which")
    void refusesAnUnknownOperation() {
        leaderIsSelf();

        assertThatThrownBy(() -> channel.onLeader("whatever", null, String.class))
                .hasMessageContaining("whatever");
    }

    // ---- forwarding ----------------------------------------------------------------------------

    @Test
    @DisplayName("off the leader it is unicast, and the answer comes back")
    void forwardsToTheLeader() throws Exception {
        leaderIsSomebodyElse();
        answerEveryRequestWith(reply -> LeaderChannel.Message.answer(reply.id(), "\"done\""));

        assertThat(channel.onLeader("save", "Alpha", String.class)).isEqualTo("done");
    }

    @Test
    @DisplayName("a message that did not reach the node is offered again")
    void retriesWhenItDoesNotArrive() {
        leaderIsSomebodyElse();
        AtomicInteger sends = new AtomicInteger();
        when(cluster.unicastOn(eq(Channels.LEADER), eq(other), any(byte[].class)))
                .thenAnswer(invocation -> {
                    sends.incrementAndGet();
                    return false;
                });

        assertThatThrownBy(() -> channel.onLeader("save", "Alpha", String.class))
                .hasMessageContaining("did not reach");
        assertThat(sends.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("a node that is no longer the leader sends the caller back to read it again")
    void retriesWhenTheLeaderMoved() {
        leaderIsSomebodyElse();
        AtomicInteger sends = new AtomicInteger();
        answerEveryRequestWith(request -> {
            sends.incrementAndGet();
            return LeaderChannel.Message.refused(request.id(), "No longer the leader", true);
        });

        // the message is the leader's own words, passed through rather than restated
        assertThatThrownBy(() -> channel.onLeader("save", "Alpha", String.class))
                .hasMessageContaining("No longer the leader");
        assertThat(sends.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("no answer at all is a retry, not a hang")
    void retriesOnSilence() {
        leaderIsSomebodyElse();
        when(cluster.unicastOn(eq(Channels.LEADER), eq(other), any(byte[].class)))
                .thenReturn(true);

        assertThatThrownBy(() -> channel.onLeader("save", "Alpha", String.class))
                .hasMessageContaining("did not answer");
    }

    @Test
    @DisplayName("with no leader there is nowhere to send it, and it says so")
    void saysWhenThereIsNoLeader() {
        when(cluster.leader()).thenReturn(null);

        assertThatThrownBy(() -> channel.onLeader("save", "Alpha", String.class))
                .hasMessageContaining("no leader");
    }

    // ---- what arrives -------------------------------------------------------------------------

    @Test
    @DisplayName("a request that arrives is performed and answered")
    void performsWhatArrives() throws Exception {
        leaderIsSelf();
        channel.handle("double", Integer.class, value -> value * 2);
        when(cluster.unicastOn(eq(Channels.LEADER), eq(other), any(byte[].class)))
                .thenReturn(true);

        channel.onPayload(other, encode(LeaderChannel.Message.request(7L, "double", "21")));

        verify(cluster, times(1)).unicastOn(eq(Channels.LEADER), eq(other), any(byte[].class));
    }

    @Test
    @DisplayName("a node that is not the leader says so rather than performing it")
    void refusesWhatItShouldNotPerform() {
        leaderIsSomebodyElse();
        AtomicInteger calls = new AtomicInteger();
        channel.handle("double", Integer.class, value -> calls.incrementAndGet());

        LeaderChannel.Message reply =
                channel.perform(LeaderChannel.Message.request(1L, "double", "21"));

        assertThat(reply.notLeader()).isTrue();
        assertThat(calls.get()).isZero();
    }

    @Test
    @DisplayName("a terminal holding the port says 'ask somebody else', not 'unknown operation'")
    void aNodeWithNoHandlersIsNotALeader() {
        // the greenfinger-shell prompt registers nothing: it asks and never answers. Should it
        // have taken the cluster port before a crawler was up, a write arriving here has to be
        // retried elsewhere rather than failed outright
        leaderIsSelf();
        LeaderChannel terminal = new LeaderChannel(cluster, 200L, 3, false);

        LeaderChannel.Message reply =
                terminal.perform(LeaderChannel.Message.request(1L, "catalog.save", "{}"));

        assertThat(reply.notLeader()).isTrue();
        assertThat(reply.error()).contains("terminal");
    }

    @Test
    @DisplayName("an unreadable message is discarded rather than thrown")
    void discardsRubbish() {
        channel.onPayload(other, "not json".getBytes(StandardCharsets.UTF_8));

        verify(cluster, never()).unicastOn(anyString(), any(Node.class), any(byte[].class));
    }

    @Test
    @DisplayName("an answer nobody is waiting for is dropped, not an error")
    void dropsAnUnexpectedAnswer() throws Exception {
        channel.onPayload(other, encode(LeaderChannel.Message.answer(999L, "\"late\"")));
    }

    /**
     * Answers every request the moment it is sent, on the sending thread, which is close enough
     * to a node that replies instantly and keeps the test free of its own concurrency.
     */
    private void answerEveryRequestWith(
            java.util.function.Function<LeaderChannel.Message, LeaderChannel.Message> answer) {
        when(cluster.unicastOn(eq(Channels.LEADER), eq(other), any(byte[].class)))
                .thenAnswer(invocation -> {
                    byte[] sent = invocation.getArgument(2);
                    LeaderChannel.Message request = OBJECT_MAPPER.readValue(sent,
                            LeaderChannel.Message.class);
                    new Thread(() -> channel.onPayload(other, encode(answer.apply(request))))
                            .start();
                    return true;
                });
    }

    private static byte[] encode(LeaderChannel.Message message) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(message);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

}
