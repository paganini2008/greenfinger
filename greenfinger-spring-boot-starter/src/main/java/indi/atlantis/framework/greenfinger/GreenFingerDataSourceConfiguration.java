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

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * GreenFingerDataSourceConfiguration
 *
 * @author Fred Feng
 *
 * @since 2.0.4
 */
@Slf4j
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@Configuration(proxyBeanMethods = false)
public class GreenFingerDataSourceConfiguration {

	@Getter
	@Setter
	@ToString
	@ConfigurationProperties(prefix = "atlantis.framework.greenfinger.datasource")
	public static class DataSourceSettings {
		private String jdbcUrl;
		private String username;
		private String password;
		private String driverClassName;
		private int maxPoolSize = 16;

		private Map<String, String> settings = new HashMap<String, String>();
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(HikariDataSource.class)
	@EnableConfigurationProperties(GreenFingerDataSourceConfiguration.DataSourceSettings.class)
	@ConditionalOnMissingBean(DataSource.class)
	public static class DefaultDataSourceConfiguration {

		@Bean
		public HikariConfig hikariConfig(DataSourceSettings dataSourceSettings) {
			if (log.isTraceEnabled()) {
				log.trace("HikariDataSource DataSourceSettings: " + dataSourceSettings);
			}
			final HikariConfig config = new HikariConfig();
			config.setDriverClassName(dataSourceSettings.getDriverClassName());
			config.setJdbcUrl(dataSourceSettings.getJdbcUrl());
			config.setUsername(dataSourceSettings.getUsername());
			config.setPassword(dataSourceSettings.getPassword());
			config.setMinimumIdle(1);
			config.setMaximumPoolSize(dataSourceSettings.getMaxPoolSize());
			config.setMaxLifetime(3 * 60 * 1000);
			config.setIdleTimeout(60 * 1000);
			config.setValidationTimeout(3000);
			config.setReadOnly(false);
			config.setConnectionInitSql("SELECT 1");
			config.setConnectionTestQuery("SELECT 1");
			config.setConnectionTimeout(60 * 1000);
			config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");

			config.addDataSourceProperty("cachePrepStmts", "true");
			config.addDataSourceProperty("prepStmtCacheSize", "250");
			config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
			return config;
		}

		@Bean
		public DataSource dataSource(DataSourceSettings dataSourceSettings) {
			return new HikariDataSource(hikariConfig(dataSourceSettings));
		}

	}

}
