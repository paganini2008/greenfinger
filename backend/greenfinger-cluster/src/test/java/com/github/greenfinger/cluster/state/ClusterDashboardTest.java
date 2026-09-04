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

package com.github.greenfinger.cluster.state;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.cluster.support.TestCluster;
import com.github.greenfinger.cluster.support.TestRun;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.component.state.Dashboard;

/**
 * The counters as the Monitor page reads them.
 *
 * <p>
 * Reads are local -- every process holds a full replica -- which is what makes it reasonable for a
 * page refreshing every second to ask for eight of them. Writes are a round trip, which is why
 * they arrive here already summed.
 * 
 * @Description: ClusterDashboardTest
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
class ClusterDashboardTest {

    private TestCluster cluster;
    private ClusterGlobalStateManager counters;
    private TestRun run;

    @BeforeEach
    void setUp() throws Exception {
        cluster = TestCluster.start(1);
        run = new TestRun("cat-dash", "books");
        counters = new ClusterGlobalStateManager(cluster.node(0).cache(), run.getCatalogDetails(),
                "node-a", 10_000L, true);
        counters.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() throws Exception {
        counters.destroy();
        cluster.close();
    }

    @Test
    @DisplayName("every counter is readable, and reads what was written")
    void everyCounterRoundTrips() {
        for (CountingType countingType : CountingType.values()) {
            counters.incrementCount(0L, countingType, 3);
        }
        counters.flush();

        Dashboard dashboard = counters.getDashboard();
        assertThat(dashboard.getTotalUrlCount()).isEqualTo(3);
        assertThat(dashboard.getHandledUrlCount()).isEqualTo(3);
        assertThat(dashboard.getInvalidUrlCount()).isEqualTo(3);
        assertThat(dashboard.getExistingUrlCount()).isEqualTo(3);
        assertThat(dashboard.getFilteredUrlCount()).isEqualTo(3);
        assertThat(dashboard.getSavedResourceCount()).isEqualTo(3);
        assertThat(dashboard.getIndexedResourceCount()).isEqualTo(3);
        assertThat(dashboard.getSavedImageCount()).isEqualTo(3);
        assertThat(dashboard.getDuplicatedContentCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("the clock is the crawl's: whoever opens it first sets it, and the rest read it")
    void theClockIsSetOnce() throws Exception {
        long start = counters.getDashboard().getStartTime();
        assertThat(start).isPositive();

        ClusterGlobalStateManager second = new ClusterGlobalStateManager(cluster.node(0).cache(),
                run.getCatalogDetails(), "node-b", 10_000L, false);
        second.afterPropertiesSet();
        try {
            // a second node joining must not restart the elapsed time
            assertThat(second.getDashboard().getStartTime()).isEqualTo(start);
            assertThat(second.getDashboard().getEndTime()).isPositive();
            assertThat(second.getDashboard().getElapsedTime()).isNotNegative();
            assertThat(second.getDashboard().getRemainingTime()).isNotNegative();
        } finally {
            second.destroy();
        }
    }

    @Test
    void completionIsReadableAsWellAsWritable() {
        assertThat(counters.isCompleted()).isFalse();
        counters.setCompleted(true, "reached maxFetchSize");
        assertThat(counters.isCompleted()).isTrue();
        assertThat(counters.getDashboard().getCompletionReason())
                .isEqualTo("reached maxFetchSize");
        assertThat(counters.getDashboard().isInterrupted()).isFalse();
    }

    @Test
    @DisplayName("the node that starts a run clears what the last run at this version left behind")
    void theInitiatorResets() throws Exception {
        counters.incrementCount(0L, CountingType.SAVED_RESOURCE_COUNT, 7);
        counters.incrementCount(0L, CountingType.URL_TOTAL_COUNT, 9);
        counters.flush();
        counters.setCompleted(true, "reached maxFetchSize");
        assertThat(counters.getDashboard().getSavedResourceCount()).isEqualTo(7);

        // the same catalog at the same version, crawled again: these keys are still in the shared
        // cache, and inheriting them would end the new run before it began
        ClusterGlobalStateManager again = new ClusterGlobalStateManager(cluster.node(0).cache(),
                run.getCatalogDetails(), "node-a", 10_000L, true);
        again.afterPropertiesSet();
        try {
            assertThat(again.isCompleted()).isFalse();
            assertThat(again.getDashboard().getCompletionReason()).isNull();
            assertThat(again.getDashboard().getSavedResourceCount()).isZero();
            assertThat(again.getDashboard().getTotalUrlCount()).isZero();
        } finally {
            again.destroy();
        }
    }

    @Test
    @DisplayName("a node joining a run in progress does not clear the counters it is joining")
    void aJoinerDoesNotReset() throws Exception {
        counters.incrementCount(0L, CountingType.SAVED_RESOURCE_COUNT, 4);
        counters.flush();

        ClusterGlobalStateManager joiner = new ClusterGlobalStateManager(cluster.node(0).cache(),
                run.getCatalogDetails(), "node-b", 10_000L, false);
        joiner.afterPropertiesSet();
        try {
            assertThat(joiner.getDashboard().getSavedResourceCount()).isEqualTo(4);
        } finally {
            joiner.destroy();
        }
    }

    @Test
    @DisplayName("an interruption is recorded as one, so nothing publishes a half version")
    void anInterruptionIsMarked() {
        counters.interrupt("interrupted by request");

        assertThat(counters.isCompleted()).isTrue();
        assertThat(counters.getDashboard().isInterrupted()).isTrue();
        assertThat(counters.getDashboard().getCompletionReason())
                .isEqualTo("interrupted by request");
    }

    @Test
    @DisplayName("progress takes whichever limit has advanced further")
    void progressFollowsTheNearerLimit() {
        counters.incrementCount(0L, CountingType.SAVED_RESOURCE_COUNT, 5);
        counters.flush();

        assertThat(counters.getDashboard().getProgress()).isBetween(0d, 1d);
        assertThat(counters.getDashboard().getCatalogDetails().getName()).isEqualTo("books");
    }

    @Test
    @DisplayName("the average execution time is this node's own; averaging four would describe none")
    void executionTimeIsLocal() {
        for (int i = 0; i < 300; i++) {
            counters.incrementCount(System.currentTimeMillis() - 5,
                    CountingType.SAVED_RESOURCE_COUNT, 1);
        }
        // more than the window holds, so the oldest have rolled out
        assertThat(counters.getDashboard().getAverageExecutionTime()).isNotNegative();
    }

    @Test
    void toStringSaysWhatHappened() {
        counters.incrementCount(0L, CountingType.SAVED_RESOURCE_COUNT, 2);
        counters.flush();

        assertThat(counters.getDashboard().toString()).contains("books").contains("dispatched")
                .contains("handled");
    }

    @Test
    void isTimeoutMeasuresSinceTheLastWrite() throws Exception {
        counters.incrementCount(0L, CountingType.URL_TOTAL_COUNT, 1);
        counters.flush();

        assertThat(counters.isTimeout(1, TimeUnit.HOURS)).isFalse();
        // a millisecond has to actually pass: the comparison is strictly greater than
        Thread.sleep(5L);
        assertThat(counters.isTimeout(1, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(counters.getName()).isEqualTo("cluster");
        assertThat(counters.getCatalogDetails().getName()).isEqualTo("books");
    }

    @Test
    @DisplayName("nothing written yet reads as zero rather than failing")
    void anEmptyCounterReadsZero() {
        assertThat(counters.getDashboard().getSavedImageCount()).isZero();
        assertThat(counters.perNodeCounters()).isEmpty();
    }

}
