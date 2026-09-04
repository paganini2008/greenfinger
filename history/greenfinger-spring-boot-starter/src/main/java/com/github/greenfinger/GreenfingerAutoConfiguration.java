/*
 * Copyright 2017-2025 Fred Feng (paganini.fy@gmail.com)
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

package com.github.greenfinger;

import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.redis.core.RedisTemplate;
import com.github.doodler.common.transmitter.HashPartitioner;
import com.github.doodler.common.transmitter.MultipleChoicePartitioner;
import com.github.doodler.common.transmitter.Partitioner;
import com.github.doodler.common.utils.SerializableTaskTimer;
import com.github.greenfinger.components.DashboardFactory;
import com.github.greenfinger.components.DefaultWebCrawlerComponentFactory;
import com.github.greenfinger.components.RedisDashboardFactory;
import com.github.greenfinger.components.WebCrawlerComponentFactory;

/**
 * 
 * @Description: GreenfingerAutoConfiguration
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@EnableElasticsearchRepositories("com.github.greenfinger.searcher")
@ComponentScan("com.github.greenfinger")
@EnableConfigurationProperties({WebCrawlerProperties.class, WebCrawlerExtractorProperties.class})
@Configuration(proxyBeanMethods = false)
public class GreenfingerAutoConfiguration {

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

    @ConditionalOnMissingBean
    @Bean
    public WebCrawlerComponentFactory webCrawlerComponentFactory() {
        return new DefaultWebCrawlerComponentFactory();
    }

    @Bean
    public WebCrawlerSemaphore webCrawlerSemaphore() {
        return new WebCrawlerSemaphore();
    }

    @ConditionalOnMissingBean
    @Bean
    public DashboardFactory defaultDashboardFactory(RedisTemplate<String, Object> redisTemplate) {
        return new RedisDashboardFactory(redisTemplate);
    }

}
