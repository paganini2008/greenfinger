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

package com.github.greenfinger.core.engine;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 
 * @Description: RocksDbCrawlFrontierTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class RocksDbCrawlFrontierTest {

    @TempDir
    Path directory;

    private RocksDbCrawlFrontier frontier;

    private RocksDbCrawlFrontier open() throws Exception {
        frontier = new RocksDbCrawlFrontier(directory.resolve("frontier").toString());
        frontier.afterPropertiesSet();
        return frontier;
    }

    @AfterEach
    void tearDown() throws Exception {
        if (frontier != null) {
            frontier.destroy();
        }
    }

    private CrawlTask task(String url) {
        return CrawlTask.seed("cat-1", CrawlTask.ACTION_CRAWL, "https://a.com", url, "test", "UTF-8",
                0);
    }

    @Test
    @DisplayName("urls come back in the order they went in, which is breadth first")
    void pollsInInsertionOrder() throws Exception {
        RocksDbCrawlFrontier frontier = open();
        frontier.put(task("https://a.com/1"));
        frontier.put(task("https://a.com/2"));
        frontier.put(task("https://a.com/3"));

        assertThat(frontier.poll().getUrl()).isEqualTo("https://a.com/1");
        assertThat(frontier.poll().getUrl()).isEqualTo("https://a.com/2");
        assertThat(frontier.poll().getUrl()).isEqualTo("https://a.com/3");
        assertThat(frontier.poll()).isNull();
    }

    @Test
    void carriesDepthAndReferer() throws Exception {
        RocksDbCrawlFrontier frontier = open();
        CrawlTask child = task("https://a.com/parent").child("https://a.com/child");
        frontier.put(child);

        CrawlTask polled = frontier.poll();
        assertThat(polled.getDepth()).isEqualTo(1);
        assertThat(polled.getReferer()).isEqualTo("https://a.com/parent");
        assertThat(polled.getUrl()).isEqualTo("https://a.com/child");
    }

    @Test
    @DisplayName("the same url queued twice is queued once: delivery is at-least-once")
    void queuesAUrlOnlyOnce() throws Exception {
        RocksDbCrawlFrontier frontier = open();
        frontier.put(task("https://a.com/1"));
        frontier.put(task("https://a.com/1"));
        frontier.put(task("https://a.com/2"));

        assertThat(frontier.remaining()).isEqualTo(2);
        assertThat(frontier.poll().getUrl()).isEqualTo("https://a.com/1");
        assertThat(frontier.poll().getUrl()).isEqualTo("https://a.com/2");
        assertThat(frontier.poll()).isNull();
    }

    @Test
    @DisplayName("and a url handled earlier is not queued again when its message is redelivered")
    void doesNotQueueAUrlItHasAlreadyFinished() throws Exception {
        RocksDbCrawlFrontier frontier = open();
        frontier.put(task("https://a.com/1"));
        frontier.complete(frontier.poll());
        assertThat(frontier.remaining()).isZero();

        frontier.put(task("https://a.com/1"));

        assertThat(frontier.remaining()).isZero();
        assertThat(frontier.poll()).isNull();
    }

    @Test
    @DisplayName("recovery walks the queue and stops at it, not into the urls it remembers")
    void theSecondKeySpaceDoesNotDisturbRecovery() throws Exception {
        RocksDbCrawlFrontier first = open();
        first.put(task("https://a.com/1"));
        first.put(task("https://a.com/2"));
        first.complete(first.poll());
        first.destroy();

        RocksDbCrawlFrontier second = open();

        assertThat(second.recoveredCount()).isEqualTo(1);
        assertThat(second.remaining()).isEqualTo(1);
        // what is still queued is still remembered: a redelivery of it is a duplicate either side
        // of the restart
        second.put(task("https://a.com/2"));
        assertThat(second.remaining()).isEqualTo(1);
        assertThat(second.poll().getUrl()).isEqualTo("https://a.com/2");
    }

    @Test
    @DisplayName("a finished url is queued again by the next run, which is what a refresh is")
    void aNewRunMayQueueWhatTheLastOneFinished() throws Exception {
        RocksDbCrawlFrontier first = open();
        first.put(task("https://a.com/1"));
        first.complete(first.poll());
        first.destroy();

        // A refresh writes the same version, so it opens this very store. Remembering across the
        // restart would have it refuse every page the last run fetched -- which is every page a
        // refresh exists to look at again.
        RocksDbCrawlFrontier second = open();
        second.put(task("https://a.com/1"));

        assertThat(second.remaining()).isEqualTo(1);
        assertThat(second.poll().getUrl()).isEqualTo("https://a.com/1");
    }

    @Test
    @DisplayName("completing a task removes it; the count tracks what is outstanding")
    void completeRemovesFromTheFrontier() throws Exception {
        RocksDbCrawlFrontier frontier = open();
        frontier.put(task("https://a.com/1"));
        frontier.put(task("https://a.com/2"));
        assertThat(frontier.remaining()).isEqualTo(2);

        CrawlTask polled = frontier.poll();
        frontier.complete(polled);
        assertThat(frontier.remaining()).isEqualTo(1);
    }

    @Test
    @DisplayName("an interrupted crawl recovers everything it had not finished")
    void recoversOutstandingWork() throws Exception {
        RocksDbCrawlFrontier first = open();
        first.put(task("https://a.com/1"));
        first.put(task("https://a.com/2"));
        first.put(task("https://a.com/3"));
        // one finishes, one is taken but never completed, one is never taken
        first.complete(first.poll());
        first.poll();
        first.destroy();

        RocksDbCrawlFrontier second = open();
        assertThat(second.recoveredCount()).isEqualTo(2);
        assertThat(second.poll().getUrl()).isEqualTo("https://a.com/2");
        assertThat(second.poll().getUrl()).isEqualTo("https://a.com/3");
    }

    @Test
    void freshFrontierRecoversNothing() throws Exception {
        assertThat(open().recoveredCount()).isZero();
    }

    @Test
    void cleanDiscardsEverything() throws Exception {
        RocksDbCrawlFrontier first = open();
        first.put(task("https://a.com/1"));
        first.clean();
        frontier = null;

        RocksDbCrawlFrontier second = open();
        assertThat(second.recoveredCount()).isZero();
        assertThat(second.poll()).isNull();
    }

    @Test
    void reportsItsName() throws Exception {
        assertThat(open().getName()).isEqualTo("rocksdb-frontier");
    }

}
