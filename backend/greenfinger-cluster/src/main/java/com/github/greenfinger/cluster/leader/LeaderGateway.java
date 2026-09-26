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
 * <p>
 * Everything behind it is a per-process copy -- the catalog table on H2 or SQLite, the RocksDB
 * directories, the local file tree, the embedded index and vector store. One writer means no drift,
 * so the leader's table is simply the truth: no write stamps, content hashes or tombstones, which
 * concurrent writers would all need.
 *
 * <p>
 * The crawl's own output does not come through here: a url is dispatched to exactly one node, so
 * there is no conflict, and routing pages through the leader would double their bytes and cap the
 * cluster's crawl rate.
 *
 * @Description: LeaderGateway
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
public interface LeaderGateway {

    /**
     * Runs one operation on the leader and returns what it returned. On the leader itself this is a
     * plain call with no network -- the ordinary case in the cluster of one most installations are.
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
