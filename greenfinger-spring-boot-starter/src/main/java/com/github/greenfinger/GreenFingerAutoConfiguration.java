package com.github.greenfinger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import com.github.doodler.common.transmitter.HashPartitioner;
import com.github.doodler.common.transmitter.MultipleChoicePartitioner;
import com.github.doodler.common.transmitter.Partitioner;
import com.github.greenfinger.api.CatalogApiController;
import com.github.greenfinger.api.IndexApiController;
import com.github.greenfinger.components.CompositeCatalogUrlPathAcceptor;
import com.github.greenfinger.components.ConditionalCountingType;
import com.github.greenfinger.components.DefaultCatalogUrlPathAcceptor;
import com.github.greenfinger.components.DepthUrlPathAcceptor;
import com.github.greenfinger.components.DurationInterruptionChecker;
import com.github.greenfinger.components.Extractor;
import com.github.greenfinger.components.HtmlUnitExtractor;
import com.github.greenfinger.components.InterruptionChecker;
import com.github.greenfinger.components.MaxFetchSizeInterruptionChecker;
import com.github.greenfinger.components.ThreadWaitExtractor;
import com.github.greenfinger.components.UrlPathAcceptor;
import com.github.greenfinger.es.ResourceIndexService;
import com.github.greenfinger.jdbc.JdbcResourceManger;

/**
 * 
 * @Description: GreenFingerAutoConfiguration
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@EnableElasticsearchRepositories("com.github.greenfinger.es")
@Import({CatalogApiController.class, IndexApiController.class})
@EnableConfigurationProperties({WebCrawlerExtractorProperties.class})
@Configuration(proxyBeanMethods = false)
public class GreenFingerAutoConfiguration {

    @Bean
    public WebCrawlerService crawlerLauncher() {
        return new WebCrawlerService();
    }

    @Bean
    public CrawlerHandler crawlerHandler() {
        return new CrawlerHandler();
    }

    @Autowired
    public void addPartitioner(Partitioner partitioner) {
        MultipleChoicePartitioner multipleChoicePartitioner =
                (MultipleChoicePartitioner) partitioner;
        final String[] fieldNames = "catalogId,refer,path,version".split(",", 4);
        HashPartitioner hashPartitioner = new HashPartitioner(fieldNames);
        multipleChoicePartitioner.addPartitioner(hashPartitioner);
    }

    @ConditionalOnMissingBean
    @Bean
    public ResourceManager resourceManager() {
        return new JdbcResourceManger();
    }

    @ConditionalOnMissingBean
    @Bean
    public Extractor extractor(WebCrawlerExtractorProperties config) {
        HtmlUnitExtractor pageExtractor = new HtmlUnitExtractor(config);
        return new ThreadWaitExtractor(pageExtractor);
    }

    @Bean
    public InterruptionChecker durationInterruptionChecker(WebCrawlerExtractorProperties config) {
        return new DurationInterruptionChecker(config);
    }

    @Bean
    public InterruptionChecker maxFetchSizeInterruptionChecker(WebCrawlerExtractorProperties config) {
        return new MaxFetchSizeInterruptionChecker(config, ConditionalCountingType.URL_TOTAL_COUNT);
    }

    @Bean
    public UrlPathAcceptor compositeCatalogUrlPathAcceptor(ResourceManager resourceManager) {
        return new CompositeCatalogUrlPathAcceptor(resourceManager);
    }

    @Bean
    public UrlPathAcceptor defaultCatalogUrlPathAcceptor(ResourceManager resourceManager) {
        return new DefaultCatalogUrlPathAcceptor(resourceManager);
    }

    @Bean
    public UrlPathAcceptor depthCatalogUrlPathAcceptor(WebCrawlerExtractorProperties config) {
        return new DepthUrlPathAcceptor(config);
    }

    @Bean
    public ResourceIndexService resourceIndexService() {
        return new ResourceIndexService();
    }

    @Bean
    public CatalogAdminService catalogAdminService() {
        return new CatalogAdminService();
    }

}
