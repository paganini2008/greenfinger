/**
* Copyright 2018-2021 Fred Feng (paganini.fy@gmail.com)

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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jmx.support.ConnectorServerFactoryBean;
import org.springframework.remoting.rmi.RmiRegistryFactoryBean;

import com.github.paganini2008.devtools.net.NetUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * JmxConfig
 *
 * @author Fred Feng
 * @since 1.0
 */
@Slf4j
@Configuration
public class JmxConfig {

	public JmxConfig() {
		this.rmiHost = NetUtils.getLocalHost();
		this.rmiPort = NetUtils.getRandomPort(51000, 52000);
	}

	private String rmiHost;
	private Integer rmiPort;

	@Bean
	public RmiRegistryFactoryBean rmiRegistry() {
		final RmiRegistryFactoryBean rmiRegistryFactoryBean = new RmiRegistryFactoryBean();
		rmiRegistryFactoryBean.setPort(rmiPort);
		rmiRegistryFactoryBean.setAlwaysCreate(true);
		return rmiRegistryFactoryBean;
	}

	@Bean
	@DependsOn("rmiRegistry")
	public ConnectorServerFactoryBean connectorServerFactoryBean() throws Exception {
		final ConnectorServerFactoryBean connectorServerFactoryBean = new ConnectorServerFactoryBean();
		connectorServerFactoryBean.setObjectName("connector:name=rmi");
		final String jmxServiceUrl = String.format("service:jmx:rmi://%s:%s/jndi/rmi://%s:%s/jmxrmi", rmiHost, rmiPort, rmiHost,
				rmiPort);
		connectorServerFactoryBean.setServiceUrl(jmxServiceUrl);
		log.info("JmxServiceUrl: " + jmxServiceUrl);
		log.info("JmxConfigBean create successfully.");
		return connectorServerFactoryBean;
	}
}
