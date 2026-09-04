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

import java.util.ArrayList;
import java.util.List;
import com.github.greenfinger.cluster.replication.ReplicationBatch;
import com.github.greenfinger.cluster.replication.ReplicationSink;

/**
 * Records what a decorator would have told the other nodes.
 *
 * <p>
 * What the decorators are for is deciding <em>what</em> to send and <em>when not to</em> -- a
 * value that came from elsewhere must not be sent back, an unchanged row must not be sent at all.
 * Those decisions are what this makes assertable; the sending itself is the channel's, and has its
 * own test against a real cluster.
 * 
 * @Description: CapturingSink
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
public class CapturingSink implements ReplicationSink {

    private final List<ReplicationBatch.Entry> entries = new ArrayList<>();

    @Override
    public void replicate(ReplicationBatch.Entry entry) {
        entries.add(entry);
    }

    public List<ReplicationBatch.Entry> entries() {
        return List.copyOf(entries);
    }

    public List<String> keys() {
        return entries.stream().map(ReplicationBatch.Entry::key).toList();
    }

    public List<Byte> ops() {
        return entries.stream().map(ReplicationBatch.Entry::op).toList();
    }

    public void clear() {
        entries.clear();
    }

}
