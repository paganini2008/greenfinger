/**
* Copyright 2017-2022 Fred Feng (paganini.fy@gmail.com)

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
package io.atlantisframework.greenfinger.console;

import java.io.File;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import com.github.paganini2008.devtools.Env;
import com.github.paganini2008.devtools.io.FileUtils;

import io.atlantisframework.greenfinger.EnableGreenFingerServer;

/**
 * 
 * GreenFingerServerConsoleMain
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
 */
@EnableGreenFingerServer
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class GreenFingerServerConsoleMain {

	static {
		System.setProperty("es.set.netty.runtime.available.processors", "false");
		System.setProperty("spring.devtools.restart.enabled", "false");
		File logDir = FileUtils.getFile(FileUtils.getUserDirectory(), "logs", "io", "atlantisframework", "greenfinger", "console");
		if (!logDir.exists()) {
			logDir.mkdirs();
		}
		System.setProperty("LOG_BASE", logDir.getAbsolutePath());

	}

	public static void main(String[] args) {
		SpringApplication.run(GreenFingerServerConsoleMain.class, args);
		System.out.println(Env.getPid());
	}
}
