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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import com.github.greenfinger.shell.command.CrawlCommands;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.core.catalog.CatalogDetailsNotFoundException;

/**
 * What the user sees when a command fails.
 *
 * <p>
 * The point of every one of these is the same: a mistake made while typing should read as one
 * line, and a defect should still read as a stack trace. Getting that backwards is what these
 * guard against.
 * 
 * @Description: OneShotCommandRunnerTest
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
class OneShotCommandRunnerTest {

    /**
     * No interactive shell in this build. Every case here gives the runner a command, and a
     * command is the path that never touches the prompt.
     */
    private static <T> org.springframework.beans.factory.ObjectProvider<T> absent() {
        return new org.springframework.beans.factory.ObjectProvider<>() {

            @Override
            public T getObject() {
                throw new org.springframework.beans.factory.NoSuchBeanDefinitionException(
                        "not in this build");
            }

            @Override
            public T getObject(Object... args) {
                return getObject();
            }

            @Override
            public T getIfAvailable() {
                return null;
            }

            @Override
            public T getIfUnique() {
                return null;
            }
        };
    }

    @Test
    @DisplayName("a mistyped catalog name is one red line, not 33 lines of stack trace")
    void aMistypedCatalogNamePrintsOneLine() throws Exception {
        RecordingCommands commands =
                new RecordingCommands(new CatalogDetailsNotFoundException("No such catalog: nope"));
        OneShotCommandRunner runner = new OneShotCommandRunner(commands, absent(), absent());

        String error;
        try (ConsoleCapture console = new ConsoleCapture()) {
            runner.run(new DefaultApplicationArguments("catalog", "show", "--catalog", "nope"));
            error = console.errorOutput();
        }

        assertThat(error).contains("No such catalog: nope");
        assertThat(error).doesNotContain("at com.github.greenfinger");
        assertThat(runner.getExitCode()).isEqualTo(1);
    }

    @Test
    @DisplayName("and it says where to look for the name that would have worked")
    void aMistypedCatalogNameGetsAHint() throws Exception {
        OneShotCommandRunner runner = new OneShotCommandRunner(
                new RecordingCommands(new CatalogDetailsNotFoundException("No such catalog: nope")), absent(), absent());

        String error;
        try (ConsoleCapture console = new ConsoleCapture()) {
            runner.run(new DefaultApplicationArguments("catalog", "show", "--catalog", "nope"));
            error = console.errorOutput();
        }

        assertThat(error).contains("catalogs");
    }

    @Test
    void anyOtherWebCrawlerExceptionReadsTheSameWay() throws Exception {
        OneShotCommandRunner runner = new OneShotCommandRunner(new RecordingCommands(
                new WebCrawlerException("No such config file: /tmp/absent.properties")), absent(), absent());

        String error;
        try (ConsoleCapture console = new ConsoleCapture()) {
            runner.run(new DefaultApplicationArguments("crawl", "--config", "/tmp/absent.properties"));
            error = console.errorOutput();
        }

        assertThat(error).contains("No such config file");
        assertThat(error).doesNotContain("at com.github.greenfinger");
        assertThat(runner.getExitCode()).isEqualTo(1);
    }

    @Test
    @DisplayName("an unknown --layers value comes back as IllegalArgumentException, also a typo")
    void aBadOptionValueReadsTheSameWay() throws Exception {
        OneShotCommandRunner runner = new OneShotCommandRunner(
                new RecordingCommands(new IllegalArgumentException("Unknown layer: vectors")), absent(), absent());

        String error;
        try (ConsoleCapture console = new ConsoleCapture()) {
            runner.run(new DefaultApplicationArguments("delete", "--layers", "vectors"));
            error = console.errorOutput();
        }

        assertThat(error).contains("Unknown layer: vectors");
        assertThat(runner.getExitCode()).isEqualTo(1);
    }

    @Test
    @DisplayName("a usage error keeps its hint lines: what went wrong, then what to type instead")
    void aUsageErrorKeepsItsHints() throws Exception {
        OneShotCommandRunner runner = new OneShotCommandRunner(
                new RecordingCommands(new UsageException("Nothing to crawl.",
                        "Give a url:            crawl --url https://example.com",
                        "Or a saved catalog:    crawl --catalog <name>")), absent(), absent());

        String error;
        try (ConsoleCapture console = new ConsoleCapture()) {
            runner.run(new DefaultApplicationArguments("crawl"));
            error = console.errorOutput();
        }

        assertThat(error.lines()).hasSize(3);
        assertThat(error.lines().findFirst()).hasValue("Nothing to crawl.");
        assertThat(error).contains("crawl --url https://example.com")
                .contains("crawl --catalog <name>");
        assertThat(runner.getExitCode()).isEqualTo(1);
    }

    @Test
    @DisplayName("an unusable command exits non-zero, so a script can tell it did not work")
    void aUsageErrorIsAFailedRun() throws Exception {
        OneShotCommandRunner runner = new OneShotCommandRunner(new RecordingCommands(
                new UsageException("Say which versions: --version, --before, --keep-latest or --all")), absent(), absent());

        try (ConsoleCapture console = new ConsoleCapture()) {
            runner.run(new DefaultApplicationArguments("delete", "--catalog", "books"));
        }

        assertThat(runner.getExitCode()).isEqualTo(1);
    }

    @Test
    @DisplayName("a defect still gets its stack trace, because the trace is the bug report")
    void anythingElseIsStillThrown() {
        OneShotCommandRunner runner = new OneShotCommandRunner(
                new RecordingCommands(new IllegalStateException("connection pool exhausted")), absent(), absent());

        try (ConsoleCapture console = new ConsoleCapture()) {
            assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("status")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("connection pool exhausted");
        }

        assertThat(runner.getExitCode()).isZero();
    }

    @Test
    @DisplayName("an exception with no message still says something")
    void aMessagelessExceptionFallsBackToItsType() throws Exception {
        OneShotCommandRunner runner =
                new OneShotCommandRunner(new RecordingCommands(new WebCrawlerException((String) null)), absent(), absent());

        String error;
        try (ConsoleCapture console = new ConsoleCapture()) {
            runner.run(new DefaultApplicationArguments("status"));
            error = console.errorOutput();
        }

        assertThat(error).contains("WebCrawlerException");
    }

    @Test
    void aCommandThatWorksExitsZeroAndSaysNothingOnStderr() throws Exception {
        RecordingCommands commands = new RecordingCommands(null);
        OneShotCommandRunner runner = new OneShotCommandRunner(commands, absent(), absent());

        String error;
        try (ConsoleCapture console = new ConsoleCapture()) {
            runner.run(new DefaultApplicationArguments("status"));
            error = console.errorOutput();
        }

        assertThat(error).isEmpty();
        assertThat(commands.dispatched).containsExactly("status");
        assertThat(runner.getExitCode()).isZero();
    }

    @Test
    @DisplayName("with no command the runner stands aside for the interactive prompt")
    void noArgumentsDispatchesNothing() throws Exception {
        RecordingCommands commands = new RecordingCommands(null);
        OneShotCommandRunner runner = new OneShotCommandRunner(commands, absent(), absent());

        runner.run(new DefaultApplicationArguments());

        assertThat(commands.dispatched).isEmpty();
        assertThat(runner.getExitCode()).isZero();
    }

    @Test
    @DisplayName("a worker node opens no prompt: nobody is typing at it")
    void aWorkerOpensNoPrompt() throws Exception {
        RecordingCommands commands = new RecordingCommands(null);
        OneShotCommandRunner runner = new OneShotCommandRunner(commands, absent(), absent());
        org.springframework.test.util.ReflectionTestUtils.setField(runner, "worker", true);

        // no command on the line, which is how a worker is started
        runner.run(new DefaultApplicationArguments());

        assertThat(commands.dispatched).isEmpty();
        assertThat(runner.getExitCode()).isZero();
    }

    /**
     * The real command set needs a database, a site and five output channels behind it. What the
     * runner does with the result is the whole subject here, so the commands are reduced to the
     * one thing that matters: what they threw.
     */
    private static class RecordingCommands extends CrawlCommands {

        private final RuntimeException failure;
        private final List<String> dispatched = new ArrayList<>();

        RecordingCommands(RuntimeException failure) {
            super(null, null, null, null, null, null, null, null, null, null, null);
            this.failure = failure;
        }

        @Override
        public void dispatch(String command, String primaryCommand, CrawlOptions options) {
            dispatched.add(command);
            if (failure != null) {
                throw failure;
            }
        }
    }

}
