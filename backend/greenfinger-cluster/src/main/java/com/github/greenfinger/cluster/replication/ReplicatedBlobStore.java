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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import com.github.greenfinger.core.output.BlobStore;
import lombok.extern.slf4j.Slf4j;

/**
 * Copies pages and images to the other nodes, for the one blob store that is not shared.
 *
 * <p>
 * MinIO is one bucket every node writes to and reads from, so nothing here applies to it. A local
 * directory is one per node, and then a picture fetched on node B is a broken image on node A --
 * which is exactly what the picture search shows, since it serves the archived copy rather than
 * the site's own url.
 *
 * <h2>Written only if it is not already there</h2>
 * Delivery is at least once: a frame whose acknowledgement was lost is sent again, and the
 * receiver's own deduplication has a time window rather than a memory. So the same picture can
 * arrive twice, and rewriting it is wasted io and a chance for a reader to catch a half written
 * file. The check is a complete answer rather than a guess because a path here is derived from an
 * id that is derived from the content: the same path always means the same bytes.
 *
 * <h2>This is the expensive channel</h2>
 * A page is a few kilobytes but a crawl of a picture-heavy site moves megabytes a second, all of
 * it multicast to every node. That is the cost of not having shared storage, and it is why the
 * startup report recommends MinIO rather than treating the two as equivalent.
 * 
 * @Description: ReplicatedBlobStore
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class ReplicatedBlobStore implements BlobStore {

    public static final byte OP_WRITE = 20;
    public static final byte OP_DELETE_PREFIX = 21;

    private final BlobStore delegate;
    private final ReplicationSink channel;

    public ReplicatedBlobStore(BlobStore delegate, ReplicationSink channel) {
        this.delegate = delegate;
        this.channel = channel;
    }

    @Override
    public String getName() {
        return "replicated:" + delegate.getName();
    }

    @Override
    public void write(String path, byte[] bytes, String contentType) throws Exception {
        delegate.write(path, bytes, contentType);
        channel.replicate(ReplicationBatch.Entry.of(OP_WRITE, contentType == null ? "" : contentType,
                path, bytes));
    }

    @Override
    public void writeText(String path, String text) throws Exception {
        write(path, text != null ? text.getBytes(StandardCharsets.UTF_8) : new byte[0],
                "text/plain; charset=utf-8");
    }

    @Override
    public long deletePrefix(String prefix) throws Exception {
        long deleted = delegate.deletePrefix(prefix);
        channel.replicate(ReplicationBatch.Entry.of(OP_DELETE_PREFIX, "", prefix));
        return deleted;
    }

    /**
     * Applies a file from another node. The existence check is the whole point -- see the class
     * comment.
     */
    public static void apply(ReplicationBatch.Entry entry, BlobStore blobStore) {
        try {
            switch (entry.op()) {
                case OP_WRITE -> {
                    if (!blobStore.exists(entry.key())) {
                        blobStore.write(entry.key(), entry.value(),
                                entry.scope().isEmpty() ? null : entry.scope());
                    }
                }
                case OP_DELETE_PREFIX -> blobStore.deletePrefix(entry.key());
                default -> log.debug("Unknown blob op: {}", entry.op());
            }
        } catch (Exception e) {
            log.warn("Could not apply blob {} '{}': {}", entry.op(), entry.key(), e.getMessage());
        }
    }

    // ---- reads and lifecycle pass straight through -------------------------------------------

    @Override
    public boolean exists(String path) throws Exception {
        return delegate.exists(path);
    }

    @Override
    public long sizeOfPrefix(String prefix) throws Exception {
        return delegate.sizeOfPrefix(prefix);
    }

    @Override
    public List<String> listPrefix(String prefix) throws Exception {
        return delegate.listPrefix(prefix);
    }

    @Override
    public Optional<String> readText(String path) throws Exception {
        return delegate.readText(path);
    }

    @Override
    public Optional<byte[]> readBytes(String path) throws Exception {
        return delegate.readBytes(path);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        delegate.afterPropertiesSet();
    }

    @Override
    public void destroy() throws Exception {
        delegate.destroy();
    }

}
