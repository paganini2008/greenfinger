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

package com.github.greenfinger.core.component.dedup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

/**
 * 64 bit simhash. Two documents that differ only in boilerplate produce fingerprints a few bits
 * apart, which is what makes near-duplicate detection possible at all -- an exact hash moves
 * completely when a single timestamp on the page changes.
 * 
 * @Description: SimHash
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@UtilityClass
public class SimHash {

    public static final int BITS = 64;

    /** Number of bands the fingerprint is split into for candidate lookup. */
    public static final int BANDS = 4;

    public static final int BAND_BITS = BITS / BANDS;

    public long fingerprint(String text) {
        List<String> tokens = TextNormalizer.tokenize(TextNormalizer.normalize(text));
        if (tokens.isEmpty()) {
            return 0L;
        }
        Map<String, Integer> weights = new HashMap<>();
        tokens.forEach(t -> weights.merge(t, 1, Integer::sum));

        int[] vector = new int[BITS];
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            long hash = fnv1a64(entry.getKey());
            int weight = entry.getValue();
            for (int i = 0; i < BITS; i++) {
                vector[i] += ((hash >>> i) & 1L) == 1L ? weight : -weight;
            }
        }
        long fingerprint = 0L;
        for (int i = 0; i < BITS; i++) {
            if (vector[i] > 0) {
                fingerprint |= (1L << i);
            }
        }
        return fingerprint;
    }

    public int hammingDistance(long left, long right) {
        return Long.bitCount(left ^ right);
    }

    /**
     * The value of one band of the fingerprint. Two fingerprints within {@code k} bits of each
     * other must agree exactly on at least one of {@code BANDS} bands when {@code k < BANDS}, which
     * is what lets a band lookup narrow the field before distances are computed.
     */
    public int band(long fingerprint, int index) {
        return (int) ((fingerprint >>> (index * BAND_BITS)) & ((1L << BAND_BITS) - 1));
    }

    private long fnv1a64(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

}
