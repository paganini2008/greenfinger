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
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.shell.core.command.annotation.EnableCommand;
import com.github.greenfinger.core.WebCrawlerExtractorProperties;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.shell.command.CatalogCommands;
import com.github.greenfinger.shell.command.CrawlCommands;
import com.github.greenfinger.shell.command.QueryCommands;

/**
 * One jar, two faces.
 *
 * <p>
 * With {@code --greenfinger.shell.client=true} it is the terminal: it joins the cluster under its
 * own application name, runs no crawl, and asks the leader for everything. Without it, it is a
 * crawler node with a command line on it, which is what runs a verb given on the line.
 *
 * <p>
 * Which one it is decides whether the engine and the database are wired at all, so it is a
 * condition on the two configurations rather than a branch inside one: {@link CrawlerNodeMode}
 * and {@link ShellClientMode} are mutually exclusive, and whichever is not in use contributes
 * nothing.
 *
 * @Description: GreenfingerShellMain
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@EnableCommand({CrawlCommands.class, CatalogCommands.class, QueryCommands.class})
@EnableConfigurationProperties({WebCrawlerProperties.class, WebCrawlerExtractorProperties.class,
        OutputProperties.class})
@SpringBootApplication
public class GreenfingerShellMain {

    /** What the launcher sets to ask for the terminal rather than a crawler. */
    public static final String CLIENT_PROPERTY = "greenfinger.shell.client";

    public static void main(String[] args) {
        // a command line crawler has no http endpoint to expose; starting as a plain application
        // keeps a servlet container off the classpath's critical path and out of the startup time
        ConfigurableApplicationContext context =
                new SpringApplicationBuilder(GreenfingerShellMain.class)
                        .web(WebApplicationType.NONE)
                        .properties(CLIENT_PROPERTY + "=" + isClient(args))
                        .run(CommandLine.toSpringArguments(args));
        // a command that failed has to say so to the shell as well as to the reader, and that is
        // an exit code. System.exit only on failure: the success path is left exactly as it was,
        // where the jvm ends on its own once the last non-daemon thread is done.
        // A worker stopped from outside has had its context closed by the shutdown hook already,
        // and asking a closed one for its exit code only produces a stack trace on the way out
        int exitCode = context.isActive() ? SpringApplication.exit(context) : 0;
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Read off the raw line rather than from the environment: it decides which configuration is
     * built, and a condition is evaluated before the arguments have become properties.
     */
    static boolean isClient(String[] args) {
        if (Boolean.getBoolean(CLIENT_PROPERTY)) {
            return true;
        }
        for (String arg : args) {
            if (arg == null) {
                continue;
            }
            String value = arg.trim();
            if (value.equals("--" + CLIENT_PROPERTY)
                    || value.equalsIgnoreCase("--" + CLIENT_PROPERTY + "=true")) {
                return true;
            }
        }
        return false;
    }

}
