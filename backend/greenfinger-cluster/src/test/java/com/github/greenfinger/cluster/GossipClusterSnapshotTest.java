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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.cluster.support.TestCluster;
import com.github.greenfinger.service.ClusterSnapshot;

/**
 * What goes into the cluster section of a run's report.
 * 
 * @Description: GossipClusterSnapshotTest
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
class GossipClusterSnapshotTest {

    @Test
    @DisplayName("every member is named, and the one that took the snapshot is marked")
    void describesTheCluster() {
        try (TestCluster cluster = TestCluster.start(2)) {
            TestCluster.Node self = cluster.nodes().get(0);
            Map<String, Object> snapshot =
                    new GossipClusterSnapshot(self.cluster()).snapshot();

            assertThat(snapshot.get("clustered")).isEqualTo(true);
            assertThat(snapshot.get("memberCount")).isEqualTo(2);
            assertThat(String.valueOf(snapshot.get("self")))
                    .isEqualTo(self.cluster().self().label());
            assertThat(String.valueOf(snapshot.get("leader"))).isNotBlank();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> members = (List<Map<String, Object>>) snapshot.get("members");
            assertThat(members).hasSize(2);
            assertThat(members).anySatisfy(
                    member -> assertThat(member.get("self")).isEqualTo(true));
            assertThat(members).anySatisfy(
                    member -> assertThat(member.get("leader")).isEqualTo(true));
            assertThat(members.get(0)).containsKeys("id", "label", "host", "port");

            // the numbers that explain a crawl afterwards: a full inbound buffer discards
            // silently, so its counters have to be recorded with the run or not at all
            assertThat(snapshot).containsKeys("channels", "buffers", "splitBrain");
        }
    }

    @Test
    @DisplayName("a standalone report says one process rather than saying nothing")
    void theStandaloneAnswerIsFilledIn() {
        Map<String, Object> snapshot = ClusterSnapshot.standalone().snapshot();

        assertThat(snapshot.get("clustered")).isEqualTo(false);
        assertThat(snapshot.get("memberCount")).isEqualTo(1);
        // the same keys as the clustered answer: a reader comparing two reports must not have to
        // wonder whether an absent key means one process or a failed lookup
        assertThat(snapshot).containsKeys("name", "self", "leader", "members", "channels",
                "buffers", "splitBrain");
    }

    @Test
    @DisplayName("a cluster that cannot be asked still produces a report section")
    void aBrokenClusterDoesNotLoseTheReport() {
        try (TestCluster cluster = TestCluster.start(1)) {
            TestCluster.Node self = cluster.nodes().get(0);
            self.cluster().stop();

            Map<String, Object> snapshot = new GossipClusterSnapshot(self.cluster()).snapshot();
            assertThat(snapshot).containsKeys("clustered", "memberCount", "members");
        }
    }

}
