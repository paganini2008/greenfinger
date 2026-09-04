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

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.utils.BeanLifeCycleUtils;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.WebCrawlerComponentFactory;
import com.github.greenfinger.core.component.acceptor.UrlPathAcceptor;
import com.github.greenfinger.core.component.dedup.ContentDedupFilter;
import com.github.greenfinger.core.component.dedup.ExistingUrlPathFilter;
import com.github.greenfinger.core.component.extractor.Extractor;
import com.github.greenfinger.core.component.completion.CompletionChecker;
import com.github.greenfinger.core.component.state.Dashboard;
import com.github.greenfinger.core.component.state.GlobalStateManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * One crawl's components, and the two clocks that end it.
 *
 * <h2>A fresh one per run</h2>
 * 1.x made this bean {@code prototype} so that every crawl -- update, crawl or rebuild -- got a
 * context that had just run {@code afterPropertiesSet}. The same holds here by construction: the
 * launcher builds one per run and destroys it when the run ends. What that initialisation resets
 * is not only these fields but the counters themselves, which in a cluster outlive the process.
 *
 * <h2>Completion is not decided here, it is noticed here</h2>
 * Whether the crawl is over is a property of the shared state, not of this node: the two checkers
 * are handed the dashboard and answer a question about it, so every node reaches the same answer
 * and the first one to notice writes the flag. {@code maxFetchSize} is asked on the crawl's own
 * thread, because the counter it watches can only move when a page goes past. {@code fetchDuration}
 * is asked by the clock, because time passes whether or not anything is being fetched.
 *
 * <h2>The watchdog, and why quiet is ambiguous</h2>
 * The clock also watches for the counters standing still, which means one of two opposite things.
 * A small site runs out of urls: everything dispatched has been handled, and the quiet is the
 * crawl being done -- that is a completion and the version is published. Or a node stopped
 * answering while holding urls and the counters will never meet -- that is an intervention, and
 * publishing it would put half a version in front of searches. The two are told apart by whether
 * the counters agree, which is why that comparison exists without being a third checker.
 *
 * @Description: DefaultWebCrawlerExecutionContext
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class DefaultWebCrawlerExecutionContext implements WebCrawlerExecutionContext {

    private final CatalogDetails catalogDetails;
    private final WebCrawlerComponentFactory componentFactory;
    private final WebCrawlerProperties webCrawlerProperties;
    /** Whether this node started the run, as opposed to joining one already under way. */
    private final boolean initiator;

    @Getter
    private List<CompletionChecker> completionCheckers;
    @Getter
    private List<UrlPathAcceptor> urlPathAcceptors;
    @Getter
    private Extractor extractor;
    @Getter
    private ExistingUrlPathFilter existingUrlPathFilter;
    @Getter
    private ContentDedupFilter contentDedupFilter;
    @Getter
    private GlobalStateManager globalStateManager;
    @Getter
    private CrawlFrontier crawlFrontier;

    private ScheduledExecutorService clock;

    public DefaultWebCrawlerExecutionContext(CatalogDetails catalogDetails,
            WebCrawlerComponentFactory componentFactory,
            WebCrawlerProperties webCrawlerProperties, boolean initiator) {
        this.catalogDetails = catalogDetails;
        this.componentFactory = componentFactory;
        this.webCrawlerProperties = webCrawlerProperties;
        this.initiator = initiator;
    }

    @Override
    public CatalogDetails getCatalogDetails() {
        return catalogDetails;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("Initializing crawler components for catalog '{}'", catalogDetails);

        completionCheckers = componentFactory.getCompletionCheckers(catalogDetails);
        AnnotationAwareOrderComparator.sort(completionCheckers);
        BeanLifeCycleUtils.afterPropertiesSet(completionCheckers);

        urlPathAcceptors = componentFactory.getUrlPathAcceptors(catalogDetails);
        AnnotationAwareOrderComparator.sort(urlPathAcceptors);
        BeanLifeCycleUtils.afterPropertiesSet(urlPathAcceptors);
        log.info("UrlPathAcceptor chain: {}",
                urlPathAcceptors.stream().map(UrlPathAcceptor::getName).toList());

        existingUrlPathFilter = componentFactory.getExistingUrlPathFilter(catalogDetails);
        BeanLifeCycleUtils.afterPropertiesSet(existingUrlPathFilter);
        log.info("Url dedup: {}", existingUrlPathFilter.getName());

        contentDedupFilter = componentFactory.getContentDedupFilter(catalogDetails);
        BeanLifeCycleUtils.afterPropertiesSet(contentDedupFilter);
        log.info("Content dedup: {}", contentDedupFilter.getName());

        crawlFrontier = componentFactory.getCrawlFrontier(catalogDetails);
        BeanLifeCycleUtils.afterPropertiesSet(crawlFrontier);

        extractor = componentFactory.getExtractor(catalogDetails);
        BeanLifeCycleUtils.afterPropertiesSet(extractor);
        log.info("Extractor: {}", extractor.getName());

        globalStateManager = componentFactory.getGlobalStateManager(catalogDetails, initiator);
        BeanLifeCycleUtils.afterPropertiesSet(globalStateManager);

        // started last: it reads the state manager, which has to exist before the first tick
        long interval = Math.max(200L, webCrawlerProperties.getCompletionCheckInterval().toMillis());
        clock = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "greenfinger-completion");
            thread.setDaemon(true);
            return thread;
        });
        clock.scheduleWithFixedDelay(this::tickQuietly, interval, interval,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() throws Exception {
        if (clock != null) {
            clock.shutdownNow();
        }
        BeanLifeCycleUtils.destroyQuietly(extractor);
        BeanLifeCycleUtils.destroyQuietly(crawlFrontier);
        BeanLifeCycleUtils.destroyQuietly(contentDedupFilter);
        BeanLifeCycleUtils.destroyQuietly(existingUrlPathFilter);
        BeanLifeCycleUtils.destroyQuietly(urlPathAcceptors);
        BeanLifeCycleUtils.destroyQuietly(completionCheckers);
        BeanLifeCycleUtils.destroyQuietly(globalStateManager);
        log.info("Destroyed crawler components for catalog '{}'", catalogDetails);
    }

    @Override
    public boolean isUrlAcceptable(String referUrl, String url, CrawlTask task) {
        if (urlPathAcceptors == null) {
            return true;
        }
        for (UrlPathAcceptor urlPathAcceptor : urlPathAcceptors) {
            if (!urlPathAcceptor.accept(catalogDetails, referUrl, url, task)) {
                if (log.isTraceEnabled()) {
                    log.trace("Rejected '{}' by {}", url, urlPathAcceptor.getName());
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isCompleted() {
        return globalStateManager.isCompleted();
    }

    @Override
    public boolean checkCompletion() {
        ask(false);
        return isCompleted();
    }

    @Override
    public String getCompletionReason() {
        return globalStateManager.getDashboard().getCompletionReason();
    }

    @Override
    public boolean isInterrupted() {
        return globalStateManager.getDashboard().isInterrupted();
    }

    /** One tick of the clock: the scheduled checkers, then the watchdog. */
    private void tickQuietly() {
        try {
            if (ask(true)) {
                return;
            }
            watch();
        } catch (RuntimeException e) {
            log.warn("Completion check failed: {}", e.getMessage(), e);
        }
    }

    /**
     * @param scheduled which half of the checkers to ask.
     * @return whether the crawl is over, by any means.
     */
    boolean ask(boolean scheduled) {
        if (isCompleted()) {
            return true;
        }
        Dashboard dashboard = globalStateManager.getDashboard();
        for (CompletionChecker checker : completionCheckers) {
            if (checker.scheduled() != scheduled) {
                continue;
            }
            if (checker.isCompleted(catalogDetails, dashboard)) {
                globalStateManager.setCompleted(true,
                        checker.getReason(catalogDetails, dashboard));
                return true;
            }
        }
        return false;
    }

    /**
     * The counters have stood still for the whole idle timeout. That means one of two opposite
     * things, and which one decides whether the version is published.
     *
     * <p>
     * If every url that was dispatched has been handled, there is nothing queued or in flight
     * anywhere: the site simply ran out of urls, and the quiet is the crawl being finished. That
     * is a completion, and it does not wait for {@code fetchDuration} -- a small site is done when
     * it is done. If the two do not agree, some urls were held by a node that stopped answering,
     * or the network went away mid-crawl; those pages will never arrive, and publishing what did
     * would put half a version in front of searches.
     */
    void watch() {
        long idleTimeout = webCrawlerProperties.getIdleTimeout().toMillis();
        if (idleTimeout <= 0
                || !globalStateManager.isTimeout(idleTimeout, TimeUnit.MILLISECONDS)) {
            return;
        }
        Dashboard dashboard = globalStateManager.getDashboard();
        long dispatched = dashboard.getTotalUrlCount();
        long handled = dashboard.getHandledUrlCount();
        if (dispatched == 0) {
            // Zero equals zero, and reading that as an exhausted site would end every crawl the
            // moment it began. Quiet with nothing ever dispatched means the entry point never
            // went anywhere, which is a failure rather than a finish.
            log.error("Catalog '{}' never dispatched a url and has been quiet for {}:"
                    + " the entry point was never reached.", catalogDetails.getName(),
                    webCrawlerProperties.getIdleTimeout());
            globalStateManager.interrupt("nothing was ever dispatched");
            return;
        }
        // At least, not exactly. Delivery through the cluster is at-least-once, so a task can be
        // handed to a worker twice and reported handled twice, and a run that finished perfectly
        // well would then read as one url short of itself for ever. What matters is that nothing
        // is still owed, and handled can only ever run ahead.
        if (handled >= dispatched) {
            log.info(
                    "Catalog '{}' has run out of urls: {} dispatched and all of them handled.",
                    catalogDetails.getName(), dispatched);
            globalStateManager.setCompleted(true,
                    String.format("the site is exhausted: all %d url(s) handled", dispatched));
            return;
        }
        log.error(
                "Catalog '{}' has not moved for {} with {} url(s) dispatched and {} handled:"
                        + " {} are unaccounted for, which means a node stopped answering."
                        + " Ending the crawl without publishing; the missing pages are still in a"
                        + " frontier and a resume will pick them up.",
                catalogDetails.getName(), webCrawlerProperties.getIdleTimeout(), dispatched,
                handled, dispatched - handled);
        globalStateManager.interrupt(String.format("stalled: %d url(s) unaccounted for",
                dispatched - handled));
    }

}
