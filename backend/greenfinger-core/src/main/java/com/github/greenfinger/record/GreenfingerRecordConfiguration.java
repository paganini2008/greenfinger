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

package com.github.greenfinger.record;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.catalog.CatalogDetailsService;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.core.report.CrawlReportStore;

/**
 * Persistence, which 2.0 always needs.
 *
 * <p>
 * 1.x could not crawl at all until a database was installed and a schema loaded. The zero-install
 * promise is kept instead by defaulting to an H2 file beside the output: still a real database,
 * still the same code path, but nothing to set up.
 * 
 * @Description: GreenfingerRecordConfiguration
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Configuration(proxyBeanMethods = false)
@EntityScan(basePackages = "com.github.greenfinger.core.model")
@EnableJpaRepositories(basePackages = "com.github.greenfinger.record")
public class GreenfingerRecordConfiguration {

    @ConditionalOnMissingBean
    @Bean
    public CatalogStore catalogStore(CatalogRepository catalogRepository) {
        return new JpaCatalogStore(catalogRepository);
    }

    @ConditionalOnMissingBean
    @Bean
    public CrawlReportStore crawlReportStore(CrawlerReportRepository crawlerReportRepository) {
        return new JpaCrawlReportStore(crawlerReportRepository);
    }

    @ConditionalOnMissingBean
    @Bean
    public CatalogDetailsService catalogDetailsService(CatalogStore catalogStore,
            WebCrawlerProperties webCrawlerProperties) {
        return new DatabaseCatalogDetailsService(catalogStore, webCrawlerProperties);
    }

    /**
     * Shared image rows are written in their own transaction, so that two crawl threads racing on
     * the same picture do not poison the page transaction they are part of. SQLite is the one
     * database where that is the wrong shape: see {@link ImageWriter}.
     */
    @ConditionalOnMissingBean
    @Bean
    public ImageWriter imageWriter(ImageRepository imageRepository,
            ResourceImageRepository resourceImageRepository,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            org.springframework.core.env.Environment environment) {
        return new ImageWriter(imageRepository, resourceImageRepository, transactionManager,
                !isSqlite(environment));
    }

    /**
     * From the configured url rather than from the connection, so no database is opened to decide
     * how beans are wired. An application that builds its own data source leaves the property
     * empty and gets the default, which is right for every database but one.
     */
    private static boolean isSqlite(org.springframework.core.env.Environment environment) {
        String url = environment.getProperty("spring.datasource.url", "");
        return url.startsWith("jdbc:sqlite:");
    }

    @ConditionalOnMissingBean
    @Bean
    public ResourceRecordStore resourceRecordStore(ResourceRepository resourceRepository,
            ImageRepository imageRepository, ResourceImageRepository resourceImageRepository,
            ImageWriter imageWriter) {
        return new JpaResourceRecordStore(resourceRepository, imageRepository,
                resourceImageRepository, imageWriter);
    }

}
