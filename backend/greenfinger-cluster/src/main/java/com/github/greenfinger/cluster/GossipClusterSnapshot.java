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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.github.greenfinger.service.ClusterSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The cluster as it stood when a run ended, taken from spreader.
 *
 * <p>
 * Recorded with the run rather than left to a monitoring system, because the numbers that explain a
 * crawl are the ones from the moment it finished: a full inbound buffer discards silently by
 * design, and a report that says forty pages were dispatched and thirty-one handled is only
 * explicable next to the drop count of the channel that was carrying them.
 * 
 * @Description: GossipClusterSnapshot
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class GossipClusterSnapshot implements ClusterSnapshot {

    private final GossipCluster cluster;

    @Override
    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        // the standalone shape, filled in below: a reader comparing two reports should not have to
        // wonder whether a missing key means one process or a failed lookup
        snapshot.putAll(ClusterSnapshot.standalone().snapshot());
        try {
            List<Node> members = cluster.members();
            snapshot.put("clustered", true);
            snapshot.put("name", cluster.clusterName());
            snapshot.put("self", cluster.self().label());
            snapshot.put("leader", cluster.leader() != null ? cluster.leader().label() : "");
            snapshot.put("memberCount", members.size());
            snapshot.put("members", members.stream().map(this::describe).toList());
            snapshot.put("channels", channels());
            snapshot.put("buffers", buffers());
            snapshot.put("splitBrain", String.valueOf(cluster.splitBrainStatus()));
        } catch (Exception e) {
            // a report is worth writing without its cluster section; the crawl already happened
            log.debug("Could not take a cluster snapshot: {}", e.getMessage());
        }
        return snapshot;
    }

    private Map<String, Object> describe(Node node) {
        Map<String, Object> described = new LinkedHashMap<>();
        described.put("id", node.id());
        described.put("label", node.label());
        described.put("host", node.host());
        described.put("port", node.port());
        described.put("self", node.id().equals(cluster.self().id()));
        described.put("leader", cluster.leader() != null && node.id().equals(cluster.leader().id()));
        return described;
    }

    private Map<String, Object> channels() {
        Map<String, Object> channels = new LinkedHashMap<>();
        cluster.metrics().forEach((name, metrics) -> channels.put(name, String.valueOf(metrics)));
        return channels;
    }

    private List<String> buffers() {
        return cluster.bufferMetrics().stream().map(String::valueOf).toList();
    }

}
