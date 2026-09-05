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
