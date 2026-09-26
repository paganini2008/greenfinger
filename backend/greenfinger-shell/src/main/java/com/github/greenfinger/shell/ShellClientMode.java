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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import com.chaconneai.spreader.GossipCluster;
import com.github.greenfinger.cluster.ClusterProperties;
import com.github.greenfinger.cluster.leader.LeaderChannel;
import com.github.greenfinger.cluster.remote.RemoteOperations;
import com.github.greenfinger.service.ops.GreenfingerOperations;

/**
 * The terminal: a face on a cluster somebody else is running, which is what
 * {@code greenfinger-shell.sh} starts. It joins the crawlers' cluster under an application name of
 * its own and carries no engine, database or index -- every command is a question put to the
 * leader. A crawl started here belongs to the cluster and outlives the session, as the page's do.
 *
 * @Description: ShellClientMode
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = GreenfingerShellMain.CLIENT_PROPERTY, havingValue = "true")
// the cluster's own auto-configuration is off here: it needs a crawl registry, which is the very
// thing this face does not have
@EnableConfigurationProperties(ClusterProperties.class)
public class ShellClientMode {

    /**
     * The request channel. The same one the crawler nodes use between themselves, with no handlers
     * registered on this side: this process asks and never answers.
     */
    @Bean
    public LeaderChannel leaderChannel(GossipCluster cluster, ClusterProperties properties) {
        return new LeaderChannel(cluster, properties.getLeader().getTimeoutMs(),
                properties.getLeader().getMaxAttempts(), false);
    }

    @Bean
    public GreenfingerOperations remoteOperations(LeaderChannel leaderChannel) {
        return new RemoteOperations(leaderChannel);
    }

    @Bean
    public ShellClusterGuard shellClusterGuard(GossipCluster cluster) {
        return new ShellClusterGuard(cluster);
    }

}
