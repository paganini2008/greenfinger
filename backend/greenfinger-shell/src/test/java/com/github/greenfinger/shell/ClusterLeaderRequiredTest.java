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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import com.chaconneai.spreader.GossipCluster;
import com.github.greenfinger.core.WebCrawlerException;

/**
 * A crawler that joined a cluster with no leader.
 *
 * @Description: ClusterLeaderRequiredTest
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
class ClusterLeaderRequiredTest {

    @Test
    @DisplayName("no leader is a refusal to start, not a crawl that waits for one")
    void refusesWhenNobodyLeads() throws Exception {
        GossipCluster cluster = mock(GossipCluster.class);
        when(cluster.clusterName()).thenReturn("nightly");
        when(cluster.awaitLeader(anyLong(), any())).thenReturn(false);

        assertThatThrownBy(() -> new ClusterLeaderRequired(provider(cluster)).afterPropertiesSet())
                .isInstanceOf(WebCrawlerException.class).hasMessageContaining("no leader")
                .hasMessageContaining("nightly");
    }

    @Test
    @DisplayName("a leader, whoever it is, is all this asks for")
    void acceptsAnyLeader() throws Exception {
        GossipCluster cluster = mock(GossipCluster.class);
        when(cluster.awaitLeader(anyLong(), any())).thenReturn(true);

        assertThatCode(() -> new ClusterLeaderRequired(provider(cluster)).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("no cluster at all is a test slice, where there is nobody to lead")
    void toleratesNoCluster() {
        assertThatCode(() -> new ClusterLeaderRequired(provider(null)).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    private ObjectProvider<GossipCluster> provider(GossipCluster cluster) {
        return new ObjectProvider<>() {

            @Override
            public GossipCluster getObject() {
                return cluster;
            }

            @Override
            public GossipCluster getIfAvailable() {
                return cluster;
            }
        };
    }

}
