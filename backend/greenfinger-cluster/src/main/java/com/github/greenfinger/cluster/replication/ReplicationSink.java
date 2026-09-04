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

/**
 * Where a write goes to be told to the other nodes.
 *
 * <p>
 * The decorators need exactly this and nothing else about the cluster -- not membership, not the
 * leader, not the channel. Naming that keeps them honest: a decorator that could reach the cluster
 * could start making decisions with it, and deciding anything is not a decorator's job.
 * 
 * @Description: ReplicationSink
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@FunctionalInterface
public interface ReplicationSink {

    /**
     * Queues one write for the other nodes. Never blocks and never throws: replication is not on
     * the critical path of the write it describes, and a page that was saved is saved whether or
     * not the others have heard about it yet.
     */
    void replicate(ReplicationBatch.Entry entry);

}
