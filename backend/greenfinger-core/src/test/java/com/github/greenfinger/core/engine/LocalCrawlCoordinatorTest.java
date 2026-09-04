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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.state.Dashboard;
import com.github.greenfinger.core.component.state.DefaultGlobalStateManager;
import com.github.greenfinger.core.CatalogFixtures;

/**
 * The counters a crawl ends by, and the one way they used to be able to disagree for ever.
 *
 * <p>
 * A crawl is over when nothing is still owed: every url dispatched has been answered for. That
 * holds only while every dispatch is settled exactly once -- by a worker finishing it, or, when
 * the frontier turns a duplicate away, by whoever handed it over. A url that is dispatched and
 * then silently dropped is a url the watchdog waits on until it gives up and calls the run
 * stalled, and a stalled run publishes nothing.
 * 
 * @Description: LocalCrawlCoordinatorTest
 * @Author: Fred Feng
 * @Date: 04/09/2026
 * @Version 2.0.0
 */
class LocalCrawlCoordinatorTest {

    @TempDir
    Path directory;

    private CatalogDetails catalogDetails;
    private DefaultGlobalStateManager stateManager;
    private RocksDbCrawlFrontier frontier;
    private LocalCrawlCoordinator coordinator;

    @BeforeEach
    void setUp() throws Exception {
        catalogDetails = CatalogFixtures.details();
        stateManager = new DefaultGlobalStateManager(catalogDetails);
        stateManager.afterPropertiesSet();
        frontier = new RocksDbCrawlFrontier(directory.resolve("frontier").toString());
        frontier.afterPropertiesSet();
        coordinator = new LocalCrawlCoordinator(frontier, stateManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        frontier.destroy();
    }

    private CrawlTask task(String url) {
        return CrawlTask.seed(catalogDetails.getId(), CrawlTask.ACTION_CRAWL, "https://a.com", url,
                "test", "UTF-8", 0);
    }

    @Test
    @DisplayName("a url the frontier already had is answered for, so the counters still meet")
    void aRefusedDuplicateIsStillSettled() throws Exception {
        coordinator.dispatch(task("https://a.com/1"));
        // the same url again, as an at-least-once delivery produces
        coordinator.dispatch(task("https://a.com/1"));

        Dashboard dashboard = stateManager.getDashboard();
        assertThat(dashboard.getTotalUrlCount()).isEqualTo(2);
        // one of them was queued and is waiting for a worker; the other was refused and settled
        // on the spot, and is counted as a url that had been seen before
        assertThat(dashboard.getHandledUrlCount()).isEqualTo(1);
        assertThat(dashboard.getExistingUrlCount()).isEqualTo(1);

        coordinator.afterHandled(frontier.poll());

        assertThat(frontier.poll()).isNull();
        assertThat(dashboard.getHandledUrlCount()).isEqualTo(dashboard.getTotalUrlCount());
    }

    @Test
    @DisplayName("and an ordinary url is counted once on the way out and once on the way back")
    void anOrdinaryUrlIsCountedTwiceOver() throws Exception {
        coordinator.dispatch(task("https://a.com/1"));

        Dashboard dashboard = stateManager.getDashboard();
        assertThat(dashboard.getTotalUrlCount()).isEqualTo(1);
        assertThat(dashboard.getHandledUrlCount()).isZero();
        assertThat(dashboard.getExistingUrlCount()).isZero();

        coordinator.afterHandled(frontier.poll());

        assertThat(dashboard.getHandledUrlCount()).isEqualTo(1);
    }
}
