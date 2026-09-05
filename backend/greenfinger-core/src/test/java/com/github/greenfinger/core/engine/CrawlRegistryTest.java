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
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.CatalogFixtures;
import com.github.greenfinger.core.WebCrawlerSemaphore;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.WebCrawlerComponentFactory;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.component.state.DefaultGlobalStateManager;
import com.github.greenfinger.core.component.state.GlobalStateManager;

/**
 * 
 * @Description: CrawlRegistryTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class CrawlRegistryTest {

    /**
     * The registry only reaches for the state manager and the completion flag, so a context with
     * those is enough to exercise it without opening any stores.
     */
    private WebCrawlerExecutionContext contextFor(CatalogDetails details) throws Exception {
        DefaultGlobalStateManager stateManager = new DefaultGlobalStateManager(details);
        stateManager.afterPropertiesSet();
        return new WebCrawlerExecutionContext() {

            @Override
            public CatalogDetails getCatalogDetails() {
                return details;
            }

            @Override
            public java.util.List<com.github.greenfinger.core.component.completion.CompletionChecker> getCompletionCheckers() {
                return java.util.List.of();
            }

            @Override
            public java.util.List<com.github.greenfinger.core.component.acceptor.UrlPathAcceptor> getUrlPathAcceptors() {
                return java.util.List.of();
            }

            @Override
            public com.github.greenfinger.core.component.extractor.Extractor getExtractor() {
                return null;
            }

            @Override
            public com.github.greenfinger.core.component.dedup.ExistingUrlPathFilter getExistingUrlPathFilter() {
                return null;
            }

            @Override
            public com.github.greenfinger.core.component.dedup.ContentDedupFilter getContentDedupFilter() {
                return null;
            }

            @Override
            public GlobalStateManager getGlobalStateManager() {
                return stateManager;
            }

            @Override
            public CrawlFrontier getCrawlFrontier() {
                return null;
            }

            @Override
            public boolean isUrlAcceptable(String referUrl, String url, CrawlTask task) {
                return true;
            }

            @Override
            public boolean isCompleted() {
                return stateManager.isCompleted();
            }

            @Override
            public boolean checkCompletion() {
                return isCompleted();
            }

            @Override
            public String getCompletionReason() {
                return stateManager.getDashboard().getCompletionReason();
            }

            @Override
            public boolean isInterrupted() {
                return stateManager.getDashboard().isInterrupted();
            }
        };
    }

    @Test
    void tracksWhatIsRunning() throws Exception {
        CrawlRegistry registry = new CrawlRegistry();
        CatalogDetails details = CatalogFixtures.details();

        assertThat(registry.isRunning("cat-1")).isFalse();
        registry.register("cat-1", contextFor(details));
        assertThat(registry.isRunning("cat-1")).isTrue();
        assertThat(registry.getRunningCatalogIds()).containsExactly("cat-1");
    }

    @Test
    @DisplayName("interrupting asks the crawl to stop rather than killing it")
    void interruptSetsCompletion() throws Exception {
        CrawlRegistry registry = new CrawlRegistry();
        WebCrawlerExecutionContext context = contextFor(CatalogFixtures.details());
        registry.register("cat-1", context);

        assertThat(registry.interrupt("cat-1")).isTrue();
        assertThat(context.isCompleted()).isTrue();
        assertThat(registry.isRunning("cat-1")).isFalse();
    }

    @Test
    void interruptingSomethingThatIsNotRunningSaysSo() {
        assertThat(new CrawlRegistry().interrupt("cat-99")).isFalse();
    }

    @Test
    @DisplayName("the counters survive the crawl, which is when a summary is usually wanted")
    void keepsTheFinalSnapshot() throws Exception {
        CrawlRegistry registry = new CrawlRegistry();
        WebCrawlerExecutionContext context = contextFor(CatalogFixtures.details());
        registry.register("cat-1", context);
        context.getGlobalStateManager().incrementCount(0L, CountingType.SAVED_RESOURCE_COUNT, 5);

        registry.unregister("cat-1");

        assertThat(registry.isRunning("cat-1")).isFalse();
        assertThat(registry.getDashboard("cat-1")).isPresent();
        assertThat(registry.getDashboard("cat-1").orElseThrow().getSavedResourceCount()).isEqualTo(5);
    }

    @Test
    void reportsNothingForACatalogItNeverSaw() {
        assertThat(new CrawlRegistry().getDashboard("cat-42")).isEmpty();
    }

    @Test
    void clearsEverything() throws Exception {
        CrawlRegistry registry = new CrawlRegistry();
        registry.register("cat-1", contextFor(CatalogFixtures.details()));
        registry.clear();
        assertThat(registry.getRunningCatalogIds()).isEmpty();
        assertThat(registry.getDashboard("cat-1")).isEmpty();
    }

    @Test
    @DisplayName("one crawl at a time; a second is refused rather than queued")
    void semaphoreAllowsOneCrawl() {
        WebCrawlerSemaphore semaphore = new WebCrawlerSemaphore();

        assertThat(semaphore.isOccupied()).isFalse();
        assertThat(semaphore.acquire("cat-1", 10, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(semaphore.isOccupied()).isTrue();
        assertThat(semaphore.getCatalogId()).isEqualTo("cat-1");

        assertThat(semaphore.acquire("cat-2", 10, TimeUnit.MILLISECONDS)).isFalse();

        semaphore.release();
        assertThat(semaphore.isOccupied()).isFalse();
        assertThat(semaphore.getCatalogId()).isNull();
        assertThat(semaphore.acquire("cat-2", 10, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    void releasingWhenFreeIsHarmless() {
        WebCrawlerSemaphore semaphore = new WebCrawlerSemaphore();
        semaphore.release();
        semaphore.release();
        assertThat(semaphore.acquire("cat-1", 10, TimeUnit.MILLISECONDS)).isTrue();
    }

}
