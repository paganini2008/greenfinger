package indi.atlantis.framework.greenfinger.console.config;

import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import io.undertow.UndertowOptions;

/**
 * 
 * WebServerConfig
 *
 * @author Fred Feng
 * @since 1.0
 */
@Configuration
public class WebServerConfig {

	@Primary
	@Bean
	public UndertowServletWebServerFactory undertowServletWebServerFactory(Environment environment, ServerProperties serverProperties) {
		UndertowServletWebServerFactory serverFactory = new UndertowServletWebServerFactory();
		final int port = environment.getRequiredProperty("server.port", Integer.class);
		serverFactory.setPort(port);
		serverFactory.setIoThreads(Runtime.getRuntime().availableProcessors() * 2);
		serverFactory.setWorkerThreads(200);
		serverFactory.setUseDirectBuffers(true);
		serverFactory.setBufferSize(1024);
		serverFactory.addBuilderCustomizers((builder) -> {
			builder.setServerOption(UndertowOptions.MAX_ENTITY_SIZE, 100L * 1024 * 1024);
		});
		return serverFactory;
	}

}
