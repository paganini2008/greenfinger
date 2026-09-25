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

import java.util.concurrent.CountDownLatch;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * A process that is a member of the cluster and nothing else: {@code greenfinger-cli.sh -n 3}
 * starts the one you type into and two of these, which have no prompt and exist to be dispatched
 * to -- two more fetchers and two more copies of everything.
 *
 * <p>
 * It stays up by blocking here, as the interactive shell does: an {@link ApplicationRunner} that
 * does not return keeps the context open. A non-daemon thread would not do -- 
 * {@code SpringApplication.exit} closes the context as soon as main reaches it.
 *
 * @Description: WorkerNodeRunner
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@Component
@ConditionalOnProperty(name = "greenfinger.shell.worker", havingValue = "true")
public class WorkerNodeRunner implements ApplicationRunner, DisposableBean {

    private final CountDownLatch until = new CountDownLatch(1);

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // stdout rather than the log: com.github.greenfinger logs at WARN by default, and a
        // background process whose only sign of life is a log file has to say when it is ready
        // without depending on somebody having turned the level up first
        System.out.println("Worker node ready. It has no prompt: it takes urls from the cluster,"
                + " keeps its own copy of what it fetches, and stops when it is asked to.");
        until.await();
        System.out.println("Worker node stopping.");
    }

    /**
     * Released when the context closes, which is what a shutdown hook does on SIGTERM -- so the
     * run above returns and the shutdown carries on in order rather than being cut off.
     */
    @Override
    public void destroy() {
        until.countDown();
    }

}
