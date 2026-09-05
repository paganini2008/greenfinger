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

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Several writes in one datagram.
 *
 * <p>
 * Replication is a stream of small writes -- a url fingerprint is forty bytes, a database row a
 * few hundred -- and one message each would spend more time framing and acknowledging than
 * writing. Packing them costs nothing in latency that matters, because nothing is waiting on a
 * replica.
 *
 * <h2>Why a binary frame and not json</h2>
 * The same frame carries image bytes. Base64 in json would inflate every picture by a third, and
 * an image is the largest thing this ever sends.
 *
 * @param op    what to do with it, defined by whoever owns the channel
 * @param scope which crawl it belongs to. Carried on every entry because the stores being
 *              replicated are per catalog and version -- a url filter is not one store but one
 *              per crawl, and applying a fingerprint to the wrong one would hide a page
 * @param key   the path, the row id, the fingerprint
 * @param value the bytes, or empty for an operation that needs none
 * 
 * @Description: ReplicationBatch
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
public record ReplicationBatch(List<Entry> entries) {

    /**
     * 
     * @Description: Entry
     * @Author: Fred Feng
     * @Date: 02/09/2026
     * @Version 2.0.0
     */
    public record Entry(byte op, String scope, String key, byte[] value) {

        public static Entry of(byte op, String scope, String key) {
            return new Entry(op, scope, key, new byte[0]);
        }

        public static Entry of(byte op, String scope, String key, byte[] value) {
            return new Entry(op, scope, key, value == null ? new byte[0] : value);
        }

        public String valueAsText() {
            return new String(value, StandardCharsets.UTF_8);
        }
    }

    public byte[] encode() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(estimate());
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeInt(entries.size());
            for (Entry entry : entries) {
                out.writeByte(entry.op());
                writeString(out, entry.scope());
                writeString(out, entry.key());
                out.writeInt(entry.value().length);
                out.write(entry.value());
            }
        } catch (IOException e) {
            // a ByteArrayOutputStream does not do io, so this cannot happen
            throw new IllegalStateException(e);
        }
        return buffer.toByteArray();
    }

    public static ReplicationBatch decode(byte[] content) {
        try (DataInputStream in =
                new DataInputStream(new java.io.ByteArrayInputStream(content))) {
            int count = in.readInt();
            List<Entry> entries = new ArrayList<>(Math.max(0, Math.min(count, 4096)));
            for (int i = 0; i < count; i++) {
                byte op = in.readByte();
                String scope = readString(in);
                String key = readString(in);
                byte[] value = new byte[in.readInt()];
                in.readFully(value);
                entries.add(new Entry(op, scope, key, value));
            }
            return new ReplicationBatch(entries);
        } catch (IOException | NegativeArraySizeException | OutOfMemoryError e) {
            // a malformed frame must not take the node down: length fields come off the network
            return null;
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        byte[] bytes = new byte[in.readInt()];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private int estimate() {
        int size = Integer.BYTES;
        for (Entry entry : entries) {
            size += sizeOf(entry);
        }
        return size;
    }

    /** Roughly what this entry adds to a frame, used to decide when a batch is full enough. */
    public static int sizeOf(Entry entry) {
        return 1 + Integer.BYTES + entry.scope().length() * 3 + Integer.BYTES
                + entry.key().length() * 3 + Integer.BYTES + entry.value().length;
    }

}
