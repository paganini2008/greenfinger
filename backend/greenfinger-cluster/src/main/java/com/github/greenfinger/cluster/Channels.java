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

/**
 * The channels this application talks on.
 *
 * <p>
 * A channel is a subscription filter: a listener registered on one never sees traffic from
 * another. Splitting by purpose rather than sharing one matters here because the traffic shapes
 * are opposite. A crawl task is unicast to exactly one node and must never be handled twice; a
 * replication record is multicast to every node and must reach all of them; a control message is a
 * handful of bytes that has to be acted on immediately rather than queued behind ten thousand
 * urls.
 *
 * <p>
 * One shared channel would put all three in the same inbound buffer, where a burst of urls delays
 * the stop signal that is trying to end that very burst.
 * 
 * @Description: Channels
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
public final class Channels {

    /** One url, unicast to whichever node the balancer picks. The recursive call itself. */
    public static final String CRAWL = "greenfinger.crawl";

    /** Rows written on one node, multicast to the others when the database is a local file. */
    public static final String RECORD = "greenfinger.record";

    /** Url and content fingerprints, so every node's dedup filter agrees. */
    public static final String ROCKSDB = "greenfinger.rocksdb";

    /** Page and image bytes, when the blob store is a local directory rather than MinIO. */
    public static final String BLOB = "greenfinger.blob";

    /**
     * Index documents and vectors, when the index is the embedded one rather than a server every
     * node can reach.
     */
    public static final String SEARCH = "greenfinger.search";

    /** Start, stop, finished. Small, rare, and acted on before anything else. */
    public static final String CONTROL = "greenfinger.control";

    private Channels() {}

}
