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
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.CatalogFixtures;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.WebCrawlerComponentFactory;
import com.github.greenfinger.core.component.acceptor.UrlPathAcceptor;
import com.github.greenfinger.core.component.completion.CompletionChecker;
import com.github.greenfinger.core.component.completion.FetchDurationCompletionChecker;
import com.github.greenfinger.core.component.completion.MaxFetchSizeCompletionChecker;
import com.github.greenfinger.core.component.dedup.ContentDedupFilter;
import com.github.greenfinger.core.component.dedup.ExistingUrlPathFilter;
import com.github.greenfinger.core.component.extractor.Extractor;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.component.state.DefaultGlobalStateManager;
import com.github.greenfinger.core.component.state.GlobalStateManager;
import com.github.greenfinger.core.model.Catalog;

/**
 * The two things quiet counters can mean.
 *
 * <p>
 * The watchdog is asked directly rather than waited for: what is under test is which of the two
 * answers it gives and whether the version is publishable afterwards, and neither of those is
 * about the clock. The clock is the trivial half.
 *
 * @Description: CompletionWatchdogTest
 * @Author: Fred Feng
 * @Date: 04/09/2026
 * @Version 2.0.0
 */
class CompletionWatchdogTest {

    /** A state manager whose idea of "nothing has moved" is set by the test. */
    private static class Quiet extends DefaultGlobalStateManager {

        boolean idle;

        Quiet(CatalogDetails catalogDetails) {
            super(catalogDetails);
        }

        @Override
        public boolean isTimeout(long delay, TimeUnit timeUnit) {
            return idle;
        }
    }

    /**
     * Only the state manager and the checkers are real. The rest of a context opens rocksdb, an
     * http client and a dedup store, none of which the watchdog touches.
     */
    private DefaultWebCrawlerExecutionContext contextOf(CatalogDetails details, Quiet state) {
        WebCrawlerComponentFactory factory = new WebCrawlerComponentFactory() {

            @Override
            public List<CompletionChecker> getCompletionCheckers(CatalogDetails catalogDetails) {
                // mutable: the context sorts this list by @Order
                return new java.util.ArrayList<>(List.of(new MaxFetchSizeCompletionChecker(),
                        new FetchDurationCompletionChecker()));
            }

            @Override
            public List<UrlPathAcceptor> getUrlPathAcceptors(CatalogDetails catalogDetails) {
                return new java.util.ArrayList<>();
            }

            @Override
            public Extractor getExtractor(CatalogDetails catalogDetails) {
                return new com.github.greenfinger.core.component.extractor.NamedExtractor() {

                    @Override
                    public String getName() {
                        return "none";
                    }

                    @Override
                    public String extractHtml(
                            CatalogDetails catalogDetails, String referUrl, String url,
                            java.nio.charset.Charset charset, CrawlTask task) {
                        throw new UnsupportedOperationException("nothing is fetched here");
                    }
                };
            }

            @Override
            public ExistingUrlPathFilter getExistingUrlPathFilter(CatalogDetails catalogDetails) {
                return new ExistingUrlPathFilter() {

                    @Override
                    public String getName() {
                        return "none";
                    }

                    @Override
                    public boolean mightExist(String path) {
                        return false;
                    }
                };
            }

            @Override
            public ContentDedupFilter getContentDedupFilter(CatalogDetails catalogDetails) {
                return new ContentDedupFilter.NoOp();
            }

            @Override
            public GlobalStateManager getGlobalStateManager(CatalogDetails catalogDetails,
                    boolean initiator) {
                return state;
            }

            @Override
            public CrawlFrontier getCrawlFrontier(CatalogDetails catalogDetails) {
                return null;
            }
        };
        return new DefaultWebCrawlerExecutionContext(details, factory,
                new WebCrawlerProperties(), true);
    }

    private Quiet started(CatalogDetails details) throws Exception {
        Quiet state = new Quiet(details);
        state.afterPropertiesSet();
        return state;
    }

    @Test
    @DisplayName("a site that ran out of urls is a completion, and it is publishable")
    void anExhaustedSiteCompletes() throws Exception {
        CatalogDetails details = CatalogFixtures.details();
        Quiet state = started(details);
        DefaultWebCrawlerExecutionContext context = contextOf(details, state);
        context.afterPropertiesSet();
        try {
            state.incrementCount(0L, CountingType.URL_TOTAL_COUNT, 12);
            state.incrementCount(0L, CountingType.HANDLED_URL_COUNT, 12);

            // still moving: nothing is decided while the counters are alive
            context.watch();
            assertThat(context.isCompleted()).isFalse();

            state.idle = true;
            context.watch();

            assertThat(context.isCompleted()).isTrue();
            assertThat(context.isInterrupted()).isFalse();
            assertThat(context.getCompletionReason()).contains("exhausted", "12");
        } finally {
            context.destroy();
        }
    }

    @Test
    @DisplayName("urls nobody accounted for is an intervention, and it publishes nothing")
    void urlsLeftOutstandingInterrupt() throws Exception {
        CatalogDetails details = CatalogFixtures.details();
        Quiet state = started(details);
        DefaultWebCrawlerExecutionContext context = contextOf(details, state);
        context.afterPropertiesSet();
        try {
            state.incrementCount(0L, CountingType.URL_TOTAL_COUNT, 40);
            state.incrementCount(0L, CountingType.HANDLED_URL_COUNT, 31);
            state.idle = true;

            context.watch();

            assertThat(context.isCompleted()).isTrue();
            assertThat(context.isInterrupted()).isTrue();
            assertThat(context.getCompletionReason()).contains("stalled", "9");
        } finally {
            context.destroy();
        }
    }

    @Test
    @DisplayName("a crawl that never dispatched anything failed, it did not finish")
    void anEmptyCrawlIsNotAnExhaustedSite() throws Exception {
        CatalogDetails details = CatalogFixtures.details();
        Quiet state = started(details);
        DefaultWebCrawlerExecutionContext context = contextOf(details, state);
        context.afterPropertiesSet();
        try {
            state.idle = true;

            context.watch();

            // 0 == 0 would read as an exhausted site without the guard, which would publish an
            // empty version for a crawl whose entry point was never reached
            assertThat(context.isInterrupted()).isTrue();
            assertThat(context.getCompletionReason()).isEqualTo("nothing was ever dispatched");
        } finally {
            context.destroy();
        }
    }

    @Test
    @DisplayName("maxFetchSize is asked on the way past, fetchDuration by the clock")
    void eachCheckerIsAskedByTheRightHalf() throws Exception {
        Catalog catalog = CatalogFixtures.catalog();
        catalog.setMaxFetchSize(2);
        catalog.setDuration(0L);
        CatalogDetails details = CatalogFixtures.details(catalog);
        Quiet state = started(details);
        DefaultWebCrawlerExecutionContext context = contextOf(details, state);
        context.afterPropertiesSet();
        try {
            // the fixture counts saved resources, which is what maxFetchSize is measured against
            state.incrementCount(0L, CountingType.SAVED_RESOURCE_COUNT, 3);

            // the scheduled half does not know about maxFetchSize
            assertThat(context.ask(true)).isFalse();
            assertThat(context.checkCompletion()).isTrue();
            assertThat(context.getCompletionReason()).contains("maxFetchSize");
            assertThat(context.isInterrupted()).isFalse();
        } finally {
            context.destroy();
        }
    }

    @Test
    @DisplayName("the first reason wins, so a wind-down does not overwrite the limit that ended it")
    void theFirstReasonWins() throws Exception {
        CatalogDetails details = CatalogFixtures.details();
        Quiet state = started(details);

        state.setCompleted(true, "reached maxFetchSize: savedResourceCount = 31 > 30");
        state.interrupt("interrupted: the process was asked to stop");

        assertThat(state.getDashboard().getCompletionReason()).startsWith("reached maxFetchSize");
        assertThat(state.getDashboard().isInterrupted()).isFalse();
    }
}
