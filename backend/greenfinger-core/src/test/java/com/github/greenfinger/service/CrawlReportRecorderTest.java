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

package com.github.greenfinger.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The two pieces of the report that are worth testing on their own: what each node did, and a
 * jdbc url that must not carry a password into a row anybody can read.
 * 
 * @Description: CrawlReportRecorderTest
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
class CrawlReportRecorderTest {

    @Test
    @DisplayName("counters are turned round: one row per node, not one per counter")
    void invertsTheCounters() {
        List<Map<String, Object>> nodes = CrawlReportRecorder.perNode(
                Map.of("savedResourceCount", Map.of("a1b2", 18L, "c3d4", 21L),
                        "handledUrlCount", Map.of("a1b2", 20L, "c3d4", 24L)),
                List.of());

        assertThat(nodes).hasSize(2);
        assertThat(nodes.get(0).get("node")).isEqualTo("a1b2");
        assertThat(nodes.get(0).get("dashboard"))
                .isEqualTo(Map.of("savedResourceCount", 18L, "handledUrlCount", 20L));
    }

    @Test
    @DisplayName("a node that did nothing still gets a row, because that is the finding")
    void aSilentNodeStillHasARow() {
        List<Map<String, Object>> nodes = CrawlReportRecorder.perNode(
                Map.of("savedResourceCount", Map.of("a1b2", 18L)), List.of("a1b2", "e5f6"));

        assertThat(nodes).hasSize(2);
        assertThat(nodes.get(1).get("node")).isEqualTo("e5f6");
        assertThat((Map<?, ?>) nodes.get(1).get("dashboard")).isEmpty();
    }

    @Test
    @DisplayName("the membership list and the counters spell a node differently, and it is one node")
    void theShortAndLongFormsAreTheSameNode() {
        // the counters are keyed by the short id the coordinator uses; the membership list holds
        // the full uuid. Matched exactly, every node that had done work would get a second,
        // empty row underneath its real one
        List<Map<String, Object>> nodes = CrawlReportRecorder.perNode(
                Map.of("savedResourceCount", Map.of("cd222cef", 3L)),
                List.of("cd222cef-def6-4a63-a3ff-049047d731ac"));

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).get("node")).isEqualTo("cd222cef");
    }

    @Test
    void oneProcessHasNoPerNodeSection() {
        assertThat(CrawlReportRecorder.perNode(Map.of(), null)).isEmpty();
        assertThat(CrawlReportRecorder.perNode(null, List.of())).isEmpty();
    }

    @Test
    @DisplayName("a jdbc url is stored without whatever its query string was carrying")
    void stripsCredentialsFromTheJdbcUrl() {
        assertThat(CrawlReportRecorder.stripCredentials(
                "jdbc:mysql://db:3306/greenfinger?user=root&password=hunter2"))
                        .isEqualTo("jdbc:mysql://db:3306/greenfinger");
        assertThat(CrawlReportRecorder.stripCredentials("jdbc:h2:file:./data/gf"))
                .isEqualTo("jdbc:h2:file:./data/gf");
        assertThat(CrawlReportRecorder.stripCredentials(null)).isEmpty();
    }

}
