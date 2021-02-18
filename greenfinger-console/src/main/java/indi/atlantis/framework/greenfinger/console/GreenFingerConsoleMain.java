package indi.atlantis.framework.greenfinger.console;

import java.io.File;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.github.paganini2008.devtools.Env;
import com.github.paganini2008.devtools.io.FileUtils;

import indi.atlantis.framework.greenfinger.EnableGreenFingerServer;

/**
 * 
 * GreenFingerConsoleMain
 *
 * @author Jimmy Hoff
 * 
 * @since 1.0
 */
@EnableGreenFingerServer
@SpringBootApplication
public class GreenFingerConsoleMain {

	static {
		System.setProperty("spring.devtools.restart.enabled", "false");
		File logDir = FileUtils.getFile(FileUtils.getUserDirectory(), "logs", "springworld", "examples");
		if (!logDir.exists()) {
			logDir.mkdirs();
		}
		System.setProperty("LOG_BASE", logDir.getAbsolutePath());
	}

	public static void main(String[] args) {
		SpringApplication.run(GreenFingerConsoleMain.class, args);
		System.out.println(Env.getPid());
	}
}
