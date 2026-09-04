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

import com.chaconneai.openspreader.cache.ProcessingCache;
import com.github.greenfinger.cluster.state.ClusterGlobalStateManager;
import com.github.greenfinger.core.WebCrawlerExtractorProperties;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.DefaultWebCrawlerComponentFactory;
import com.github.greenfinger.cluster.replication.ReplicatedDedup;
import com.github.greenfinger.core.component.dedup.ContentDedupFilter;
import com.github.greenfinger.core.component.dedup.ExistingUrlPathFilter;
import com.github.greenfinger.core.component.state.GlobalStateManager;
import com.github.greenfinger.cluster.replication.ClusterReplication;

/**
 * The standard components, with one substitution: the counters.
 *
 * <p>
 * Three substitutions, and each is a thing that stops being this process's own the moment a second
 * node joins the same crawl.
 *
 * <ul>
 * <li><b>The counters.</b> "1,200 pages saved" has to mean the crawl saved 1,200, not that this
 * node did, or the Monitor page shows a third of the truth on each of three screens and
 * {@code maxFetchSize} fires three times too late.</li>
 * <li><b>The url filter.</b> Urls go round robin, so a url found on two pages usually lands on two
 * nodes; without telling each other, both fetch it.</li>
 * <li><b>The content filter.</b> The same page republished at a second address is the case url
 * dedup cannot catch, and it is only caught at all if the fingerprints are shared.</li>
 * </ul>
 *
 * <p>
 * The frontier is deliberately <em>not</em> substituted: it is this node's own work queue, and
 * sharing it would have every node crawl everything.
 * 
 * @Description: ClusterComponentFactory
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
public class ClusterComponentFactory extends DefaultWebCrawlerComponentFactory {

    private final ProcessingCache cache;
    private final ClusterReplication replication;
    private final java.util.function.Supplier<String> nodeId;
    private final long counterFlushIntervalMs;

    public ClusterComponentFactory(WebCrawlerProperties webCrawlerProperties,
            WebCrawlerExtractorProperties extractorProperties, ProcessingCache cache,
            ClusterReplication replication, java.util.function.Supplier<String> nodeId,
            long counterFlushIntervalMs) {
        super(webCrawlerProperties, extractorProperties);
        this.cache = cache;
        this.replication = replication;
        this.nodeId = nodeId;
        this.counterFlushIntervalMs = counterFlushIntervalMs;
    }

    @Override
    public GlobalStateManager getGlobalStateManager(CatalogDetails catalogDetails,
            boolean initiator) {
        return new ClusterGlobalStateManager(cache, catalogDetails, nodeId.get(),
                counterFlushIntervalMs, initiator);
    }

    @Override
    public ExistingUrlPathFilter getExistingUrlPathFilter(CatalogDetails catalogDetails) {
        return new ReplicatedDedup.Urls(super.getExistingUrlPathFilter(catalogDetails),
                replication.getDedup(), catalogDetails.getId());
    }

    @Override
    public ContentDedupFilter getContentDedupFilter(CatalogDetails catalogDetails) {
        return new ReplicatedDedup.Contents(super.getContentDedupFilter(catalogDetails),
                replication.getDedup(), catalogDetails.getId());
    }

}
