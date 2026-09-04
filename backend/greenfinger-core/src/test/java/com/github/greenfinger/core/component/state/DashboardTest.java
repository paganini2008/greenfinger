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

package com.github.greenfinger.core.component.state;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.CatalogFixtures;
import com.github.greenfinger.core.catalog.CatalogDetails;

/**
 * 
 * @Description: DashboardTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class DashboardTest {

    private CatalogDetails catalogDetails;
    private DefaultGlobalStateManager stateManager;

    @BeforeEach
    void setUp() throws Exception {
        catalogDetails = CatalogFixtures.details();
        stateManager = new DefaultGlobalStateManager(catalogDetails);
        stateManager.afterPropertiesSet();
    }

    @Test
    void everyCounterIsIncrementable() {
        long now = System.currentTimeMillis();
        for (CountingType countingType : CountingType.values()) {
            assertThat(stateManager.incrementCount(now, countingType)).isEqualTo(1);
        }
        Dashboard dashboard = stateManager.getDashboard();
        assertThat(dashboard.getTotalUrlCount()).isEqualTo(1);
        assertThat(dashboard.getSavedResourceCount()).isEqualTo(1);
        assertThat(dashboard.getSavedImageCount()).isEqualTo(1);
        assertThat(dashboard.getDuplicatedContentCount()).isEqualTo(1);
    }

    @Test
    void countersStartAtZeroAndAcceptDeltas() {
        Dashboard dashboard = stateManager.getDashboard();
        assertThat(dashboard.getSavedImageCount()).isZero();
        stateManager.incrementCount(System.currentTimeMillis(), CountingType.SAVED_IMAGE_COUNT, 7);
        assertThat(dashboard.getSavedImageCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("progress follows whichever limit is nearer")
    void progressTracksTheNearerLimit() {
        Dashboard dashboard = stateManager.getDashboard();
        // not exactly zero: the elapsed-time limit has already started ticking
        assertThat(dashboard.getProgress()).isLessThan(0.01f);

        // the fixture stops at 100 saved resources
        for (int i = 0; i < 50; i++) {
            stateManager.incrementCount(0L, CountingType.SAVED_RESOURCE_COUNT);
        }
        assertThat(dashboard.getProgress()).isBetween(0.4d, 0.6d);
    }

    @Test
    void progressNeverExceedsOne() {
        for (int i = 0; i < 500; i++) {
            stateManager.incrementCount(0L, CountingType.SAVED_RESOURCE_COUNT);
        }
        assertThat(stateManager.getDashboard().getProgress()).isEqualTo(1d);
    }

    @Test
    void remainingTimeIsNeverNegative() {
        assertThat(stateManager.getDashboard().getRemainingTime()).isNotNegative();
    }

    @Test
    void completionIsLatchedAndVisible() {
        assertThat(stateManager.isCompleted()).isFalse();
        stateManager.setCompleted(true, "reached maxFetchSize");
        assertThat(stateManager.isCompleted()).isTrue();
        assertThat(stateManager.getDashboard().isCompleted()).isTrue();
    }

    @Test
    void membersAreTracked() {
        stateManager.addMember("node-1");
        stateManager.addMember("node-1");
        stateManager.addMember("node-2");
        assertThat(stateManager.getMembers()).containsExactly("node-1", "node-2");
        stateManager.removeMember("node-1");
        assertThat(stateManager.getMembers()).containsExactly("node-2");
    }

    @Test
    void averageExecutionTimeUsesTheConfiguredCounter() {
        stateManager.incrementCount(System.currentTimeMillis() - 100,
                CountingType.SAVED_RESOURCE_COUNT);
        assertThat(stateManager.getDashboard().getAverageExecutionTime()).isPositive();
    }

    @Test
    void timeoutIsMeasuredFromTheLastChange() throws Exception {
        assertThat(stateManager.isTimeout(1, TimeUnit.HOURS)).isFalse();
        // the clock has to actually advance past the last change for a zero timeout to fire
        Thread.sleep(5L);
        assertThat(stateManager.isTimeout(0, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    @DisplayName("a snapshot does not move when the live counters do")
    void readonlySnapshotIsFrozen() {
        stateManager.incrementCount(0L, CountingType.SAVED_RESOURCE_COUNT);
        Dashboard snapshot = new ReadonlyDashboard(stateManager.getDashboard());
        stateManager.incrementCount(0L, CountingType.SAVED_RESOURCE_COUNT);

        assertThat(snapshot.getSavedResourceCount()).isEqualTo(1);
        assertThat(stateManager.getDashboard().getSavedResourceCount()).isEqualTo(2);
        assertThat(snapshot.getCatalogDetails()).isEqualTo(catalogDetails);
        assertThat(snapshot.toString()).isNotBlank();
    }

    @Test
    @DisplayName("and it carries why the crawl ended, which is what is read after it is over")
    void readonlySnapshotKeepsTheCompletionReason() {
        stateManager.setCompleted(true, "the site is exhausted: all 6 url(s) handled");

        Dashboard snapshot = new ReadonlyDashboard(stateManager.getDashboard());

        assertThat(snapshot.isCompleted()).isTrue();
        assertThat(snapshot.getCompletionReason())
                .isEqualTo("the site is exhausted: all 6 url(s) handled");
        assertThat(snapshot.isInterrupted()).isFalse();
    }

    @Test
    @DisplayName("an interrupted run is remembered as one")
    void readonlySnapshotKeepsTheInterruption() {
        stateManager.interrupt("interrupted by request");

        Dashboard snapshot = new ReadonlyDashboard(stateManager.getDashboard());

        assertThat(snapshot.isInterrupted()).isTrue();
        assertThat(snapshot.getCompletionReason()).isEqualTo("interrupted by request");
    }

    @Test
    void dashboardRendersAsText() {
        assertThat(stateManager.getDashboard().toString()).contains("StartTime", "SavedRes");
        assertThat(stateManager.getName()).isEqualTo("default");
    }


}
