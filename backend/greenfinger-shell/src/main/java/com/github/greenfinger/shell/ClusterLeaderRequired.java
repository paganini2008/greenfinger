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

package com.github.greenfinger.shell;

import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import com.chaconneai.spreader.GossipCluster;
import com.github.greenfinger.core.ManagedBeanLifeCycle;
import com.github.greenfinger.core.WebCrawlerException;

/**
 * A crawler with no leader has nothing to run on, and says so at once.
 *
 * <p>
 * Leadership is possession of the cluster port. A process that neither holds it nor can find a
 * member who does elects nobody -- which is what happens when something that is not a member is
 * sitting on the port. Everything then works except what needs a leader: catalogs cannot be
 * written, and the crawl waits on a request nobody answers.
 *
 * @Description: ClusterLeaderRequired
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
public class ClusterLeaderRequired implements ManagedBeanLifeCycle {

    /** Generous enough for an election, short enough to be a startup check. */
    static final long WAIT_SECONDS = 15L;

    /** Looked up late: the cluster is auto-configured, which happens after this bean is declared. */
    private final ObjectProvider<GossipCluster> clusters;

    public ClusterLeaderRequired(ObjectProvider<GossipCluster> clusters) {
        this.clusters = clusters;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        GossipCluster cluster = clusters.getIfAvailable();
        if (cluster == null) {
            // the cluster is switched off: a test slice, where there is nobody to lead
            return;
        }
        cluster.awaitJoin(WAIT_SECONDS, TimeUnit.SECONDS);
        if (cluster.awaitLeader(WAIT_SECONDS, TimeUnit.SECONDS)) {
            return;
        }
        throw new WebCrawlerException("Cluster '" + cluster.clusterName() + "' has no leader, so"
                + " this node cannot do anything that needs one.\nThe cluster port is held by"
                + " something that is not a member of it -- another cluster, or a process that"
                + " merely has the port.\nStop it, or give this run a cluster of its own.");
    }

}
