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

package com.github.greenfinger.core.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.experimental.UtilityClass;

/**
 * The one id scheme, used verbatim by the database, the file system, MinIO, Elasticsearch and the
 * vector store. Every id is a UUID rendered as its 36 character text form, so an id copied out of
 * one of them can be pasted into any of the others.
 *
 * <p>
 * Two flavours, chosen by whether the thing being named has a natural key:
 *
 * <ul>
 * <li><b>v5</b> for resources, images and chunks. Derived from the natural key, so the same input
 * always yields the same id, which is what makes replaying a layer idempotent -- writing a second
 * time overwrites the first rather than duplicating it. The database's unique constraint is then
 * an assertion rather than a mechanism: if it ever fires, url deduplication has a bug.</li>
 * <li><b>v7</b> for catalogs. A catalog has no natural key -- its name is only a label -- and
 * deriving the id from the name would let "delete a catalog and recreate it under the same name"
 * silently inherit the previous catalog's leftover documents and vectors.</li>
 * </ul>
 *
 * @Description: UuidUtils
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@UtilityClass
public class UuidUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Guards the v7 ordering guarantee: several crawl threads can land in the same millisecond, and
     * without a counter their ids would come out in arbitrary order within it.
     */
    private static final AtomicLong MONOTONIC = new AtomicLong();

    private static final long LAST_MILLIS_MASK = 0xFFFFFFFFFFFFL;

    /**
     * Name based, RFC 9562 version 5 (SHA-1 of the namespace followed by the name).
     *
     * @param namespace the scoping uuid, in practice the catalog's id
     * @param name the natural key, already joined into one string by the caller
     */
    public UUID nameBased(UUID namespace, String name) {
        MessageDigest digest = sha1();
        digest.update(toBytes(namespace));
        digest.update(name.getBytes(StandardCharsets.UTF_8));
        byte[] hash = digest.digest();
        hash[6] &= 0x0F;
        hash[6] |= 0x50; // version 5
        hash[8] &= 0x3F;
        hash[8] |= (byte) 0x80; // IETF variant
        return fromBytes(hash);
    }

    /** Convenience form: the namespace as text, for callers holding the catalog id as a string. */
    public String nameBased(String namespace, String name) {
        return nameBased(UUID.fromString(namespace), name).toString();
    }

    /**
     * Time ordered, RFC 9562 version 7: 48 bits of unix milliseconds, then a counter that keeps ids
     * minted in the same millisecond in order, then random bits.
     */
    public UUID timeBased() {
        long millis = System.currentTimeMillis() & LAST_MILLIS_MASK;
        long counter = nextCounter(millis);

        long msb = (millis << 16) | 0x7000L | (counter & 0x0FFFL);
        long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }

    public String timeBasedString() {
        return timeBased().toString();
    }

    /**
     * The counter occupies rand_a, so it wraps at 4096 ids per millisecond. Beyond that the ids are
     * still unique -- the random half guarantees it -- only their ordering within that millisecond
     * stops being meaningful, which no caller depends on.
     */
    private long nextCounter(long millis) {
        while (true) {
            long previous = MONOTONIC.get();
            long previousMillis = previous >>> 12;
            long next = previousMillis == millis ? previous + 1 : (millis << 12);
            if (MONOTONIC.compareAndSet(previous, next)) {
                return next & 0x0FFFL;
            }
        }
    }

    private MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required by the platform", e);
        }
    }

    private byte[] toBytes(UUID uuid) {
        byte[] bytes = new byte[16];
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (msb >>> (8 * (7 - i)));
            bytes[8 + i] = (byte) (lsb >>> (8 * (7 - i)));
        }
        return bytes;
    }

    private UUID fromBytes(byte[] bytes) {
        long msb = 0L;
        long lsb = 0L;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xFFL);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (bytes[i] & 0xFFL);
        }
        return new UUID(msb, lsb);
    }

}
