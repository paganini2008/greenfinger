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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * The extra nodes a session runs on this machine.
 *
 * <h2>Why the prompt forks and the one-shot launcher does not</h2>
 * {@code greenfinger-cli.sh catalog-crawl --node=3} is handled entirely in the launcher: it starts
 * two workers, runs the command, and its trap stops them when the command returns. The prompt
 * cannot work that way -- it was started long before anybody typed {@code --node=3}, and by then
 * the launcher has been replaced by the jvm.
 *
 * <p>
 * So the session forks them itself. This process is already a node and already the cluster's
 * leader -- it holds the cluster port and it started first -- so asking for three means starting
 * two more. They join by cluster name, take their share of the urls, and are stopped when the
 * crawl that asked for them finishes. A session that crawls twice with {@code --node=3} therefore
 * runs three nodes twice, not three and then five.
 *
 * <h2>The launcher starts them, not this class</h2>
 * A worker needs its own data directory, its own database when the database is a file, and the
 * cluster name this session joined. All of that is decided by {@code worker_env} in
 * {@code greenfinger-cli.sh}, and it stays decided there: this runs
 * {@code greenfinger-cli.sh --as-worker} with {@code GREENFINGER_WORKER_DIR} set, so a worker the
 * prompt forked and a worker the launcher forked are the same process with the same environment.
 * Writing it again in Java would be two implementations of one rule, and they would drift.
 *
 * @Description: LocalNodes
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
@Slf4j
@Component
public class LocalNodes implements DisposableBean {

    /** Where the launcher was found, so the same one is used to fork. */
    static final String LAUNCHER_PROPERTY = "greenfinger.launcher";

    /** Set by the launcher; the parent of the per-worker directories. */
    static final String WORKER_ROOT_ENV = "GREENFINGER_WORKER_ROOT";

    /** Set by the launcher from run.conf; where every process here writes its log. */
    static final String LOG_DIR_ENV = "GF_LOG_DIR";

    private final List<Process> running = new ArrayList<>();

    /**
     * Starts {@code nodes - 1} of them, because this process is the first.
     *
     * @return how many actually started. Zero is not an error and is what happens when the jar was
     *         run directly rather than through the launcher -- there is then no script to fork.
     */
    public synchronized int start(int nodes) {
        if (nodes <= 1 || !running.isEmpty()) {
            return running.size();
        }
        Path launcher = launcher();
        if (launcher == null) {
            return 0;
        }
        Path root = workerRoot(launcher);
        for (int i = 2; i <= nodes; i++) {
            Path directory = root.resolve("cli-" + i);
            try {
                Files.createDirectories(directory);
                ProcessBuilder builder = new ProcessBuilder(launcher.toString(), "--as-worker");
                builder.environment().put("GREENFINGER_WORKER_DIR", directory.toString());
                // Its own file, so three nodes writing at once do not interleave into one -- but
                // in the same directory as every other launcher's log, because four places to
                // look is three too many when something went wrong overnight.
                Path logs = logDirectory();
                Files.createDirectories(logs);
                builder.environment().put("GREENFINGER_LOG",
                        logs.resolve("cli-worker-" + i + ".log").toString());
                builder.redirectErrorStream(true);
                builder.redirectOutput(logs.resolve("cli-worker-" + i + ".out").toFile());
                running.add(builder.start());
            } catch (IOException e) {
                log.warn("Could not start a worker node in {}: {}", directory, e.getMessage());
            }
        }
        return running.size();
    }

    /**
     * Asked to stop rather than killed: a worker winds its crawl down at the next check, so pages
     * already fetched still reach the outputs and the frontier stays consistent for a resume.
     */
    public synchronized int stop() {
        int stopped = 0;
        for (Process process : running) {
            if (process.isAlive()) {
                process.destroy();
                stopped++;
            }
        }
        for (Process process : running) {
            try {
                if (!process.waitFor(20, TimeUnit.SECONDS)) {
                    // it did not go: a node stuck in a fetch is not worth waiting on for ever,
                    // and its frontier survives being killed
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        running.clear();
        return stopped;
    }

    public synchronized int count() {
        running.removeIf(process -> !process.isAlive());
        return running.size();
    }

    /**
     * The launcher this process was started by. Passed as a system property rather than guessed,
     * because a jar run directly has no launcher and should say so instead of forking something
     * that happens to be next to it.
     */
    private Path launcher() {
        String path = System.getProperty(LAUNCHER_PROPERTY);
        if (StringUtils.isBlank(path)) {
            return null;
        }
        File file = new File(path);
        return file.canExecute() ? file.toPath() : null;
    }

    /** The one log directory, or logs/ beside the launcher when nothing said otherwise. */
    private Path logDirectory() {
        String configured = System.getenv(LOG_DIR_ENV);
        if (StringUtils.isNotBlank(configured)) {
            return Paths.get(configured);
        }
        Path launcher = launcher();
        return launcher != null ? launcher.toAbsolutePath().getParent().resolve("logs")
                : Paths.get("logs");
    }

    /**
     * Where the launcher said, which is {@code workers/} under the data store -- beside
     * {@code system/} and {@code user/} rather than inside either, because a worker directory
     * nested in one of those would show up in everything that walks node 1's files.
     */
    private Path workerRoot(Path launcher) {
        String configured = System.getenv(WORKER_ROOT_ENV);
        if (StringUtils.isNotBlank(configured)) {
            return Paths.get(configured);
        }
        return launcher.toAbsolutePath().getParent().resolve("data").resolve("workers");
    }

    @Override
    public void destroy() {
        stop();
    }

}
