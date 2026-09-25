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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import com.chaconneai.openspreader.cache.ProcessingCache;
import com.chaconneai.openspreader.pooling.ProcessingPool;
import com.chaconneai.spreader.GossipCluster;
import com.github.greenfinger.cluster.channel.CrawlTaskChannel;
import com.github.greenfinger.core.WebCrawlerExtractorProperties;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.component.WebCrawlerComponentFactory;
import com.github.greenfinger.core.engine.CrawlCoordinatorFactory;
import com.github.greenfinger.core.engine.CrawlRegistry;
import com.github.greenfinger.cluster.replication.ClusterReplication;
import com.github.greenfinger.cluster.replication.JpaRowWriter;
import com.github.greenfinger.cluster.leader.CatalogCatchUp;
import com.github.greenfinger.cluster.leader.LeaderCatalogStore;
import com.github.greenfinger.cluster.leader.LeaderChannel;
import com.github.greenfinger.cluster.leader.LeaderDeletionService;
import com.github.greenfinger.cluster.replication.ReplicatedCatalogStore;
import com.github.greenfinger.cluster.replication.ReplicationSink;
import com.github.greenfinger.cluster.replication.ReplicatedRecordStore;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.output.BlobStore;
import com.github.greenfinger.output.index.LuceneIndexes;
import com.github.greenfinger.output.vector.VectorStore;
import com.github.greenfinger.core.catalog.CatalogDetailsService;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.core.report.CrawlReportStore;
import com.github.greenfinger.core.WebCrawlerSemaphore;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.vector.EmbeddingProperties;
import com.github.greenfinger.record.ImageRepository;
import com.github.greenfinger.record.ResourceImageRepository;
import com.github.greenfinger.record.ResourceRepository;
import com.github.greenfinger.service.CrawlerLauncher;
import com.github.greenfinger.service.DeletionBroadcast;
import com.github.greenfinger.service.DeletionService;
import com.github.greenfinger.service.FileRestorer;
import com.github.greenfinger.service.ClusterSnapshot;
import com.github.greenfinger.service.ReplayService;

/**
 * Wires the cluster in, without an annotation to remember.
 *
 * <p>
 * There is no standalone edition to opt out of: one process is a cluster of one. The cache is a
 * condition rather than a nicety -- the counters live in it and completion compares two of them --
 * so without it the wiring is absent and the crawl runs on this node alone, which
 * {@link ClusterConfigurationCheck} says loudly when it happens outside a test.
 * 
 * @Description: GreenfingerClusterAutoConfiguration
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@AutoConfiguration
@ConditionalOnBean({GossipCluster.class, CrawlRegistry.class, ProcessingCache.class})
@ConditionalOnProperty(prefix = "greenfinger.cluster", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(ClusterProperties.class)
public class GreenfingerClusterAutoConfiguration {

    @Bean
    public CrawlTaskChannel crawlTaskChannel(GossipCluster cluster, CrawlRegistry crawlRegistry,
            ClusterProperties properties) {
        return new CrawlTaskChannel(cluster, crawlRegistry, properties.getDispatch());
    }

    /**
     * Also the {@link CrawlCoordinatorFactory}, and registered once: returning the same instance
     * from a second {@code @Bean} method runs its lifecycle twice, which subscribes to the crawl
     * channel twice and fetches every page again. Primary, because core declares its own.
     */
    @Bean
    @Primary
    public CrawlCluster crawlCluster(GossipCluster cluster, CrawlTaskChannel crawlTaskChannel,
            CrawlRegistry crawlRegistry, ObjectProvider<CrawlerLauncher> launcher,
            ObjectProvider<ReplayService> replayService,
            ObjectProvider<DeletionService> deletionService,
            ApplicationEventPublisher eventPublisher) {
        return new CrawlCluster(cluster, crawlTaskChannel, crawlRegistry, launcher, replayService,
                deletionService, eventPublisher);
    }

    /**
     * How a delete reaches the two layers that no store copies: the embedded index, which is
     * handed out undecorated, and the RocksDB directories, which are plain files. Primary for the
     * reason every override in this class is -- core's own bean is registered first, so a
     * same-named one here would be a duplicate rather than a replacement.
     */
    @Bean
    @Primary
    public DeletionBroadcast clusterDeletionBroadcast(ObjectProvider<CrawlCluster> crawlCluster) {
        return new ClusterDeletionBroadcast(crawlCluster);
    }

    /**
     * The same components core would have built, with the counters substituted. A second bean
     * rather than an override: core's {@code @ConditionalOnMissingBean} only yields to a bean
     * registered earlier, and auto-configuration is last. This one is primary.
     */
    @Bean
    @Primary
    public WebCrawlerComponentFactory clusterComponentFactory(
            WebCrawlerProperties webCrawlerProperties,
            WebCrawlerExtractorProperties extractorProperties, ProcessingCache cache,
            ClusterReplication replication, ClusterProperties properties, GossipCluster cluster) {
        return new ClusterComponentFactory(webCrawlerProperties, extractorProperties, cache,
                replication, () -> cluster.self().shortId(),
                properties.getCounters().getFlushIntervalMs());
    }

    /**
     * Which stores have to be copied, decided once at startup from the jdbc url and the configured
     * blob target rather than guessed per write.
     */
    @Bean(initMethod = "afterPropertiesSet", destroyMethod = "destroy")
    public ClusterReplication clusterReplication(GossipCluster cluster,
            ClusterProperties properties, CrawlRegistry crawlRegistry, Environment environment,
            OutputProperties outputProperties, EmbeddingProperties embeddingProperties,
            ObjectProvider<ResourceRepository> resources, ObjectProvider<ImageRepository> images,
            ObjectProvider<ResourceImageRepository> references,
            // by name, not by type: by type this resolves to the replicating decorator, which
            // needs this bean, and the two would wait for each other. It is also the wrong one --
            // a delete that arrived from elsewhere must not be sent back out
            @Qualifier("resourceRecordStore") ObjectProvider<ResourceRecordStore> recordStore,
            @Qualifier("catalogStore") ObjectProvider<CatalogStore> catalogStore) {
        StoreType database = StoreType.ofJdbcUrl(environment.getProperty("spring.datasource.url"));
        // the plain factory, so what it hands back is the undecorated store: this one is used to
        // apply what arrives, and applying through the decorator would send it straight back
        OutputFactory plain = new OutputFactory(outputProperties, embeddingProperties);
        StoreType blobs = StoreType.ofBlobStore(plain.getBlobStore().getName());

        ReplicatedRecordStore.RowWriter rows = database.replicated()
                ? new JpaRowWriter(resources.getObject(), images.getObject(),
                        references.getObject(), recordStore.getObject())
                : null;
        BlobStore plainBlobStore = blobs.replicated() ? plain.getBlobStore() : null;
        CatalogStore plainCatalogs = database.replicated() ? catalogStore.getObject() : null;

        // the embedded search engines are one copy per node, so their writes have to be copied
        // around; a server every node reaches needs none of it and gets null
        boolean embeddedIndex =
                "lucene".equalsIgnoreCase(outputProperties.getIndex().getProvider());
        boolean embeddedVectors =
                "lucene".equalsIgnoreCase(outputProperties.getVector().getStore());
        LuceneIndexes luceneIndexes = embeddedIndex ? plain.sharedLuceneIndexes() : null;
        VectorStore plainVectors = embeddedVectors ? plain.getVectorStore() : null;

        return new ClusterReplication(cluster, properties, crawlRegistry, database, blobs, rows,
                plainCatalogs, plainBlobStore, luceneIndexes, plainVectors);
    }

    /** Wraps the blob store when its bytes are local, and leaves MinIO alone. */
    @Bean
    @Primary
    public OutputFactory clusterOutputFactory(OutputProperties outputProperties,
            EmbeddingProperties embeddingProperties, ClusterReplication replication) {
        return new ClusterOutputFactory(outputProperties, embeddingProperties, replication);
    }

    /**
     * Wraps the catalog store when the database is a file per node.
     *
     * <p>
     * Before the resource rows, in importance: a node that has not heard of a catalog cannot open
     * its half of a crawl, and the urls dispatched to it go nowhere.
     */
    @Bean
    @Primary
    public CatalogStore clusterCatalogStore(
            @Qualifier("catalogStore") CatalogStore catalogStore,
            ClusterReplication replication, LeaderCatalogStore leaderCatalogStore) {
        return replication.getRecords() != null
                ? leaderCatalogStore
                : catalogStore;
    }

    /**
     * Where an administrative write goes. Declared whatever the database is: a shared one has
     * nothing to replicate, but the same gateway carries a delete, which touches local disk.
     */
    @Bean(initMethod = "afterPropertiesSet", destroyMethod = "destroy")
    public LeaderChannel leaderChannel(GossipCluster cluster, ClusterProperties properties) {
        return new LeaderChannel(cluster, properties.getLeader().getTimeoutMs(),
                properties.getLeader().getMaxAttempts());
    }

    /**
     * Catalog writes performed by the leader, reads answered here. Built even on a shared
     * database, because leadership moves and a node without the handlers would refuse every
     * write; only used as the catalog store when there is something to keep in step.
     */
    @Bean(initMethod = "afterPropertiesSet", destroyMethod = "destroy")
    public LeaderCatalogStore leaderCatalogStore(
            @Qualifier("catalogStore") CatalogStore catalogStore, ClusterReplication replication,
            LeaderChannel leaderChannel) {
        ReplicationSink sink =
                replication.getRecords() != null ? replication.getRecords() : entry -> {};
        return new LeaderCatalogStore(catalogStore,
                new ReplicatedCatalogStore(catalogStore, sink), leaderChannel);
    }

    /**
     * What a node that has been away does about it: take the leader's table.
     *
     * <p>
     * Only when the table is one file per node. A shared database cannot fall behind itself, and
     * a timer pointed at it would spend every interval proving that.
     */
    @Bean(initMethod = "afterPropertiesSet", destroyMethod = "destroy")
    @ConditionalOnBean(GossipCluster.class)
    public CatalogCatchUp catalogCatchUp(GossipCluster cluster, LeaderCatalogStore store,
            @Qualifier("catalogStore") CatalogStore catalogStore, ClusterReplication replication,
            ClusterProperties properties) {
        return new CatalogCatchUp(cluster, store, catalogStore,
                replication.getRecords() != null ? properties.getLeader().getCatchUpIntervalMs()
                        : Long.MAX_VALUE);
    }

    /** Wraps the record store when the database is a file per node. */
    @Bean
    @Primary
    public ResourceRecordStore clusterRecordStore(
            @Qualifier("resourceRecordStore") ResourceRecordStore recordStore,
            ClusterReplication replication) {
        return replication.getRecords() != null
                ? new ReplicatedRecordStore(recordStore, replication.getRecords())
                : recordStore;
    }

    /**
     * Replay across the cluster, when the task pool can carry the slices; without it core's own
     * replay runs the whole thing here, correctly and more slowly. The bean name is passed in
     * because the pool addresses a method by it and a bean cannot ask Spring its own name.
     */
    @Bean
    @Primary
    @ConditionalOnBean(ProcessingPool.class)
    public ReplayService clusterReplayService(OutputFactory outputFactory,
            ResourceRecordStore recordStore, CatalogDetailsService catalogDetailsService,
            FileRestorer fileRestorer, CatalogStore catalogStore,
            // the plain method-dispatch pool, not the fork/join one: what travels here is a
            // range of pages, and splitting a range further would only add round trips
            @Qualifier("defaultProcessingPool") ProcessingPool pool,
            ObjectProvider<CrawlCluster> crawlCluster) {
        ClusterReplayService replayService = new ClusterReplayService(outputFactory, recordStore,
                catalogDetailsService, fileRestorer, catalogStore, pool, "clusterReplayService");
        // late, and through a provider: the cluster is a bean that depends on this one
        replayService.setAnnouncer((catalogId, version) -> crawlCluster.getObject()
                .announceRestoreFiles(catalogId, version));
        return replayService;
    }

    /**
     * A delete, performed by the leader.
     *
     * <p>
     * Primary over core's, and for the same reason every override here is: core declares its own
     * {@code @ConditionalOnMissingBean}, and that condition is met only by a bean registered
     * earlier, which auto-configuration by definition is not.
     */
    @Bean(initMethod = "afterPropertiesSet", destroyMethod = "destroy")
    @Primary
    public DeletionService leaderDeletionService(OutputFactory outputFactory,
            OutputProperties outputProperties, WebCrawlerProperties webCrawlerProperties,
            ResourceRecordStore recordStore, WebCrawlerSemaphore semaphore,
            CatalogStore catalogStore, CrawlReportStore crawlReportStore,
            DeletionBroadcast deletionBroadcast, LeaderChannel leaderChannel,
            CatalogDetailsService catalogDetailsService) {
        return new LeaderDeletionService(outputFactory, outputProperties, webCrawlerProperties,
                recordStore, semaphore, catalogStore, crawlReportStore, deletionBroadcast,
                leaderChannel, catalogDetailsService);
    }

    @Bean
    public ClusterConfigurationCheck clusterConfigurationCheck(
            ObjectProvider<GossipCluster> cluster, ObjectProvider<ProcessingCache> cache,
            ObjectProvider<CrawlCoordinatorFactory> coordinatorFactory) {
        return new ClusterConfigurationCheck(cluster, cache, coordinatorFactory);
    }

    /**
     * Replaces the standalone answer, which is a cluster of one.
     *
     * <p>
     * Named differently from the bean it replaces, and marked primary, because that is the shape
     * every override in this class has to take. Core's configuration is imported by the
     * application rather than auto-configured, so it is processed first: a same-named bean here is
     * not an override, it is a duplicate definition, and the application fails to start with
     * "a bean with that name has already been defined".
     */
    @Bean
    @Primary
    public ClusterSnapshot gossipClusterSnapshot(GossipCluster cluster) {
        return new GossipClusterSnapshot(cluster);
    }

    @Bean
    public ClusterStartupReport clusterStartupReport(GossipCluster cluster,
            OutputFactory outputFactory, Environment environment) {
        return new ClusterStartupReport(cluster, outputFactory, environment);
    }

}
