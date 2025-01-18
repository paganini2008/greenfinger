package com.github.greenfinger;

import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import com.github.doodler.common.transmitter.HashPartitioner;
import com.github.doodler.common.transmitter.MultipleChoicePartitioner;
import com.github.doodler.common.transmitter.Partitioner;
import com.github.doodler.common.utils.SerializableTaskTimer;
import com.github.greenfinger.components.DefaultWebCrawlerComponentFactory;
import com.github.greenfinger.components.WebCrawlerComponentFactory;

/**
 * 
 * @Description: GreenFingerAutoConfiguration
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@EnableElasticsearchRepositories("com.github.greenfinger.searcher")
@ComponentScan("com.github.greenfinger")
@EnableConfigurationProperties({WebCrawlerProperties.class, WebCrawlerExtractorProperties.class})
@Configuration(proxyBeanMethods = false)
public class GreenFingerAutoConfiguration {

    @Autowired
    public void addPartitioner(Partitioner partitioner) {
        if (!(partitioner instanceof MultipleChoicePartitioner)) {
            return;
        }
        MultipleChoicePartitioner multipleChoicePartitioner =
                (MultipleChoicePartitioner) partitioner;
        final String[] fieldNames = "catalogId,refer,path,version".split(",", 4);
        HashPartitioner hashPartitioner = new HashPartitioner(fieldNames);
        multipleChoicePartitioner.addPartitioner(hashPartitioner);
    }

    @Bean
    public SerializableTaskTimer taskTimer() {
        return new SerializableTaskTimer(5, 5, TimeUnit.SECONDS, false);
    }

    @Bean
    public WebCrawlerComponentFactory webCrawlerComponentFactory() {
        return new DefaultWebCrawlerComponentFactory();
    }

    @Bean
    public WebCrawlerSemaphore webCrawlerSemaphore() {
        return new WebCrawlerSemaphore();
    }

}
