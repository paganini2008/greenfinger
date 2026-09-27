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

package com.github.greenfinger.shell.render;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsImpl;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.component.state.Dashboard;
import com.github.greenfinger.core.component.state.DefaultGlobalStateManager;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.OutputType;

/**
 * 
 * @Description: DashboardRendererTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class DashboardRendererTest {

    private CatalogDetails catalogDetails;
    private DefaultGlobalStateManager stateManager;
    private final DashboardRenderer renderer = new DashboardRenderer();

    @BeforeEach
    void setUp() throws Exception {
        Catalog catalog = new Catalog();
        catalog.setId("0192f0c8-1234-7000-8000-0000000000cc");
        catalog.setName("example");
        catalog.setUrl("https://www.example.com");
        catalog.setCat("tech");
        catalog.setPathPattern("**.example.com");
        catalog.setMaxFetchSize(100);
        catalog.setDuration(30L);
        catalog.setCountingType(CountingType.SAVED_RESOURCE_COUNT);
        catalog.setOutputTypes(java.util.Set.of(OutputType.FILE));
        catalog.setIndexVersion(0);
        catalogDetails = new CatalogDetailsImpl(catalog, new WebCrawlerProperties());

        stateManager = new DefaultGlobalStateManager(catalogDetails);
        stateManager.afterPropertiesSet();
    }

    private Dashboard dashboard() {
        return stateManager.getDashboard();
    }

    @Test
    @DisplayName("a row per node, read the way the state manager actually keeps it")
    void perNodeIsTransposed() {
        // counter, then node: what perNodeCounters() returns. Read the other way round, every row
        // was named after a counter and every number came out zero
        Map<String, Map<String, Long>> byCounter = Map.of(
                CountingType.SAVED_RESOURCE_COUNT.getRepr(), Map.of("node-a", 40L, "node-b", 4L),
                CountingType.HANDLED_URL_COUNT.getRepr(), Map.of("node-a", 50L, "node-b", 6L));

        Map<String, Map<String, Long>> byNode = DashboardRenderer.byNode(byCounter);

        assertThat(byNode).containsOnlyKeys("node-a", "node-b");
        assertThat(byNode.get("node-a"))
                .containsEntry(CountingType.SAVED_RESOURCE_COUNT.getRepr(), 40L)
                .containsEntry(CountingType.HANDLED_URL_COUNT.getRepr(), 50L);
        assertThat(byNode.get("node-b"))
                .containsEntry(CountingType.SAVED_RESOURCE_COUNT.getRepr(), 4L);
    }

    @Test
    @DisplayName("nothing per node is empty rather than an exception")
    void perNodeTakesNothing() {
        assertThat(DashboardRenderer.byNode(null)).isEmpty();
        assertThat(DashboardRenderer.byNode(Map.of())).isEmpty();
    }

    @Test
    @DisplayName("the live view shows the site, a bar and every counter")
    void rendersTheLiveView() {
        stateManager.incrementCount(0L, CountingType.SAVED_RESOURCE_COUNT, 12);
        stateManager.incrementCount(0L, CountingType.SAVED_IMAGE_COUNT, 40);

        String rendered = renderer.render(catalogDetails, dashboard(), 99L);

        assertThat(rendered).contains("example", "https://www.example.com");
        assertThat(rendered).contains("Pages saved", "Images saved", "Queued");
        assertThat(rendered).contains("12", "40", "99");
        assertThat(rendered).contains("%");
    }

    @Test
    @DisplayName("both limits get a bar, because either one can be the one that ends the crawl")
    void drawsABarPerLimit() {
        stateManager.incrementCount(5L, CountingType.SAVED_RESOURCE_COUNT, 25);

        String rendered = renderer.render(catalogDetails, dashboard(), 10L);

        assertThat(rendered).contains("size", "time");
        assertThat(rendered).contains("savedResourceCount 25 / 100");
        assertThat(rendered).contains("elapsed");
        // the counted pages took measurable time, so the remaining ones can be estimated
        assertThat(rendered).contains("left");
    }

    @Test
    @DisplayName("no limit set is said, not drawn as a full bar")
    void handlesAnAbsentLimit() {
        Catalog unlimited = new Catalog();
        unlimited.setId("0192f0c8-1234-7000-8000-0000000000cd");
        unlimited.setName("unlimited");
        unlimited.setUrl("https://www.example.com");
        unlimited.setCat("tech");
        unlimited.setMaxFetchSize(0);
        unlimited.setDuration(0L);
        unlimited.setCountingType(CountingType.SAVED_RESOURCE_COUNT);
        unlimited.setOutputTypes(java.util.Set.of(OutputType.FILE));
        unlimited.setIndexVersion(0);

        String rendered = renderer.render(
                new CatalogDetailsImpl(unlimited, new WebCrawlerProperties()), dashboard(), 0L);

        assertThat(rendered).contains("no limit");
    }

    @Test
    @DisplayName("the block is a fixed height, so it can be redrawn in place")
    void reportsAStableLineCount() {
        String rendered = renderer.render(catalogDetails, dashboard(), 0L);
        assertThat(rendered.lines().count()).isEqualTo(renderer.lineCount());
    }

    @Test
    @DisplayName("a queue length is omitted rather than shown as a guess when unknown")
    void handlesAnUnknownQueueLength() {
        assertThat(renderer.render(catalogDetails, dashboard(), -1L)).contains("-");
    }

    @Test
    void summaryExplainsWhyTheCrawlStopped() {
        stateManager.incrementCount(0L, CountingType.SAVED_RESOURCE_COUNT, 7);
        String rendered = renderer.renderSummary(catalogDetails, dashboard(),
                "reached maxFetchSize", 42L, "/tmp/out");

        assertThat(rendered).contains("Crawl finished").contains("reached maxFetchSize")
                .contains("/tmp/out").contains("42").contains("7");
        assertThat(rendered).as("outstanding work invites a resume").contains("resume");
    }

    @Test
    void aFinishedCrawlDoesNotSuggestResuming() {
        assertThat(renderer.renderSummary(catalogDetails, dashboard(), "frontier drained", 0L,
                "/tmp/out")).doesNotContain("resume");
    }

    @Test
    @DisplayName("the limit shown is whichever will actually stop the crawl")
    void namesTheNearerLimit() {
        stateManager.incrementCount(0L, CountingType.SAVED_RESOURCE_COUNT, 90);
        assertThat(renderer.render(catalogDetails, dashboard(), 0L))
                .contains("savedResourceCount");
    }

}
