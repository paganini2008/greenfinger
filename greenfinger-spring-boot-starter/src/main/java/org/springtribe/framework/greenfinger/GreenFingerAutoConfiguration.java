package org.springtribe.framework.greenfinger;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springtribe.framework.gearless.common.HashPartitioner;
import org.springtribe.framework.gearless.common.Partitioner;
import org.springtribe.framework.gearless.common.RoundRobinPartitioner;
import org.springtribe.framework.greenfinger.es.IndexedResourceService;
import org.springtribe.framework.greenfinger.jdbc.JdbcResourceManger;
import org.springtribe.framework.reditools.common.IdGenerator;
import org.springtribe.framework.reditools.common.TimestampIdGenerator;

import com.github.paganini2008.devtools.date.DateUtils;

/**
 * 
 * GreenFingerAutoConfiguration
 *
 * @author Jimmy Hoff
 * 
 * @since 1.0
 */
@EnableElasticsearchRepositories("org.springtribe.framework.greenfinger.es")
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

	@Primary
	@Bean
	public Partitioner partitioner() {
		return new HashPartitioner("catalogId,refer,path,version".split(","));
	}

	@Bean
	public Partitioner secondaryPartitioner() {
		return new RoundRobinPartitioner();
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
		return new TimestampIdGenerator(keyPrefix, redisConnectionFactory);
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
