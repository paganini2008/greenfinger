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

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import com.github.greenfinger.core.WebCrawlerExtractorProperties;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.WebCrawlerSemaphore;
import com.github.greenfinger.core.catalog.CatalogDetailsService;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.component.DefaultWebCrawlerComponentFactory;
import com.github.greenfinger.core.component.WebCrawlerComponentFactory;
import com.github.greenfinger.core.engine.CrawlCoordinatorFactory;
import com.github.greenfinger.core.engine.CrawlRegistry;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.core.report.CrawlReportStore;
import com.github.greenfinger.record.GreenfingerRecordConfiguration;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.vector.EmbeddingProperties;

/**
 * Wires the crawler: the engine, the outputs, persistence, and the services the command line and a
 * web front end both drive. Nothing here knows about http.
 *
 * <p>
 * Reached through {@link EnableGreenfingerCrawler} rather than by automatic discovery, so an
 * application that merely has the jar on its classpath starts nothing.
 * 
 * @Description: GreenfingerConfiguration
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Configuration(proxyBeanMethods = false)
@Import(GreenfingerRecordConfiguration.class)
@EnableConfigurationProperties({WebCrawlerProperties.class, WebCrawlerExtractorProperties.class,
        OutputProperties.class, EmbeddingProperties.class})
public class GreenfingerConfiguration {

    /**
     * Static, because a {@link org.springframework.beans.factory.config.BeanPostProcessor} has to
     * exist before the data source it corrects is created.
     */
    /**
     * Says at startup what every greenfinger setting ended up as, after the yaml, .env and the
     * command line have all had their say.
     */
    @Bean
    public ConfigurationReport configurationReport(
            org.springframework.context.ApplicationContext applicationContext) {
        return new ConfigurationReport(applicationContext);
    }

    @Bean
    public static SqliteConnectionPoolCustomizer sqliteConnectionPoolCustomizer() {
        return new SqliteConnectionPoolCustomizer();
    }

    @ConditionalOnMissingBean
    @Bean
    public WebCrawlerComponentFactory webCrawlerComponentFactory(
            WebCrawlerProperties webCrawlerProperties,
            WebCrawlerExtractorProperties extractorProperties) {
        return new DefaultWebCrawlerComponentFactory(webCrawlerProperties, extractorProperties);
    }

    @ConditionalOnMissingBean
    @Bean
    public OutputFactory outputFactory(OutputProperties outputProperties,
            EmbeddingProperties embeddingProperties) {
        return new OutputFactory(outputProperties, embeddingProperties);
    }

    /**
     * Loads the local models at startup when a vector output is configured, so a crawl does not
     * stall halfway through fetching them.
     */
    @ConditionalOnMissingBean
    @Bean
    public EmbeddingWarmUp embeddingWarmUp(EmbeddingProperties embeddingProperties,
            OutputProperties outputProperties, OutputFactory outputFactory,
            CatalogStore catalogStore) {
        return new EmbeddingWarmUp(embeddingProperties, outputProperties, outputFactory,
                catalogStore);
    }

    @ConditionalOnMissingBean
    @Bean
    public CrawlRegistry crawlRegistry() {
        return new CrawlRegistry();
    }

    @ConditionalOnMissingBean
    @Bean
    public WebCrawlerSemaphore webCrawlerSemaphore(CatalogStore catalogStore) {
        return new WebCrawlerSemaphore(catalogStore);
    }

    @ConditionalOnMissingBean
    @Bean
    public CatalogAdminService catalogAdminService(CatalogStore catalogStore,
            WebCrawlerProperties webCrawlerProperties, OutputProperties outputProperties,
            OutputFactory outputFactory, CrawlRegistry crawlRegistry) {
        return new CatalogAdminService(catalogStore, webCrawlerProperties, outputProperties,
                outputFactory, crawlRegistry);
    }

    @ConditionalOnMissingBean
    @Bean
    public DeletionService deletionService(OutputFactory outputFactory,
            OutputProperties outputProperties, WebCrawlerProperties webCrawlerProperties,
            ResourceRecordStore recordStore, WebCrawlerSemaphore semaphore,
            CatalogStore catalogStore, CrawlReportStore crawlReportStore) {
        return new DeletionService(outputFactory, outputProperties, webCrawlerProperties,
                recordStore, semaphore, catalogStore, crawlReportStore);
    }

    @ConditionalOnMissingBean
    @Bean
    public VersionPruner versionPruner(DeletionService deletionService) {
        return new VersionPruner(deletionService);
    }

    @ConditionalOnMissingBean
    @Bean
    public CrawlReportService crawlReportService(OutputFactory outputFactory,
            CatalogDetailsService catalogDetailsService, CatalogAdminService catalogAdminService,
            CrawlReportStore crawlReportStore, ResourceRecordStore recordStore) {
        return new CrawlReportService(outputFactory, catalogDetailsService, catalogAdminService,
                crawlReportStore, recordStore);
    }

    @ConditionalOnMissingBean
    @Bean
    public ReplayService replayService(OutputFactory outputFactory,
            ResourceRecordStore recordStore, CatalogDetailsService catalogDetailsService,
            FileRestorer fileRestorer) {
        return new ReplayService(outputFactory, recordStore, catalogDetailsService, fileRestorer);
    }

    @ConditionalOnMissingBean
    @Bean
    public FileRestorer fileRestorer(WebCrawlerComponentFactory componentFactory,
            WebCrawlerProperties webCrawlerProperties, ResourceRecordStore recordStore) {
        return new FileRestorer(componentFactory, webCrawlerProperties, recordStore);
    }

    /**
     * The local one, replaced by greenfinger-cluster when that module is on the classpath. It is
     * the whole of the difference between one process and a cluster.
     */
    @ConditionalOnMissingBean
    @Bean
    public CrawlCoordinatorFactory crawlCoordinatorFactory() {
        return CrawlCoordinatorFactory.local();
    }

    /**
     * One process is a cluster of one. greenfinger-cluster replaces this with the real thing.
     */
    @ConditionalOnMissingBean
    @Bean
    public ClusterSnapshot clusterSnapshot() {
        return ClusterSnapshot.standalone();
    }

    @ConditionalOnMissingBean
    @Bean
    public CrawlReportRecorder crawlReportRecorder(CrawlReportStore crawlReportStore,
            ResourceRecordStore recordStore, OutputProperties outputProperties,
            ClusterSnapshot clusterSnapshot,
            org.springframework.beans.factory.ObjectProvider<javax.sql.DataSource> dataSource) {
        return new CrawlReportRecorder(crawlReportStore, recordStore, outputProperties,
                clusterSnapshot, dataSource.getIfAvailable());
    }

    @ConditionalOnMissingBean
    @Bean
    public CrawlerLauncher crawlerLauncher(WebCrawlerProperties webCrawlerProperties,
            WebCrawlerExtractorProperties extractorProperties, OutputFactory outputFactory,
            CatalogStore catalogStore, CatalogDetailsService catalogDetailsService,
            ResourceRecordStore recordStore, CrawlRegistry crawlRegistry,
            WebCrawlerSemaphore semaphore, VersionPruner versionPruner,
            CrawlCoordinatorFactory coordinatorFactory,
            WebCrawlerComponentFactory componentFactory, CrawlReportRecorder reportRecorder,
            ApplicationEventPublisher eventPublisher) {
        return new CrawlerLauncher(webCrawlerProperties, extractorProperties, outputFactory,
                catalogStore, catalogDetailsService, recordStore, crawlRegistry, semaphore,
                versionPruner, coordinatorFactory, componentFactory, reportRecorder,
                eventPublisher);
    }

}
