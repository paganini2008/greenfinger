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

package com.github.greenfinger.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.core.engine.CrawlTask;

/**
 * Why a url goes on the wire as json rather than through a binary codec.
 *
 * <p>
 * A fair question: openspreader ships a Kryo codec and measures it at twice the throughput of jdk
 * serialization, and this cluster sends a great many urls. So the cost is measured here rather
 * than assumed, and printed so it can be checked on other hardware.
 *
 * <p>
 * The measurement says encoding is not where a crawl spends its time, and the second test says
 * what the choice actually turns on: a wire format between nodes has to survive one of them being
 * upgraded before the others.
 * 
 * @Description: CodecComparisonTest
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
class CodecComparisonTest {

    private static final int ROUNDS = 200_000;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("encoding a url is not where a crawl spends its time")
    void measured() throws Exception {
        CrawlTask task = task();

        byte[] asJson = objectMapper.writeValueAsBytes(task);
        byte[] asJdk = jdkSerialize(task);
        long jsonNanos = timeJson(task) / ROUNDS;

        System.out.printf("CrawlTask: json %d bytes, %d ns to encode and decode"
                + " (jdk serialization would be %d bytes)%n", asJson.length, jsonNanos,
                asJdk.length);

        assertThat(objectMapper.readValue(asJson, CrawlTask.class).getUrl())
                .isEqualTo(task.getUrl());

        // A url is a few hundred bytes, and a crawl is bounded by fetching pages rather than by
        // encoding their addresses. At a thousand urls a second -- which no polite crawler
        // reaches -- this is single digit milliseconds of cpu per second. A codec twice as fast
        // would save half of nothing, and the numbers are printed so that is checkable rather
        // than merely claimed.
        assertThat(jsonNanos).isLessThan(50_000L);
        // and it is already smaller than what java's own serialization would put on the wire
        assertThat(asJson.length).isLessThan(asJdk.length);
    }

    private static byte[] jdkSerialize(CrawlTask task) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(buffer)) {
            out.writeObject(task);
        }
        return buffer.toByteArray();
    }

    @Test
    @DisplayName("json tolerates a field it has never seen; that is what decides it")
    void jsonSurvivesAFieldFromANewerNode() throws Exception {
        // one node upgraded, the others not yet: the new one sends a task with a field the old
        // one has never heard of. Json ignores it and crawls the url. A field-positional codec
        // would fail to decode, and the url would be lost -- during exactly the window when
        // half the cluster is running the new build.
        String fromANewerNode = objectMapper.writeValueAsString(task())
                .replaceFirst("\\{", "{\"somethingAddedLater\":\"x\",");

        CrawlTask decoded = objectMapper.readValue(fromANewerNode, CrawlTask.class);

        assertThat(decoded.getUrl()).isEqualTo("https://books.toscrape.com/catalogue/page-2.html");
    }

    private long timeJson(CrawlTask task) throws Exception {
        for (int i = 0; i < 10_000; i++) {
            objectMapper.readValue(objectMapper.writeValueAsBytes(task), CrawlTask.class);
        }
        long start = System.nanoTime();
        for (int i = 0; i < ROUNDS; i++) {
            objectMapper.readValue(objectMapper.writeValueAsBytes(task), CrawlTask.class);
        }
        return System.nanoTime() - start;
    }

    private static CrawlTask task() {
        CrawlTask task = CrawlTask.seed("01a06032-adf2-7000-871c-5b2e8e9fdc2a",
                CrawlTask.ACTION_CRAWL, "https://books.toscrape.com",
                "https://books.toscrape.com/catalogue/page-2.html", "default", "UTF-8", 0);
        task.setReferer("https://books.toscrape.com/catalogue/page-1.html");
        task.setDepth(3);
        return task;
    }

}
