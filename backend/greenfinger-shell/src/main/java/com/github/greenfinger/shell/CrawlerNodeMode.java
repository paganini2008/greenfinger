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

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.chaconneai.spreader.GossipCluster;
import com.github.greenfinger.service.EnableGreenfingerCrawler;

/**
 * A crawler with a command line on it: the engine, the database and the outputs, in this process.
 *
 * <p>
 * What {@code greenfinger-cli.sh} runs. A crawl given on the line runs here and the process waits
 * for it, which is what a cron entry or a deploy script wants -- a verb, and an exit code.
 *
 * <p>
 * The default, so nothing has to be passed for it. Its opposite is {@link ShellClientMode}, and
 * the two are conditional rather than two application classes because whichever one is not in use
 * must not be wired at all: one brings up an engine and a database, the other brings up neither.
 *
 * @Description: CrawlerNodeMode
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = GreenfingerShellMain.CLIENT_PROPERTY, havingValue = "false",
        matchIfMissing = true)
@EnableGreenfingerCrawler
public class CrawlerNodeMode {

    /** Refuses to carry on when the cluster this node joined has no leader. */
    @Bean
    public ClusterLeaderRequired clusterLeaderRequired(ObjectProvider<GossipCluster> clusters) {
        return new ClusterLeaderRequired(clusters);
    }

}
