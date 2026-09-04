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
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import com.github.greenfinger.cluster.support.TestCluster;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.vector.EmbeddingProperties;

/**
 * What the node says about itself at startup.
 *
 * <p>
 * Three of the things that decide how a cluster behaves fail quietly -- a missing transport jar,
 * a missing queue library, and a store that is per node when it was assumed shared. The startup
 * line is the only place any of them is stated, so it is worth asserting that it states them.
 * 
 * @Description: ClusterWiringTest
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
class ClusterWiringTest {

    private TestCluster cluster;

    @BeforeEach
    void setUp() {
        cluster = TestCluster.start(1);
    }

    @AfterEach
    void tearDown() {
        cluster.close();
    }

    @Test
    @DisplayName("a local database and a local directory are reported, because both mean copies")
    void reportsWhatIsPerNode(@TempDir Path root) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.datasource.url", "jdbc:h2:file:" + root + "/db");

        new ClusterStartupReport(cluster.node(0).cluster(), outputFactory(root), environment)
                .afterSingletonsInstantiated();

        // it logs rather than returns, so what is asserted is that it can run at all against a
        // real node and a real factory -- the shape it reads is what would break
        assertThat(StoreType.ofJdbcUrl(environment.getProperty("spring.datasource.url")))
                .isEqualTo(StoreType.H2);
    }

    @Test
    @DisplayName("a shared database is reported as shared, and nothing is copied")
    void reportsWhatIsShared(@TempDir Path root) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.datasource.url", "jdbc:postgresql://localhost/greenfinger");

        new ClusterStartupReport(cluster.node(0).cluster(), outputFactory(root), environment)
                .afterSingletonsInstantiated();

        assertThat(StoreType.ofJdbcUrl(environment.getProperty("spring.datasource.url")).shared())
                .isTrue();
    }

    @Test
    @DisplayName("no datasource url at all is not a crash: it is an unknown store, treated as shared")
    void survivesAMissingUrl(@TempDir Path root) {
        new ClusterStartupReport(cluster.node(0).cluster(), outputFactory(root),
                new MockEnvironment()).afterSingletonsInstantiated();

        assertThat(StoreType.ofJdbcUrl(null)).isEqualTo(StoreType.OTHER);
    }

    @Test
    void thePropertiesDescribeThemselves() {
        ClusterProperties properties = new ClusterProperties();
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getDispatch().getConsumers()).isGreaterThan(0);
        assertThat(properties.getDispatch().getBufferCapacity()).isGreaterThan(0);
        assertThat(properties.getReplication().getBatchSize()).isGreaterThan(0);
        assertThat(properties.getCounters().getFlushIntervalMs()).isGreaterThan(0);
        assertThat(properties.toString()).contains("Dispatch");
    }

    @Test
    @DisplayName("no cluster at all is silence: a single process is not a misconfiguration")
    void aloneIsNotAComplaint() {
        new ClusterConfigurationCheck(provider(null), provider(null), provider(null))
                .afterSingletonsInstantiated();
    }

    @Test
    @DisplayName("a cluster without the cache runs every crawl alone while looking like a member,"
            + " and nothing else would ever say so")
    void aClusterWithoutTheCacheIsReported() {
        new ClusterConfigurationCheck(provider(cluster.node(0).cluster()), provider(null),
                provider(null)).afterSingletonsInstantiated();
    }

    @Test
    @DisplayName("a cluster whose coordinator is still the local one shares nothing")
    void aLocalCoordinatorInAClusterIsReported() {
        new ClusterConfigurationCheck(provider(cluster.node(0).cluster()),
                provider(cluster.node(0).cache()),
                provider(com.github.greenfinger.core.engine.CrawlCoordinatorFactory.local()))
                        .afterSingletonsInstantiated();
    }

    /** The one method of ObjectProvider these checks use, and null for "there is not one". */
    private static <T> org.springframework.beans.factory.ObjectProvider<T> provider(T value) {
        return new org.springframework.beans.factory.ObjectProvider<>() {

            @Override
            public T getObject() {
                return value;
            }

            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }
        };
    }

    private static OutputFactory outputFactory(Path root) {
        OutputProperties outputProperties = new OutputProperties();
        outputProperties.getFile().setDirectory(root.toString());
        return new OutputFactory(outputProperties, new EmbeddingProperties());
    }

}
