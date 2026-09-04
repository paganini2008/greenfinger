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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The extra nodes a session forks.
 *
 * <p>
 * The launcher is stood in for by a small script that records how it was called and then sleeps,
 * so what is actually checked is the contract between the two: that one process per extra node is
 * started, that each is told its own directory, and that stopping them stops them. Whether the jvm
 * the real launcher goes on to start joins the cluster is a different question and is answered by
 * running it, not by a unit test.
 *
 * @Description: LocalNodesTest
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
@DisabledOnOs(OS.WINDOWS)
class LocalNodesTest {

    /** A stand-in launcher: writes down where it was pointed, then waits to be stopped. */
    private Path fakeLauncher(Path directory) throws Exception {
        Path script = directory.resolve("greenfinger-cli.sh");
        Files.writeString(script, """
                #!/usr/bin/env bash
                echo "$@ dir=${GREENFINGER_WORKER_DIR}" >> "${GREENFINGER_WORKER_DIR}/called"
                sleep 120
                """);
        Files.setPosixFilePermissions(script,
                Set.copyOf(java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x")));
        return script;
    }

    @Test
    @DisplayName("three nodes means two more, because this process is the first")
    void forksOneFewerThanAsked(@TempDir Path home) throws Exception {
        Path launcher = fakeLauncher(home);
        LocalNodes nodes = new LocalNodes();
        System.setProperty(LocalNodes.LAUNCHER_PROPERTY, launcher.toString());
        try {
            assertThat(nodes.start(3)).isEqualTo(2);
            assertThat(nodes.count()).isEqualTo(2);

            // each was given its own directory, and told it is a worker. Under the data store,
            // beside system/ and user/ rather than inside either: a worker directory nested in
            // one of those would show up in everything that walks node 1's files.
            Path second = home.resolve("data/workers/cli-2");
            Path third = home.resolve("data/workers/cli-3");
            waitForFile(second.resolve("called"));
            waitForFile(third.resolve("called"));
            assertThat(Files.readString(second.resolve("called"))).contains("--as-worker")
                    .contains(second.toString());
            assertThat(Files.readString(third.resolve("called"))).contains(third.toString());
        } finally {
            nodes.stop();
            System.clearProperty(LocalNodes.LAUNCHER_PROPERTY);
        }
    }

    @Test
    @DisplayName("stopping them stops them, and a second stop is not an error")
    void stopsWhatItStarted(@TempDir Path home) throws Exception {
        Path launcher = fakeLauncher(home);
        LocalNodes nodes = new LocalNodes();
        System.setProperty(LocalNodes.LAUNCHER_PROPERTY, launcher.toString());
        try {
            assertThat(nodes.start(2)).isEqualTo(1);
            assertThat(nodes.stop()).isEqualTo(1);
            assertThat(nodes.count()).isZero();
            assertThat(nodes.stop()).isZero();
        } finally {
            nodes.stop();
            System.clearProperty(LocalNodes.LAUNCHER_PROPERTY);
        }
    }

    @Test
    @DisplayName("one node asks for nothing, and a second call does not fork again")
    void oneNodeIsThisProcess(@TempDir Path home) throws Exception {
        Path launcher = fakeLauncher(home);
        LocalNodes nodes = new LocalNodes();
        System.setProperty(LocalNodes.LAUNCHER_PROPERTY, launcher.toString());
        try {
            assertThat(nodes.start(1)).isZero();
            assertThat(nodes.start(0)).isZero();

            assertThat(nodes.start(2)).isEqualTo(1);
            // already running: asking again is answered with what is there rather than with more
            assertThat(nodes.start(3)).isEqualTo(1);
        } finally {
            nodes.stop();
            System.clearProperty(LocalNodes.LAUNCHER_PROPERTY);
        }
    }

    @Test
    @DisplayName("a jar run without the launcher forks nothing and says so by returning zero")
    void withoutALauncherThereIsNothingToFork(@TempDir Path home) {
        System.clearProperty(LocalNodes.LAUNCHER_PROPERTY);
        LocalNodes nodes = new LocalNodes();
        assertThat(nodes.start(3)).isZero();
    }

    private void waitForFile(Path path) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!Files.exists(path) && System.nanoTime() < deadline) {
            Thread.sleep(50L);
        }
        assertThat(path).exists();
    }

}
