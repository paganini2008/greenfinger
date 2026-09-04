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

import static org.assertj.core.api.Assertions.assertThat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * 
 * @Description: UuidUtilsTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
class UuidUtilsTest {

    private static final UUID NAMESPACE = UUID.fromString("0192f0c8-1234-7000-8000-000000000001");

    @Test
    void nameBasedIsDeterministic() {
        UUID first = UuidUtils.nameBased(NAMESPACE, "3|https://example.com/a");
        UUID second = UuidUtils.nameBased(NAMESPACE, "3|https://example.com/a");
        assertThat(first).isEqualTo(second);
    }

    @Test
    void nameBasedSeparatesVersions() {
        UUID v3 = UuidUtils.nameBased(NAMESPACE, "3|https://example.com/a");
        UUID v4 = UuidUtils.nameBased(NAMESPACE, "4|https://example.com/a");
        assertThat(v3).isNotEqualTo(v4);
    }

    @Test
    void nameBasedSeparatesNamespaces() {
        UUID other = UUID.fromString("0192f0c8-1234-7000-8000-000000000002");
        assertThat(UuidUtils.nameBased(NAMESPACE, "same"))
                .isNotEqualTo(UuidUtils.nameBased(other, "same"));
    }

    @Test
    void nameBasedCarriesVersionFiveAndIetfVariant() {
        UUID uuid = UuidUtils.nameBased(NAMESPACE, "https://example.com");
        assertThat(uuid.version()).isEqualTo(5);
        assertThat(uuid.variant()).isEqualTo(2);
    }

    @Test
    void nameBasedTextFormRoundTrips() {
        String id = UuidUtils.nameBased(NAMESPACE.toString(), "x");
        assertThat(id).hasSize(36);
        assertThat(UUID.fromString(id)).isEqualTo(UuidUtils.nameBased(NAMESPACE, "x"));
    }

    @Test
    void timeBasedCarriesVersionSevenAndIetfVariant() {
        UUID uuid = UuidUtils.timeBased();
        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
    }

    @Test
    void timeBasedEmbedsCurrentMillis() {
        long before = System.currentTimeMillis();
        long embedded = UuidUtils.timeBased().getMostSignificantBits() >>> 16;
        long after = System.currentTimeMillis();
        assertThat(embedded).isBetween(before - 1000L, after + 1000L);
    }

    @Test
    void timeBasedIsOrderedWithinTheSameMillisecond() {
        List<UUID> minted = IntStream.range(0, 200).mapToObj(i -> UuidUtils.timeBased()).toList();
        for (int i = 1; i < minted.size(); i++) {
            assertThat(minted.get(i).getMostSignificantBits())
                    .isGreaterThan(minted.get(i - 1).getMostSignificantBits());
        }
    }

    @Test
    void timeBasedStaysUniqueUnderConcurrency() throws Exception {
        int threads = 8;
        int perThread = 2000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Set<String>>> tasks = IntStream.range(0, threads)
                    .<Callable<Set<String>>>mapToObj(t -> () -> {
                        Set<String> local = new HashSet<>();
                        for (int i = 0; i < perThread; i++) {
                            local.add(UuidUtils.timeBasedString());
                        }
                        return local;
                    }).toList();
            Set<String> all = new HashSet<>();
            for (Future<Set<String>> future : pool.invokeAll(tasks)) {
                all.addAll(future.get());
            }
            assertThat(all).hasSize(threads * perThread);
        } finally {
            pool.shutdownNow();
        }
    }

}
