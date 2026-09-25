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

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import com.chaconneai.openspreader.cache.ProcessingCache;
import com.chaconneai.spreader.GossipCluster;
import com.github.greenfinger.core.engine.CrawlCoordinatorFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Says so when a node has joined a cluster but will not actually share any work.
 *
 * <p>
 * The wiring is conditional on the replicated cache, since the counters live in it. With the cache
 * off nothing is wired, the coordinator falls back to the local one, and the node runs every crawl
 * by itself while still gossiping and answering as a member -- right for a test slice, a mistake
 * anywhere else, and silent either way, because the crawl works and is merely alone.
 * 
 * @Description: ClusterConfigurationCheck
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class ClusterConfigurationCheck implements SmartInitializingSingleton {

    private final ObjectProvider<GossipCluster> cluster;
    private final ObjectProvider<ProcessingCache> cache;
    private final ObjectProvider<CrawlCoordinatorFactory> coordinatorFactory;

    @Override
    public void afterSingletonsInstantiated() {
        if (cluster.getIfAvailable() == null) {
            return;
        }
        if (cache.getIfAvailable() == null) {
            log.error("This node is in a cluster but the replicated cache is off, so it will run"
                    + " every crawl by itself while still appearing as a member. Set"
                    + " spring.spreader.multiprocessing.cache.enabled=true.");
            return;
        }
        CrawlCoordinatorFactory factory = coordinatorFactory.getIfAvailable();
        if (!(factory instanceof CrawlCluster)) {
            log.error("This node is in a cluster but crawls are still being coordinated locally"
                    + " ({}). Urls will not be shared with the other nodes.",
                    factory == null ? "no coordinator" : factory.getClass().getSimpleName());
        }
    }

}
