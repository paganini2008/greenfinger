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
import com.chaconneai.spreader.GossipCluster;
import com.github.greenfinger.core.ManagedBeanLifeCycle;
import com.github.greenfinger.core.WebCrawlerException;

/**
 * A terminal with nothing to talk to says so at once.
 *
 * <p>
 * It never leads: the launcher starts it with {@code spring.spreader.leader-eligible=false}, so it
 * joins, sees everyone and contends for nothing. What that leaves is the useful question -- is
 * there a crawler here at all? No leader means no crawler node has taken the cluster port, and
 * every command would be a request nobody answers.
 *
 * @Description: ShellClusterGuard
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
public class ShellClusterGuard implements ManagedBeanLifeCycle {

    /** Long enough for an election in progress, short enough to be a startup check. */
    static final long WAIT_SECONDS = 15L;

    private final GossipCluster cluster;

    public ShellClusterGuard(GossipCluster cluster) {
        this.cluster = cluster;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        cluster.awaitJoin(WAIT_SECONDS, TimeUnit.SECONDS);
        if (cluster.awaitLeader(WAIT_SECONDS, TimeUnit.SECONDS)) {
            return;
        }
        throw new WebCrawlerException("No crawler node is running in cluster '"
                + cluster.clusterName() + "'.\nThis is a terminal, not a crawler: start the nodes"
                + " first with  ./run-local.sh  (or ./run-docker.sh), then open this again.\nTo"
                + " crawl in this process instead, use  ./greenfinger-cli.sh");
    }

}
