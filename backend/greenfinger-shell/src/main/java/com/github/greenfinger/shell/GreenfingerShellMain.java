/*
 * Copyright 2017-2026 Fred Feng (paganini.fy@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.greenfinger.shell;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.shell.core.command.annotation.EnableCommand;
import com.github.greenfinger.shell.command.CatalogCommands;
import com.github.greenfinger.shell.command.CrawlCommands;
import com.github.greenfinger.shell.command.QueryCommands;
import com.github.greenfinger.core.WebCrawlerExtractorProperties;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.service.EnableGreenfingerCrawler;

/**
 * The command line application.
 *
 * <p>
 * Run it with a command to do one thing and exit, or with no arguments to get a prompt.
 * 
 * @Description: GreenfingerShellMain
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@EnableGreenfingerCrawler
@EnableCommand({CrawlCommands.class, CatalogCommands.class, QueryCommands.class})
@EnableConfigurationProperties({WebCrawlerProperties.class, WebCrawlerExtractorProperties.class,
        OutputProperties.class})
@SpringBootApplication
public class GreenfingerShellMain {

    public static void main(String[] args) {
        // a command line crawler has no http endpoint to expose; starting as a plain application
        // keeps a servlet container off the classpath's critical path and out of the startup time
        ConfigurableApplicationContext context =
                new SpringApplicationBuilder(GreenfingerShellMain.class).web(WebApplicationType.NONE)
                        .run(CommandLine.toSpringArguments(args));
        // a command that failed has to say so to the shell as well as to the reader, and that is
        // an exit code. System.exit only on failure: the success path is left exactly as it was,
        // where the jvm ends on its own once the last non-daemon thread is done
        // a worker was stopped from outside: its shutdown hook has already closed the context,
        // and asking a closed one for its exit code only produces a stack trace on the way out
        int exitCode = context.isActive() ? SpringApplication.exit(context) : 0;
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

}
