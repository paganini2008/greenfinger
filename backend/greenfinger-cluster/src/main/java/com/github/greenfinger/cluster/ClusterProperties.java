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
         * How many times a frame is offered again before it is counted as lost. On top of the
         * transport's own retries, so three passes at two seconds covers a restart without
         * holding frames for a node that has gone.
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
         * How long to wait for the leader to perform one operation. Generous, because behind it
         * is a database write and, for a delete, a walk over an index and three directory trees.
         */
        private long timeoutMs = 30_000L;

        /**
         * How many times an operation is offered again when the leader could not be reached or
         * has moved. Not for one the leader refused on its own terms: the same question would
         * get the same answer.
         */
        private int maxAttempts = 3;

        /**
         * How often a node checks its catalog table against the leader's, besides on every
         * membership change. A backstop for a lost broadcast, not the mechanism.
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
         * How long increments accumulate before one write carries the total. A follower's cache
         * write is a round trip to the leader, which a fast crawl would saturate; batching costs
         * a dashboard half a second of lag.
         */
        private long flushIntervalMs = 500L;
    }

}
