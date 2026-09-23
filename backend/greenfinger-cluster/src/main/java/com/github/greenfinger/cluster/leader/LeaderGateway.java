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

import java.util.function.Function;

/**
 * Where an administrative write goes: to the leader, wherever it was asked for.
 *
 * <h2>Why one writer</h2>
 * Everything behind this gateway is a store that gives each process its own copy -- the catalog
 * table on H2 or SQLite, the RocksDB directories, the local file tree, the embedded index and the
 * embedded vector store. Copies drift when two of them can be written at once, and the cheapest
 * way not to have to reconcile a drift is not to produce one: one node performs the write and the
 * others are told what it did.
 *
 * <p>
 * That single writer is what a whole class of machinery is <em>not</em> needed for. With
 * concurrent writers, "who has the newer row" has to be decided from the rows themselves, and a
 * deletion cannot be expressed at all without remembering it separately -- because a node that
 * missed a delete looks exactly like the node that created the row. With one writer, the leader's
 * table is simply the truth: a node that fell behind takes what the leader has, and anything the
 * leader does not have is gone. No write stamps to compare, no content hashes to break the tie,
 * no tombstones to keep.
 *
 * <h2>What does not come through here</h2>
 * The crawl's own output: the pages, images, documents and vectors each node writes for the urls
 * it fetched. Those are not administrative writes and they have no conflict to resolve -- a url
 * is deduplicated when it is queued and dispatched to exactly one node, so two nodes never write
 * the same row. Sending them through one node would put every page's bytes on the network twice
 * and make the leader the ceiling on the whole cluster's crawl rate.
 *
 * @Description: LeaderGateway
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
public interface LeaderGateway {

    /**
     * Runs one operation on the leader and returns what it returned.
     *
     * <p>
     * On the leader this is a plain call with no network involved -- worth saying because it is
     * the ordinary case in a cluster of one, which is what most installations are.
     *
     * @param operation the registered name of the handler
     * @param request   the argument, serialised as json
     * @param type      what the leader gives back
     */
    <T> T onLeader(String operation, Object request, Class<T> type);

    /**
     * Registers what the leader does for one operation. Every node registers the same handlers:
     * leadership moves, and the node that has it has to be able to serve.
     *
     * @param argType what the request deserialises to, so a handler never sees json
     */
    <A, R> void handle(String operation, Class<A> argType, Function<A, R> handler);

    /** Whether this node is the one that performs the writes at the moment. */
    boolean isLeader();

}
