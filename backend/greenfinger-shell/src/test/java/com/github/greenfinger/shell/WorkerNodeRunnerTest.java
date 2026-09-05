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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What keeps a worker process alive, and what lets it go.
 * 
 * @Description: WorkerNodeRunnerTest
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
class WorkerNodeRunnerTest {

    @Test
    @DisplayName("the runner blocks, which is the whole reason the process stays up")
    void itDoesNotReturnUntilTheContextCloses() throws Exception {
        WorkerNodeRunner runner = new WorkerNodeRunner();
        CountDownLatch returned = new CountDownLatch(1);
        Thread caller = new Thread(() -> {
            try {
                runner.run(null);
                returned.countDown();
            } catch (Exception ignored) {
                // a failure to return is the assertion below, not an exception
            }
        });
        caller.setDaemon(true);
        caller.start();

        // Spring Boot returns from run() once the last ApplicationRunner has finished, and main
        // then closes the context. A worker that returned here would exit immediately.
        assertThat(returned.await(300, TimeUnit.MILLISECONDS)).isFalse();

        // the shutdown hook closes the context, which destroys this bean
        runner.destroy();

        assertThat(returned.await(5, TimeUnit.SECONDS))
                .as("the shutdown has to be let through, or the process would need killing")
                .isTrue();
    }

}
