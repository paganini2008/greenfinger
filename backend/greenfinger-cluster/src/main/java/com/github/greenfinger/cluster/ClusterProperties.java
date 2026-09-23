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

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * What the crawl adds on top of {@code spring.spreader.*}, which configures the cluster itself.
 * 
 * @Description: ClusterProperties
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@Getter
@Setter
@ToString
@ConfigurationProperties(prefix = "greenfinger.cluster")
public class ClusterProperties {

    /**
     * Off is for a test that wants the engine without a network, never for a deployment: there is
     * no standalone edition, and one process is a cluster of one.
     */
    private boolean enabled = true;

    private Dispatch dispatch = new Dispatch();
    private Replication replication = new Replication();
    private Counters counters = new Counters();
    private Leader leader = new Leader();

    /**
     * 
     * @Description: Dispatch
     * @Author: Fred Feng
     * @Date: 02/09/2026
     * @Version 2.0.0
     */
    @Getter
    @Setter
    @ToString
    public static class Dispatch {

        /**
         * Inbound urls held while the consumers catch up. Rounded up to a power of two.
         *
         * <p>
         * Generous on purpose: one listing page can yield a hundred urls at once, and every url
         * that does not fit is one this node has to absorb into its own frontier instead.
         */
        private int bufferCapacity = 16384;

        /**
         * Threads taking urls off the buffer and onto the frontier. More than one is safe here --
         * urls have no order between them -- and the work is a single durable write.
         */
        private int consumers = 2;
    }

    /**
     * 
     * @Description: Replication
     * @Author: Fred Feng
     * @Date: 02/09/2026
     * @Version 2.0.0
     */
    @Getter
    @Setter
    @ToString
    public static class Replication {

        /** Inbound replication records held while the single consumer applies them. */
        private int bufferCapacity = 8192;

        /**
         * How many rows or blobs are packed into one datagram. Replication is a stream of small
         * writes and one message each would spend more time in framing than in the write.
         */
        private int batchSize = 64;

        /** Longest a partly filled batch waits before it is sent anyway. */
        private long flushIntervalMs = 200L;

        /**
         * How often frames that did not reach every node are offered again.
         *
         * <p>
         * Further apart than the flush on purpose. A member that did not take a frame is
         * restarting, busy or briefly unreachable, and asking it again a fifth of a second later
         * asks the same question before anything can have changed.
         */
        private long retryIntervalMs = 2000L;

        /**
         * How many times a frame is offered again before it is given up on and counted as lost.
         *
         * <p>
         * This sits on top of the transport's own acknowledgement and retry -- spreader has
         * already tried {@code payloadRetries} times by the moment a short delivery is reported
         * here. Three passes at two seconds is therefore about six seconds of a node being
         * unreachable, which covers a restart but does not hold a frame for a node that has gone.
         */
        private int maxRetries = 3;

        /**
         * The most frames held for retry at once. Reached only when a node is unreachable for
         * long enough that the writes pile up, and the point of the cap is that replication must
         * not be the thing that fills this node's heap.
         */
        private int maxPendingFrames = 1024;
    }


    /**
     * Where the administrative writes go, and what to do when the answer does not come back.
     * 
     * @Description: Leader
     * @Author: Fred Feng
     * @Date: 22/09/2026
     * @Version 2.0.0
     */
    @Getter
    @Setter
    @ToString
    public static class Leader {

        /**
         * How long to wait for the leader to perform one operation and answer.
         *
         * <p>
         * Generous, because what is behind it is a database write and, for a delete, a walk over
         * an index and three directory trees. Short enough that a node which has stopped
         * answering does not hold the caller for ever: after this the leader is read again and
         * the operation is offered to whoever holds the port now.
         */
        private long timeoutMs = 30_000L;

        /**
         * How many times an operation is offered again when the leader could not be reached, did
         * not answer, or replied that it is no longer the leader.
         *
         * <p>
         * Not for an operation the leader refused on its own terms -- a name already taken, a
         * version being crawled. Asking the same question again would get the same answer, and
         * the caller wants the answer rather than the delay.
         */
        private int maxAttempts = 3;

        /**
         * How often a node checks its catalog table against the leader's, on top of doing it
         * whenever the membership or the leadership changes.
         *
         * <p>
         * A backstop rather than the mechanism: a write reaches the other nodes as it happens, and
         * this is for the one whose broadcast was lost. A minute is soon enough for a stale row to
         * be a curiosity rather than an incident, and one small request per node per minute is
         * nothing.
         */
        private long catchUpIntervalMs = 60_000L;
    }

    /**
     * 
     * @Description: Counters
     * @Author: Fred Feng
     * @Date: 02/09/2026
     * @Version 2.0.0
     */
    @Getter
    @Setter
    @ToString
    public static class Counters {

        /**
         * How long increments accumulate locally before one write carries the total.
         *
         * <p>
         * A cache write from a follower is a round trip to the leader -- measured at roughly two
         * thousand a second, which a fast crawl would saturate on its own with two counters per
         * page. Batching makes the cost a fixed handful of writes per second per node, whatever
         * the crawl rate, at the price of a dashboard that is half a second behind. It is a
         * dashboard.
         */
        private long flushIntervalMs = 500L;
    }

}
