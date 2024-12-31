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
import com.github.greenfinger.es.ResourceIndexService;
import com.github.greenfinger.jdbc.JdbcResourceManger;
import com.github.greenfinger.test.CompositeCatalogUrlPathAcceptor;
import com.github.greenfinger.test.ConditionalCountingType;
import com.github.greenfinger.test.CrawlerHandler;
import com.github.greenfinger.test.DefaultCatalogUrlPathAcceptor;
import com.github.greenfinger.test.DepthUrlPathAcceptor;
import com.github.greenfinger.test.DurationCondition;
import com.github.greenfinger.test.FetchSizeLimitCondition;
import com.github.greenfinger.test.InterruptibleCondition;
import com.github.greenfinger.test.WebCrawlerService;
import com.github.greenfinger.utils.HtmlUnitPageSourceExtractor;
import com.github.greenfinger.utils.PageSourceExtractor;
import com.github.greenfinger.utils.ThreadWaitPageExtractor;

/**
 * 
 * @Description: GreenFingerAutoConfiguration
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@EnableElasticsearchRepositories("io.atlantisframework.greenfinger.es")
@Import({CatalogApiController.class, IndexApiController.class})
@EnableConfigurationProperties({WebCrawlerProperties.class})
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
    public PageSourceExtractor pageExtractor(WebCrawlerProperties config) {
        HtmlUnitPageSourceExtractor pageExtractor = new HtmlUnitPageSourceExtractor(config);
        return new ThreadWaitPageExtractor(pageExtractor);
    }

    @Bean
    public InterruptibleCondition durationCondition(WebCrawlerProperties config) {
        return new DurationCondition(config);
    }

    @Bean
    public InterruptibleCondition fetchSizeLimitCondition(WebCrawlerProperties config) {
        return new FetchSizeLimitCondition(config, ConditionalCountingType.URL_TOTAL_COUNT);
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
    public UrlPathAcceptor depthCatalogUrlPathAcceptor(WebCrawlerProperties config) {
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
