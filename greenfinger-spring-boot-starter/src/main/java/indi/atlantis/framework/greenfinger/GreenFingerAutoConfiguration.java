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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import com.github.paganini2008.devtools.date.DateUtils;
import com.github.paganini2008.springdessert.reditools.common.IdGenerator;
import com.github.paganini2008.springdessert.reditools.common.TimeBasedIdGenerator;

import indi.atlantis.framework.chaconne.JobManager;
import indi.atlantis.framework.greenfinger.api.CatalogApiController;
import indi.atlantis.framework.greenfinger.api.IndexApiController;
import indi.atlantis.framework.greenfinger.es.ResourceIndexService;
import indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger;
import indi.atlantis.framework.greenfinger.job.CatalogIndexJobService;
import indi.atlantis.framework.vortex.common.HashPartitioner;
import indi.atlantis.framework.vortex.common.NamedSelectionPartitioner;
import indi.atlantis.framework.vortex.common.Partitioner;

/**
 * 
 * GreenFingerAutoConfiguration
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
 */
@EnableElasticsearchRepositories("indi.atlantis.framework.greenfinger.es")
@Import({ CatalogApiController.class, IndexApiController.class })
@Configuration(proxyBeanMethods = false)
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
	public ResourceManager resourceManager() {
		return new JdbcResourceManger();
	}

	@ConditionalOnMissingBean
	@Bean
	public PageExtractor pageExtractor(@Value("${atlantis.framework.greenfinger.http.proxyAddress:}") String proxyAddress)
			throws Exception {
		HtmlUnitPageExtractor pageExtractor = new HtmlUnitPageExtractor();
		pageExtractor.setProxyAddress(proxyAddress);
		pageExtractor.configure();
		return new ThreadWaitPageExtractor(pageExtractor);
	}

	@ConditionalOnMissingBean
	@Bean
	public IdGenerator timestampIdGenerator(RedisConnectionFactory redisConnectionFactory) {
		final String keyPrefix = String.format("spring:application:cluster:%s:id:", clusterName);
		return new TimeBasedIdGenerator(keyPrefix, redisConnectionFactory);
	}

	@ConditionalOnMissingBean
	@Bean
	public Condition defaultCondition(CrawlerStatistics crawlerStatistics) {
		return new CounterCondition(crawlerStatistics, DateUtils.convertToMillis(20, TimeUnit.MINUTES), 100000);
	}

	@Bean
	public PathAcceptorContainer pathAcceptorContainer() {
		return new PathAcceptorContainer();
	}

	@Bean
	public PathAcceptor defaultPathAcceptor() {
		return new DefaultPathAcceptor();
	}

	@Bean
	public ResourceIndexService resourceIndexService() {
		return new ResourceIndexService();
	}

	@Bean
	public CrawlerStatistics crawlerStatistics(RedisConnectionFactory redisConnectionFactory) {
		return new CrawlerStatistics(clusterName, redisConnectionFactory);
	}

	@Bean
	public CatalogAdminService catalogAdminService() {
		return new CatalogAdminService();
	}

	@ConditionalOnBean(JobManager.class)
	@Bean
	public CatalogIndexJobService catalogIndexJobService() {
		return new CatalogIndexJobService();
	}

}
