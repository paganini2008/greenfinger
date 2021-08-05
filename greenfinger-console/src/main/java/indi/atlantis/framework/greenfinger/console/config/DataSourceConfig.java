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
package indi.atlantis.framework.greenfinger.console.config;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.github.paganini2008.springdessert.reditools.common.EnableRedisClient;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * DataSourceConfig
 *
 * @author Fred Feng
 * @since 2.0.1
 */
@Slf4j
@Setter
@Getter
@EnableRedisClient
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "spring.datasource")
public class DataSourceConfig {

	private String jdbcUrl;
	private String username;
	private String password;
	private String driverClassName;

	private HikariConfig getDbConfig() {
		if (log.isTraceEnabled()) {
			log.trace("DataSourceConfig JdbcUrl: " + jdbcUrl);
			log.trace("DataSourceConfig Username: " + username);
			log.trace("DataSourceConfig Password: " + password);
			log.trace("DataSourceConfig DriverClassName: " + driverClassName);
		}
		final HikariConfig config = new HikariConfig();
		config.setDriverClassName(driverClassName);
		config.setJdbcUrl(jdbcUrl);
		config.setUsername(username);
		config.setPassword(password);
		config.setMinimumIdle(5);
		config.setMaximumPoolSize(50);
		config.setMaxLifetime(60 * 1000);
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

	@Primary
	@Bean
	public DataSource defaultDataSource() {
		return new HikariDataSource(getDbConfig());
	}

}
