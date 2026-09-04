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

package com.github.greenfinger.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsImpl;
import com.github.greenfinger.core.component.state.Dashboard;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.model.Catalog;

/**
 * The Monitor page's numbers, from both of the places they can come from.
 *
 * <p>
 * The pair matters more than either alone: a run that finishes while somebody is watching must not
 * make the page go blank, which only holds while the live dashboard and the settings file produce
 * the same shape.
 *
 * @Description: CatalogSummaryTest
 * @Author: Fred Feng
 * @Date: 31/08/2026
 * @Version 2.0.0
 */
class CatalogSummaryTest {

    @Test
    @DisplayName("a crawl in flight: live, and the counters are the dashboard's")
    void readsALiveRun() {
        CatalogSummary summary = new CatalogSummary(dashboard(false));

        assertThat(summary.isLive()).isTrue();
        assertThat(summary.isCompleted()).isFalse();
        assertThat(summary.getCatalogName()).isEqualTo("books");
        assertThat(summary.getSavedResourceCount()).isEqualTo(42L);
        assertThat(summary.getSavedImageCount()).isEqualTo(7L);
        assertThat(summary.getTotalUrlCount()).isEqualTo(100L);
        assertThat(summary.getElapsedTime()).isEqualTo("0h 1m 5s");
        assertThat(summary.getCompletionReason()).isNull();
        assertThat(summary.isInterrupted()).isFalse();
    }

    @Test
    void aFinishedDashboardIsNotLive() {
        assertThat(new CatalogSummary(dashboard(true)).isLive()).isFalse();
    }

    @Test
    @DisplayName("the versions come from the catalog now, not from the copy the crawl started with")
    void theVersionsAreTheCatalogsOwn() {
        Catalog stale = new Catalog();
        stale.setId("catalog-1");
        stale.setName("books");
        stale.setUrl("https://books.toscrape.com");
        // what the crawl was handed: the version it was about to write, not yet published
        stale.setIndexVersion(3);
        stale.setSearchVersion(-1);
        CatalogDetails asItWasStarted = new CatalogDetailsImpl(stale, new WebCrawlerProperties());

        CatalogSummary summary = new CatalogSummary(dashboard(true, asItWasStarted), details());

        assertThat(summary.getVersion()).isEqualTo(3);
        // published while the crawl was running, and the page is waiting to be told exactly that
        assertThat(summary.getSearchVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("and afterwards: the same fields, read back out of settings.json")
    void readsTheLastRunFromSettings() {
        Map<String, Object> lastRun = new LinkedHashMap<>();
        lastRun.put("startTime", 1_000_000L);
        lastRun.put("elapsedMillis", 65_000L);
        lastRun.put("totalUrlCount", 100);
        lastRun.put("existingUrlCount", 3);
        lastRun.put("filteredUrlCount", 4);
        lastRun.put("invalidUrlCount", 5);
        lastRun.put("savedResourceCount", 42);
        lastRun.put("savedImageCount", 7);
        lastRun.put("duplicatedContentCount", 2);
        lastRun.put("remainingUrlCount", 0);

        CatalogSummary summary = new CatalogSummary(details(), Map.of("lastRun", lastRun));

        assertThat(summary.isLive()).isFalse();
        assertThat(summary.isCompleted()).isTrue();
        assertThat(summary.getSavedResourceCount()).isEqualTo(42L);
        assertThat(summary.getElapsedMillis()).isEqualTo(65_000L);
        // there is no endTime in the file, so it is the start plus what the run took
        assertThat(summary.getEndTime()).isEqualTo(1_065_000L);
        assertThat(summary.getElapsedTime()).isEqualTo("0h 1m 5s");
        assertThat(summary.getProgress()).isEqualTo(1d);
    }

    @Test
    @DisplayName("a run that was cut short says so, and does not claim to have completed")
    void carriesTheInterruptionReason() {
        CatalogSummary summary = new CatalogSummary(details(),
                Map.of("lastRun", Map.of("savedResourceCount", 9, "completionReason",
                        "interrupted by request", "interrupted", true)));

        assertThat(summary.isCompleted()).isFalse();
        assertThat(summary.isInterrupted()).isTrue();
        assertThat(summary.getCompletionReason()).isEqualTo("interrupted by request");
        assertThat(summary.getProgress()).isZero();
    }

    @Test
    @DisplayName("a run that reached a limit is a completion, reason and all")
    void carriesTheCompletionReason() {
        CatalogSummary summary = new CatalogSummary(details(),
                Map.of("lastRun", Map.of("savedResourceCount", 9, "completionReason",
                        "reached maxFetchSize", "interrupted", false)));

        assertThat(summary.isCompleted()).isTrue();
        assertThat(summary.isInterrupted()).isFalse();
        assertThat(summary.getCompletionReason()).isEqualTo("reached maxFetchSize");
    }

    @Test
    @DisplayName("a settings file from before the reason was recorded still reads")
    void readsAnOlderSettingsFile() {
        CatalogSummary summary = new CatalogSummary(details(),
                Map.of("lastRun", Map.of("savedResourceCount", 9, "interruptionReason",
                        "maxFetchSize reached")));

        // the old file only wrote a reason when the run was cut short, so one being there means it
        assertThat(summary.isInterrupted()).isTrue();
        assertThat(summary.getCompletionReason()).isEqualTo("maxFetchSize reached");
    }

    @Test
    @DisplayName("a settings file with no run in it yet reads as zeroes, not as a failure")
    void survivesASettingsFileWithoutARun() {
        CatalogSummary summary = new CatalogSummary(details(), Map.of());

        assertThat(summary.getSavedResourceCount()).isZero();
        assertThat(summary.getStartTime()).isZero();
        assertThat(summary.getEndTime()).isZero();
        assertThat(summary.getElapsedTime()).isEqualTo("0h 0m 0s");
        assertThat(summary.getCatalogName()).isEqualTo("books");
    }

    @Test
    @DisplayName("a counter written by an older version as something other than a number is ignored")
    void ignoresAValueThatIsNotANumber() {
        CatalogSummary summary =
                new CatalogSummary(details(), Map.of("lastRun", Map.of("savedResourceCount", "n/a")));

        assertThat(summary.getSavedResourceCount()).isZero();
    }

    private static CatalogDetails details() {
        Catalog catalog = new Catalog();
        catalog.setId("catalog-1");
        catalog.setName("books");
        catalog.setUrl("https://books.toscrape.com");
        catalog.setCat("default");
        catalog.setIndexVersion(3);
        catalog.setSearchVersion(2);
        return new CatalogDetailsImpl(catalog, new WebCrawlerProperties());
    }

    private static Dashboard dashboard(boolean completed) {
        return dashboard(completed, details());
    }

    private static Dashboard dashboard(boolean completed, CatalogDetails carried) {
        return new Dashboard() {

            @Override
            public long getTotalUrlCount() {
                return 100L;
            }

            @Override
            public long getHandledUrlCount() {
                return 100L;
            }

            @Override
            public long getInvalidUrlCount() {
                return 5L;
            }

            @Override
            public long getExistingUrlCount() {
                return 3L;
            }

            @Override
            public long getFilteredUrlCount() {
                return 4L;
            }

            @Override
            public long getSavedResourceCount() {
                return 42L;
            }

            @Override
            public long getIndexedResourceCount() {
                return 40L;
            }

            @Override
            public long getSavedImageCount() {
                return 7L;
            }

            @Override
            public long getDuplicatedContentCount() {
                return 2L;
            }

            @Override
            public long getStartTime() {
                return 1_000_000L;
            }

            @Override
            public long getEndTime() {
                return 1_065_000L;
            }

            @Override
            public long getElapsedTime() {
                return 65_000L;
            }

            @Override
            public long getLastModified() {
                return 1_065_000L;
            }

            @Override
            public boolean isCompleted() {
                return completed;
            }

            @Override
            public double getAverageExecutionTime() {
                return 1.5d;
            }

            @Override
            public CatalogDetails getCatalogDetails() {
                return carried;
            }
        };
    }

}
