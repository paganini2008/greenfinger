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

import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.shell.core.SystemShellRunner;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.stereotype.Component;
import com.github.greenfinger.shell.command.CrawlCommands;
import com.github.greenfinger.shell.render.Ansi;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.core.catalog.CatalogDetailsNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs a single command given on the command line, then lets the application exit.
 *
 * <p>
 * With no command it opens the prompt instead, so the same executable serves both
 * {@code greenfinger-cli.sh catalog-crawl --id=x} and {@code greenfinger-face.sh}.
 *
 * <p>
 * It has to open the prompt itself rather than standing aside for Spring Shell's own runner, and
 * this is not a preference. That runner is declared {@code @ConditionalOnMissingBean} on
 * {@link ApplicationRunner} -- so the moment this class exists, it backs off, and the shell is
 * never started at all. The application then boots, runs no command, and exits with nothing to
 * say. Reproduced by typing {@code face} and watching a jvm start for twenty seconds and stop.
 *
 * <p>
 * A mistyped catalog name is a mistake, not a defect, and printing a stack trace for it buries the
 * one line that matters. So the two exceptions that mean "what you typed does not work" --
 * {@link WebCrawlerException} and {@link IllegalArgumentException}, both of which carry a message
 * written for a person -- become a single red line and exit code 1. Everything else still gets its
 * stack trace, because everything else is a bug and the trace is the report.
 * 
 * @Description: OneShotCommandRunner
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
@RequiredArgsConstructor
public class OneShotCommandRunner implements ApplicationRunner, ExitCodeGenerator {

    static final int FAILED = 1;

    private final CrawlCommands crawlCommands;

    /**
     * What the shell needs to run commands. Assembled here rather than injected as a
     * {@code ShellRunner}, because the runner the autoconfiguration builds is given whichever
     * {@code ConsoleInputProvider} bean resolution hands it, and that turned out not to be ours --
     * so the prompt stayed Spring Shell's {@code $>} however the bean was declared. Built from
     * these two, it is ours by construction.
     */
    private final ObjectProvider<CommandParser> commandParser;
    private final ObjectProvider<CommandRegistry> commandRegistry;

    /**
     * A worker node has nobody at its keyboard: it was forked to take urls from the cluster, its
     * standard input is a redirect, and a prompt opened on it reads one end-of-file and closes.
     * Harmless, and still wrong -- {@link WorkerNodeRunner} is what keeps a worker alive, and the
     * prompt has no part in it.
     */
    @org.springframework.beans.factory.annotation.Value("${greenfinger.shell.worker:false}")
    private boolean worker;

    private int exitCode = 0;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        CommandLine commandLine = CommandLine.parse(args.getSourceArgs());
        if (!commandLine.hasCommand()) {
            openThePrompt(args);
            return;
        }
        try {
            crawlCommands.dispatch(commandLine.getCommand(), commandLine.getPrimaryCommand(),
                    commandLine.getOptions());
        } catch (WebCrawlerException | IllegalArgumentException e) {
            reportToUser(e);
        }
    }

    /**
     * Hands over to the shell, which then owns this thread until the reader leaves.
     *
     * <p>
     * Only when it is the interactive one. A worker node has no command on the line either, and
     * would otherwise land here and open a prompt nobody is typing at.
     */
    private void openThePrompt(ApplicationArguments args) throws Exception {
        if (worker) {
            return;
        }
        CommandParser parser = commandParser.getIfAvailable();
        CommandRegistry registry = commandRegistry.getIfAvailable();
        if (parser == null || registry == null) {
            // a worker node: no prompt, and something else is keeping this process alive
            return;
        }
        new SystemShellRunner(new GreenfingerPrompt(), parser, registry)
                .run(args.getSourceArgs());
    }

    /**
     * The stack trace is not thrown away, only moved out of the way: it is still there at debug
     * level for whoever needs to see where the message came from.
     */
    private void reportToUser(RuntimeException e) {
        // the first line is what went wrong and the rest, when there is a rest, is what to type
        // instead -- dim, so the eye lands on the red line first
        String[] lines = messageOf(e).split("\\R");
        System.err.println(Ansi.red(lines[0]));
        for (int i = 1; i < lines.length; i++) {
            System.err.println(Ansi.dim(lines[i]));
        }
        hintFor(e).ifPresent(hint -> System.err.println(Ansi.dim(hint)));
        log.debug("Command failed", e);
        exitCode = FAILED;
    }

    static String messageOf(Throwable e) {
        String message = e.getMessage();
        // an exception raised without a message still has to say something; the type is the only
        // thing left that carries any meaning
        return message != null && !message.isBlank() ? message : e.getClass().getSimpleName();
    }

    static Optional<String> hintFor(Throwable e) {
        if (e instanceof CatalogDetailsNotFoundException) {
            return Optional.of("Run 'catalogs' to see the names that exist.");
        }
        return Optional.empty();
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

}
