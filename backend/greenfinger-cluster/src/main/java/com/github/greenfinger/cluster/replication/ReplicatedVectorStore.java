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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.output.vector.VectorHit;
import com.github.greenfinger.output.vector.VectorPoint;
import com.github.greenfinger.output.vector.VectorStore;
import lombok.extern.slf4j.Slf4j;

/**
 * Copies vectors to the other nodes, for the vector store that is not shared.
 *
 * <p>
 * The same problem the index has and the same answer: Qdrant and Weaviate are servers every node
 * reaches, the embedded store is a directory per node, and a chunk embedded on node B is a hole in
 * node A's semantic search. The vectors travel rather than being recomputed, because embedding is
 * the expensive half -- a float array is a few kilobytes and re-embedding the chunk on three nodes
 * is three times the model's work.
 *
 * <p>
 * Only the writes travel. Every read is answered from this node's own copy, and once the writes
 * have landed the copies are the same.
 * 
 * @Description: ReplicatedVectorStore
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class ReplicatedVectorStore implements VectorStore {

    public static final byte OP_UPSERT = 45;
    public static final byte OP_DELETE_VERSION = 46;
    public static final byte OP_DELETE_CATALOG = 47;

    private final VectorStore delegate;
    private final ReplicationSink channel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReplicatedVectorStore(VectorStore delegate, ReplicationSink channel) {
        this.delegate = delegate;
        this.channel = channel;
    }

    @Override
    public String getName() {
        return "replicated:" + delegate.getName();
    }

    @Override
    public void upsert(String collection, List<VectorPoint> points) throws Exception {
        delegate.upsert(collection, points);
        for (VectorPoint point : points) {
            channel.replicate(ReplicationBatch.Entry.of(OP_UPSERT, collection, point.getId(),
                    encode(point, objectMapper)));
        }
    }

    @Override
    public long deleteByCatalogVersion(String collection, String catalogVersion) throws Exception {
        long deleted = delegate.deleteByCatalogVersion(collection, catalogVersion);
        channel.replicate(
                ReplicationBatch.Entry.of(OP_DELETE_VERSION, collection, catalogVersion));
        return deleted;
    }

    @Override
    public long deleteByCatalog(String collection, String catalogId) throws Exception {
        long deleted = delegate.deleteByCatalog(collection, catalogId);
        channel.replicate(ReplicationBatch.Entry.of(OP_DELETE_CATALOG, collection, catalogId));
        return deleted;
    }

    /**
     * One point on the wire: the float array as raw bytes, then the payload as json.
     *
     * <p>
     * The vector is not json because a 768 float array written as decimal text is four times the
     * bytes and loses the last digit of every one of them; the id and the payload are, because
     * they are text already and a hand-rolled encoding for them would be a second format to keep
     * in step with the first.
     */
    static byte[] encode(VectorPoint point, ObjectMapper objectMapper) throws Exception {
        byte[] payload = objectMapper.writeValueAsBytes(point.getPayload());
        float[] vector = point.getVector();
        ByteBuffer buffer = ByteBuffer.allocate(4 + vector.length * 4 + payload.length);
        buffer.putInt(vector.length);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        buffer.put(payload);
        return buffer.array();
    }

    static VectorPoint decode(String id, byte[] bytes, ObjectMapper objectMapper) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int length = buffer.getInt();
        float[] vector = new float[length];
        for (int i = 0; i < length; i++) {
            vector[i] = buffer.getFloat();
        }
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        Map<String, Object> map = objectMapper.readValue(
                new String(payload, StandardCharsets.UTF_8),
                new TypeReference<Map<String, Object>>() {});
        return new VectorPoint(id, vector, map);
    }

    /**
     * Applies a vector from another node, into an <em>undecorated</em> store: applying through the
     * wrapper would send back out what has only just arrived.
     */
    public static void apply(ReplicationBatch.Entry entry, VectorStore plain,
            ObjectMapper objectMapper) {
        try {
            switch (entry.op()) {
                case OP_UPSERT -> {
                    VectorPoint point = decode(entry.key(), entry.value(), objectMapper);
                    List<VectorPoint> one = new ArrayList<>(1);
                    one.add(point);
                    plain.upsert(entry.scope(), one);
                }
                case OP_DELETE_VERSION ->
                    plain.deleteByCatalogVersion(entry.scope(), entry.key());
                case OP_DELETE_CATALOG -> plain.deleteByCatalog(entry.scope(), entry.key());
                default -> log.debug("Unknown vector op: {}", entry.op());
            }
        } catch (Exception e) {
            log.warn("Could not apply vector {} '{}': {}", entry.op(), entry.key(),
                    e.getMessage());
        }
    }

    // ---- everything else is this node's own copy ---------------------------------------------

    @Override
    public void ensureCollection(String collection, int dimensions) throws Exception {
        delegate.ensureCollection(collection, dimensions);
    }

    @Override
    public long count(String collection, String catalogVersion) throws Exception {
        return delegate.count(collection, catalogVersion);
    }

    @Override
    public long countByCatalog(String collection, String catalogId) throws Exception {
        return delegate.countByCatalog(collection, catalogId);
    }

    @Override
    public List<String> collectionsMatching(String prefix) throws Exception {
        return delegate.collectionsMatching(prefix);
    }

    @Override
    public List<VectorHit> search(String collection, float[] vector, int limit, int offset,
            List<String> catalogVersions) throws Exception {
        return delegate.search(collection, vector, limit, offset, catalogVersions);
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
