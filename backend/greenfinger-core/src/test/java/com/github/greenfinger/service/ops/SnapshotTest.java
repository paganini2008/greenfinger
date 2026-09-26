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

package com.github.greenfinger.service.ops;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.core.component.extractor.ThreadWait;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.model.ContentMode;
import com.github.greenfinger.core.model.ExtractorType;
import com.github.greenfinger.core.model.OutputType;

/**
 * The two shapes that cross the wire.
 *
 * <p>
 * A crawl is described by two interfaces, and an interface cannot be read back out of json. These
 * are the flattened copies, and what matters about them is exactly this: written out and read back
 * they still answer what the original answered, because the far end renders them as though they
 * were the real thing.
 *
 * @Description: SnapshotTest
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
class SnapshotTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("A catalog survives the trip, defaults and all")
    void catalogSurvivesTheTrip() throws Exception {
        CatalogSnapshot original = catalog();

        CatalogSnapshot copy = objectMapper.readValue(objectMapper.writeValueAsString(original),
                CatalogSnapshot.class);

        assertThat(copy.getId()).isEqualTo("abc");
        assertThat(copy.getName()).isEqualTo("books");
        assertThat(copy.getUrl()).isEqualTo("https://books.toscrape.com");
        assertThat(copy.getStartUrl()).isEqualTo("https://books.toscrape.com/catalogue/");
        assertThat(copy.getSitemapUrl()).isEqualTo("https://books.toscrape.com/sitemap.xml");
        assertThat(copy.getCategory()).isEqualTo("other");
        assertThat(copy.getPathPatterns()).containsExactly("**.toscrape.com/**");
        assertThat(copy.getExcludedPathPatterns()).containsExactly("**/login/**");
        assertThat(copy.getPageEncoding()).isEqualTo("UTF-8");
        assertThat(copy.getMaxFetchSize()).isEqualTo(500);
        assertThat(copy.getMaxFetchDepth()).isEqualTo(-1);
        assertThat(copy.getThreadWait()).isEqualTo(ThreadWait.RANDOM_SLEEP);
        assertThat(copy.getFetchInterval()).isEqualTo(1000L);
        assertThat(copy.getFetchDuration()).isEqualTo(30L);
        assertThat(copy.getCountingType()).isEqualTo(CountingType.SAVED_RESOURCE_COUNT);
        assertThat(copy.getMaxRetryCount()).isEqualTo(1);
        assertThat(copy.getUrlPathAcceptors()).containsExactly("domain");
        assertThat(copy.getUrlPathFilter()).isEqualTo("rocksdb");
        assertThat(copy.getExtractor()).isEqualTo(ExtractorType.ADAPTIVE);
        assertThat(copy.getVersion()).isEqualTo(2);
        assertThat(copy.getSearchVersion()).isEqualTo(1);
        assertThat(copy.getMaxVersions()).isEqualTo(10);
        assertThat(copy.getRunningState()).isEqualTo("none");
        assertThat(copy.getOutputTypes()).containsExactly(OutputType.FILE);
        assertThat(copy.isImageEnabled()).isTrue();
        assertThat(copy.getContentMode()).isEqualTo(ContentMode.TEXT_IMAGE);
    }

    @Test
    @DisplayName("Copying a catalog copies what it says, not what it is")
    void copyingTakesTheAnswers() {
        assertThat(CatalogSnapshot.of(null)).isNull();
        CatalogSnapshot copy = CatalogSnapshot.of(catalog());
        assertThat(copy.getId()).isEqualTo("abc");
        assertThat(copy.getOutputTypes()).containsExactly(OutputType.FILE);
    }

    @Test
    @DisplayName("The counters survive the trip, with the catalog they belong to")
    void countersSurviveTheTrip() throws Exception {
        DashboardSnapshot original = dashboard();

        DashboardSnapshot copy = objectMapper.readValue(objectMapper.writeValueAsString(original),
                DashboardSnapshot.class);

        assertThat(copy.getTotalUrlCount()).isEqualTo(120L);
        assertThat(copy.getHandledUrlCount()).isEqualTo(100L);
        assertThat(copy.getInvalidUrlCount()).isEqualTo(3L);
        assertThat(copy.getConsecutiveFailures()).isEqualTo(2);
        assertThat(copy.getLastFailure()).isEqualTo("timeout");
        assertThat(copy.getExistingUrlCount()).isEqualTo(9L);
        assertThat(copy.getFilteredUrlCount()).isEqualTo(8L);
        assertThat(copy.getSavedResourceCount()).isEqualTo(70L);
        assertThat(copy.getIndexedResourceCount()).isEqualTo(69L);
        assertThat(copy.getVectoredResourceCount()).isEqualTo(68L);
        assertThat(copy.getSavedImageCount()).isEqualTo(12L);
        assertThat(copy.getDuplicatedContentCount()).isEqualTo(4L);
        assertThat(copy.getAbandonedUrlCount()).isEqualTo(1L);
        assertThat(copy.getStartTime()).isEqualTo(1000L);
        assertThat(copy.getEndTime()).isEqualTo(2000L);
        assertThat(copy.getElapsedTime()).isEqualTo(1000L);
        assertThat(copy.getLastModified()).isEqualTo(1900L);
        assertThat(copy.isCompleted()).isTrue();
        assertThat(copy.getCompletionReason()).isEqualTo("maxFetchSize");
        assertThat(copy.isInterrupted()).isFalse();
        assertThat(copy.getAverageExecutionTime()).isEqualTo(12.5d);
        assertThat(copy.getCatalogDetails().getName()).isEqualTo("books");
    }

    @Test
    @DisplayName("Copying the counters copies the catalog with them")
    void copyingCarriesTheCatalog() {
        assertThat(DashboardSnapshot.of(null)).isNull();
        // the renderer asks a dashboard for its catalog, so a copy that dropped it would draw a
        // progress bar with nothing to measure against
        DashboardSnapshot copy = DashboardSnapshot.of(dashboard());
        assertThat(copy.getCatalogDetails()).isNotNull();
        assertThat(copy.getSavedResourceCount()).isEqualTo(70L);
        assertThat(copy.getProgress()).isBetween(0d, 1d);
    }

    private CatalogSnapshot catalog() {
        CatalogSnapshot catalog = new CatalogSnapshot();
        catalog.setId("abc");
        catalog.setName("books");
        catalog.setUrl("https://books.toscrape.com");
        catalog.setStartUrl("https://books.toscrape.com/catalogue/");
        catalog.setSitemapUrl("https://books.toscrape.com/sitemap.xml");
        catalog.setCategory("other");
        catalog.setPathPatterns(List.of("**.toscrape.com/**"));
        catalog.setExcludedPathPatterns(List.of("**/login/**"));
        catalog.setPageEncoding("UTF-8");
        catalog.setMaxFetchSize(500);
        catalog.setMaxFetchDepth(-1);
        catalog.setThreadWait(ThreadWait.RANDOM_SLEEP);
        catalog.setFetchInterval(1000L);
        catalog.setFetchDuration(30L);
        catalog.setCountingType(CountingType.SAVED_RESOURCE_COUNT);
        catalog.setMaxRetryCount(1);
        catalog.setUrlPathAcceptors(List.of("domain"));
        catalog.setUrlPathFilter("rocksdb");
        catalog.setExtractor(ExtractorType.ADAPTIVE);
        catalog.setVersion(2);
        catalog.setSearchVersion(1);
        catalog.setMaxVersions(10);
        catalog.setRunningState("none");
        catalog.setOutputTypes(Set.of(OutputType.FILE));
        catalog.setImageEnabled(true);
        catalog.setContentMode(ContentMode.TEXT_IMAGE);
        return catalog;
    }

    private DashboardSnapshot dashboard() {
        DashboardSnapshot dashboard = new DashboardSnapshot();
        dashboard.setTotalUrlCount(120L);
        dashboard.setHandledUrlCount(100L);
        dashboard.setInvalidUrlCount(3L);
        dashboard.setConsecutiveFailures(2);
        dashboard.setLastFailure("timeout");
        dashboard.setExistingUrlCount(9L);
        dashboard.setFilteredUrlCount(8L);
        dashboard.setSavedResourceCount(70L);
        dashboard.setIndexedResourceCount(69L);
        dashboard.setVectoredResourceCount(68L);
        dashboard.setSavedImageCount(12L);
        dashboard.setDuplicatedContentCount(4L);
        dashboard.setAbandonedUrlCount(1L);
        dashboard.setStartTime(1000L);
        dashboard.setEndTime(2000L);
        dashboard.setElapsedTime(1000L);
        dashboard.setLastModified(1900L);
        dashboard.setCompleted(true);
        dashboard.setCompletionReason("maxFetchSize");
        dashboard.setInterrupted(false);
        dashboard.setAverageExecutionTime(12.5d);
        dashboard.setCatalogDetails(catalog());
        return dashboard;
    }

}
