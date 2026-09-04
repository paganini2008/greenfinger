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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.engine.CrawlerEngine;

/**
 * 
 * @Description: RunningCrawlsTest
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
class RunningCrawlsTest {

    @Test
    @DisplayName("a crawl runs behind the prompt, and can be found again by its catalog id")
    void startsBehindThePrompt() throws Exception {
        RunningCrawls running = new RunningCrawls();
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<CrawlerEngine.Result> future = running.start("catalog-1", () -> {
                release.await(5, TimeUnit.SECONDS);
                return null;
            });

            assertThat(running.get("catalog-1")).isSameAs(future);
            assertThat(running.current()).isNotNull();
            assertThat(running.current().getKey()).isEqualTo("catalog-1");

            release.countDown();
            future.get(5, TimeUnit.SECONDS);
            // finished, so it is no longer what 'status' would attach to
            assertThat(running.current()).isNull();
            // but still there, so the summary of the last run is still available
            assertThat(running.get("catalog-1")).isNotNull();
        } finally {
            running.destroy();
        }
    }

    @Test
    void forgettingRemovesIt() throws Exception {
        RunningCrawls running = new RunningCrawls();
        try {
            running.start("catalog-2", () -> null).get(5, TimeUnit.SECONDS);
            running.forget("catalog-2");

            assertThat(running.get("catalog-2")).isNull();
            assertThat(running.current()).isNull();
        } finally {
            running.destroy();
        }
    }

    @Test
    @DisplayName("nothing running is null rather than an empty something")
    void nothingRunning() {
        RunningCrawls running = new RunningCrawls();
        assertThat(running.current()).isNull();
        assertThat(running.get("anything")).isNull();
        running.destroy();
    }

    @Test
    @DisplayName("shutting down waits for a crawl to wind itself down")
    void destroyWaitsForTheCrawl() throws Exception {
        RunningCrawls running = new RunningCrawls();
        CountDownLatch started = new CountDownLatch(1);
        running.start("catalog-3", () -> {
            started.countDown();
            Thread.sleep(50L);
            return null;
        });
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        running.destroy();
        assertThat(running.get("catalog-3").isDone()).isTrue();
    }

}
