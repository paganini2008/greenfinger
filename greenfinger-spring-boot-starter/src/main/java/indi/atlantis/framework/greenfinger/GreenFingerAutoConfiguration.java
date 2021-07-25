/**
* Copyright 2017-2021 Fred Feng (paganini.fy@gmail.com)

* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package indi.atlantis.framework.greenfinger;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.client.ClientHttpRequestFactory;

import com.github.paganini2008.devtools.date.DateUtils;
import com.github.paganini2008.springdessert.reditools.common.IdGenerator;
import com.github.paganini2008.springdessert.reditools.common.TimeBasedIdGenerator;

import indi.atlantis.framework.greenfinger.es.IndexedResourceService;
import indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger;
import indi.atlantis.framework.vortex.common.HashPartitioner;
import indi.atlantis.framework.vortex.common.NamedSelectionPartitioner;
import indi.atlantis.framework.vortex.common.Partitioner;

/**
 * 
 * GreenFingerAutoConfiguration
 *
 * @author Fred Feng
 * 
 * @since 1.0
 */
@EnableElasticsearchRepositories("indi.atlantis.framework.greenfinger.es")
@Configuration
public class GreenFingerAutoConfiguration {

	@Value("${spring.application.cluster.name}")
	private String clusterName;

	@Bean
	public CrawlerLauncher crawlerLauncher() {
		return new CrawlerLauncher();
	}

	@Bean
	public PathFilterFactory pathFilterFactory(RedisConnectionFactory redisConnectionFactory) {
		return new BloomFilterPathFilterFactory(clusterName, redisConnectionFactory);
	}

	@Bean
	public CrawlerHandler crawlerHandler() {
		return new CrawlerHandler();
	}

	@Autowired
	public void addPartitioner(Partitioner partitioner) {
		NamedSelectionPartitioner namedSelectionPartitioner = (NamedSelectionPartitioner) partitioner;
		final String[] fieldNames = "catalogId,refer,path,version".split(",", 4);
		HashPartitioner hashPartitioner = new HashPartitioner(fieldNames);
		namedSelectionPartitioner.addPartitioner(hashPartitioner);
	}

	@ConditionalOnMissingBean
	@Bean
	public ResourceManager resourceService() {
		return new JdbcResourceManger();
	}

	@ConditionalOnMissingBean
	@Bean
	public PageExtractor pageExtractor(ClientHttpRequestFactory clientHttpRequestFactory) {
		return new MultiCharsetHttpClientPageExtractor(clientHttpRequestFactory);
	}

	@ConditionalOnMissingBean
	@Bean
	public IdGenerator timestampIdGenerator(RedisConnectionFactory redisConnectionFactory) {
		final String keyPrefix = String.format("spring:application:cluster:%s:id:", clusterName);
		return new TimeBasedIdGenerator(keyPrefix, redisConnectionFactory);
	}

	@ConditionalOnMissingBean
	@Bean
	public Condition conditionalTermination(CrawlerSummary crawlerSummary) {
		return new CountingCondition(crawlerSummary, DateUtils.convertToMillis(20, TimeUnit.MINUTES), 100000);
	}

	@ConditionalOnMissingBean
	@Bean
	public PathAcceptor pathAcceptor() {
		return new DefaultPathAcceptor();
	}

	@Bean
	public IndexedResourceService indexedResourceService() {
		return new IndexedResourceService();
	}

	@Bean
	public CrawlerSummary crawlerSummary(RedisConnectionFactory redisConnectionFactory) {
		return new CrawlerSummary(clusterName, redisConnectionFactory);
	}

}
