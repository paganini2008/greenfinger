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
 * Copies pages and images to the other nodes, for the local blob store, which is a directory per
 * node -- a picture fetched on node B is otherwise a broken image on node A, since search serves
 * the archived copy. MinIO is one shared bucket and needs none of it.
 *
 * <p>
 * Written only if absent. Delivery is at least once, and rewriting is wasted io plus a chance for a
 * reader to catch a half written file. The check is exact rather than a guess because a path is
 * derived from an id derived from the content: the same path always means the same bytes.
 *
 * <p>
 * This is the expensive channel -- a picture-heavy crawl multicasts megabytes a second -- which is
 * why the startup report recommends MinIO.
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
