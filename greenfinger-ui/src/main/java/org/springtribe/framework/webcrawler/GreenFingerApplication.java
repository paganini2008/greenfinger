package org.springtribe.framework.webcrawler;

import java.io.File;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springtribe.framework.jdbc.annotations.DaoScan;

import com.github.paganini2008.devtools.io.FileUtils;

/**
 * 
 * GreenFingerApplication
 *
 * @author Jimmy Hoff
 * 
 * @since 1.0
 */
@DaoScan(basePackages = "org.springtribe.framework.greenfinger.jdbc")
@SpringBootApplication
@ComponentScan(basePackages = { "org.springtribe.framework.webcrawler"})
public class GreenFingerApplication {

	static {
		System.setProperty("spring.devtools.restart.enabled", "false");
		File logDir = FileUtils.getFile(FileUtils.getUserDirectory(), "logs", "springworld", "examples");
		if (!logDir.exists()) {
			logDir.mkdirs();
		}
		System.setProperty("DEFAULT_LOG_BASE", logDir.getAbsolutePath());
	}

	public static void main(String[] args) {
		// final int port =
		// NetUtils.getRandomPort(Constants.MICROSERVICE_RANDOM_PORT_START,
		// Constants.MICROSERVICE_BIZ_RANDOM_PORT_END);
		int port = 8022;
		System.out.println("Server Port: " + port);
		System.setProperty("server.port", String.valueOf(port));
		SpringApplication.run(GreenFingerApplication.class, args);
		System.out.println(Env.getPid());
	}
}
