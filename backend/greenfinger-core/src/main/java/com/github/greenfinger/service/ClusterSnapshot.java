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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The cluster as it stood when a run ended, for the report that records it.
 *
 * <p>
 * An interface because greenfinger-core has no idea what a cluster is -- and must not learn, since
 * it is the module that runs perfectly well without one. The standalone answer is not an empty map
 * but a filled-in one saying a single process: a report whose cluster section is missing reads as
 * "this was not recorded", and a report that says {@code clustered: false} reads as what actually
 * happened.
 * 
 * @Description: ClusterSnapshot
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
@FunctionalInterface
public interface ClusterSnapshot {

    /**
     * @return never null, and never empty.
     */
    Map<String, Object> snapshot();

    /**
     * One process, which is a cluster of one.
     */
    static ClusterSnapshot standalone() {
        return () -> {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("clustered", false);
            snapshot.put("name", "");
            snapshot.put("self", "");
            snapshot.put("leader", "");
            snapshot.put("memberCount", 1);
            snapshot.put("members", List.of());
            snapshot.put("channels", Map.of());
            snapshot.put("buffers", List.of());
            snapshot.put("splitBrain", "");
            return snapshot;
        };
    }

}
