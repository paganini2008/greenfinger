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

package com.github.greenfinger.service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import com.github.greenfinger.core.WebCrawlerConstants;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.core.WebCrawlerExtractorProperties;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.WebCrawlerSemaphore;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsService;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.component.DefaultWebCrawlerComponentFactory;
import com.github.greenfinger.core.component.WebCrawlerComponentFactory;
import com.github.greenfinger.core.component.state.Dashboard;
import com.github.greenfinger.core.engine.CrawlCoordinator;
import com.github.greenfinger.core.engine.CrawlCoordinatorFactory;
import com.github.greenfinger.core.engine.CrawlRegistry;
import com.github.greenfinger.core.engine.CrawlRun;
import com.github.greenfinger.core.engine.CrawlTask;
import com.github.greenfinger.core.engine.CrawlerEngine;
import com.github.greenfinger.core.engine.WebCrawlerCompletionEvent;
import com.github.greenfinger.core.engine.DefaultWebCrawlerExecutionContext;
import com.github.greenfinger.core.engine.ImageFetcher;
import com.github.greenfinger.core.engine.WebCrawlerExecutionContext;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.output.BlobStore;
import com.github.greenfinger.core.output.FileLayout;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.core.utils.BeanLifeCycleUtils;
import com.github.greenfinger.output.CompositeOutputChannel;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.output.blob.FileOutputChannel;
import com.github.greenfinger.output.vector.EmbeddingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs one catalog.
 *
 * <p>
 * The definition always comes from {@link CatalogDetailsService}, never from request parameters, so
 * a crawl launched from the command line and one launched from a web front end are the same crawl.
 *
 * <p>
 * The three verbs differ only in what they do to the version and where they start:
 *
 * <ul>
 * <li><b>crawl</b> -- the current version, from the start url.</li>
 * <li><b>update</b> (and its other name, <b>resume</b>) -- the current version, picking up where the
 * last run stopped, with the url filter left populated so only newly appeared urls are taken.</li>
 * <li><b>rebuild</b> -- a new version, which gives an empty url filter and an empty frontier, so
 * the whole site is fetched again while the previous version stays intact and searchable.</li>
 * </ul>
 * 
 * @Description: CrawlerLauncher
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class CrawlerLauncher {

    private final WebCrawlerProperties webCrawlerProperties;
    private final WebCrawlerExtractorProperties extractorProperties;
    private final OutputFactory outputFactory;
    private final CatalogStore catalogStore;
    private final CatalogDetailsService catalogDetailsService;
    private final ResourceRecordStore recordStore;
    private final CrawlRegistry crawlRegistry;
    private final WebCrawlerSemaphore semaphore;
    private final VersionPruner versionPruner;

    /**
     * Where a discovered url goes. Swapping this one bean is what turns a single process into a
     * cluster; nothing else in the launch path knows the difference.
     */
    private final CrawlCoordinatorFactory coordinatorFactory;

    /**
     * Builds a run's components. Injected rather than constructed here so the cluster edition can
     * substitute the one component that has to change -- the counters, which stop being this
     * process's own the moment a second node joins the same crawl.
     */
    private final WebCrawlerComponentFactory componentFactory;

    /**
     * The version's row in {@code crawler_report}. Null when the application wired the crawler
     * without persistence for reports.
     */
    private final CrawlReportRecorder reportRecorder;

    /**
     * Where the end of a run is announced. Null in an application that wired the crawler without
     * a Spring context, which is what the tests do: there is then nowhere to publish to and
     * nothing listening, and the announcement is skipped rather than guarded against everywhere.
     */
    private final ApplicationEventPublisher eventPublisher;

    /** One report per run, written beside the version it produced. */
    private final CrawlReporter reporter = new CrawlReporter();

    public CrawlerEngine.Result crawl(String catalogId, Consumer<WebCrawlerExecutionContext> onReady)
            throws Exception {
        return crawl(catalogId, null, onReady);
    }

    /**
     * @param threads this run's worker count, or null for the configured one.
     */
    public CrawlerEngine.Result crawl(String catalogId, Integer threads,
            Consumer<WebCrawlerExecutionContext> onReady) throws Exception {
        return run(catalogId, CrawlTask.ACTION_CRAWL, null, false, threads, onReady);
    }

    /**
     * @param from an explicit starting url, overriding the automatic choice
     */
    public CrawlerEngine.Result update(String catalogId, String from,
            Consumer<WebCrawlerExecutionContext> onReady) throws Exception {
        return update(catalogId, from, false, onReady);
    }

    /**
     * @param refresh also revisit pages already crawled and merge whatever changed, rather than
     *        only looking for urls that have appeared since. Costs a fetch per known page; writes
     *        nothing for the ones that came back the same.
     */
    public CrawlerEngine.Result update(String catalogId, String from, boolean refresh,
            Consumer<WebCrawlerExecutionContext> onReady) throws Exception {
        return update(catalogId, from, refresh, null, onReady);
    }

    public CrawlerEngine.Result update(String catalogId, String from, boolean refresh,
            Integer threads, Consumer<WebCrawlerExecutionContext> onReady) throws Exception {
        if (refresh) {
            guardMergeLimits(catalogDetailsService.loadCatalogDetails(catalogId));
        }
        CrawlerEngine.Result result =
                run(catalogId, CrawlTask.ACTION_UPDATE, from, refresh, threads, onReady);
        if (refresh) {
            reportPartialMerge(result);
        }
        return result;
    }

    /**
     * A merge has to revisit every page it already has, so a limit smaller than what is already
     * stored cannot possibly finish -- and a merge that stops halfway is worse than one that
     * refuses, because the pages it never reached silently keep their old content while the ones
     * it did reach are up to date, and nothing says which is which.
     */
    private void guardMergeLimits(CatalogDetails catalogDetails) {
        long known = recordStore.countByCatalog(catalogDetails.getId(),
                catalogDetails.getVersion());
        int limit = catalogDetails.getMaxFetchSize() != null ? catalogDetails.getMaxFetchSize() : 0;
        if (limit > 0 && known >= limit) {
            throw new WebCrawlerException(String.format(
                    "A merge would have to revisit %d page(s) but maxFetchSize is %d, so it would"
                            + " stop part way and leave the rest silently stale."
                            + " Raise it above %d and run the merge again.",
                    known, limit, known));
        }
    }

    /**
     * The same problem discovered the other way round: the limit was large enough for what was
     * stored, but the site had grown past it.
     */
    private void reportPartialMerge(CrawlerEngine.Result result) {
        String reason = result.getReason();
        if (StringUtils.isNotBlank(reason)
                && (reason.contains("maxFetchSize") || reason.contains("duration"))) {
            throw new WebCrawlerException(String.format(
                    "The merge stopped early (%s) with %d url(s) still queued, so some pages keep"
                            + " their old content and nothing records which. Raise the limit and"
                            + " run the merge again.",
                    reason, result.getRemaining()));
        }
    }

    /**
     * Starts a new version. Nothing is deleted; the previous version keeps serving search until
     * this one finishes.
     */
    public CrawlerEngine.Result rebuild(String catalogId,
            Consumer<WebCrawlerExecutionContext> onReady) throws Exception {
        return rebuild(catalogId, null, onReady);
    }

    public CrawlerEngine.Result rebuild(String catalogId, Integer threads,
            Consumer<WebCrawlerExecutionContext> onReady) throws Exception {
        int version = catalogStore.incrementIndexVersion(catalogId);
        log.info("Rebuilding catalog {} at version {}", catalogId, version);
        return run(catalogId, CrawlTask.ACTION_CRAWL, null, false, threads, onReady);
    }

    /**
     * Opens this node's half of a crawl that some other node started.
     *
     * <p>
     * The same run in every respect but three, and all three are things exactly one node may do:
     * seed the entry point, mark the catalog as running, and publish the version when it is over.
     * Everything else -- the components, the output channels, the worker loop -- is identical,
     * because a node that joined is not a lesser participant; it is another one.
     */
    public CrawlerEngine.Result join(String catalogId, String action, boolean refresh)
            throws Exception {
        return run(catalogId, action, null, refresh, null, null, false);
    }

    private CrawlerEngine.Result run(String catalogId, String action, String from, boolean refresh,
            Integer threads, Consumer<WebCrawlerExecutionContext> onReady) throws Exception {
        return run(catalogId, action, from, refresh, threads, onReady, true);
    }

    private CrawlerEngine.Result run(String catalogId, String action, String from, boolean refresh,
            Integer threads, Consumer<WebCrawlerExecutionContext> onReady, boolean initiator)
            throws Exception {
        CatalogDetails catalogDetails = catalogDetailsService.loadCatalogDetails(catalogId);

        // One crawl at a time, as in 1.x, and now across the cluster as well as in this process:
        // two would divide the bandwidth rather than double it. The message says which crawl is
        // in the way, because "another crawl is running" is not actionable on its own.
        if (!semaphore.acquire(catalogDetails.getId())) {
            String inTheWay = semaphore.getCatalogId();
            if (inTheWay == null) {
                inTheWay = semaphore.running().stream()
                        .map(com.github.greenfinger.core.model.Catalog::getName).findFirst()
                        .orElse("unknown");
            }
            throw new WebCrawlerException("Another crawl is already running (catalog " + inTheWay
                    + "). Wait for it, or stop it with: interrupt --catalog " + inTheWay);
        }

        WebCrawlerExecutionContext context =
                new DefaultWebCrawlerExecutionContext(catalogDetails, componentFactory,
                        webCrawlerProperties, initiator);

        BlobStore blobStore = null;
        EmbeddingClient embeddingClient = null;
        CompositeOutputChannel outputChannel = null;
        Thread shutdownHook = null;
        boolean completed = false;
        boolean publisher = false;
        // declared out here because the announcement at the end of the run needs it, and by then
        // the block that built it has been left
        CrawlCoordinator coordinator = null;
        try {
            context.afterPropertiesSet();
            if (initiator) {
                catalogStore.setRunningState(catalogDetails.getId(), action);
            }

            blobStore = outputFactory.getBlobStore();
            BeanLifeCycleUtils.afterPropertiesSet(blobStore);

            if (catalogDetails.hasOutput(OutputType.VECTOR)) {
                // shared across the process: a local model builds three onnx sessions off disk,
                // which is seconds, and paying that per run put it in front of every crawl
                embeddingClient = outputFactory.sharedEmbeddingClient();
            }

            FileLayout fileLayout = outputFactory.getFileLayout(catalogDetails);
            outputChannel =
                    outputFactory.getOutputChannel(catalogDetails, blobStore, embeddingClient);
            for (var channel : outputChannel.getChannels()) {
                if (channel instanceof FileOutputChannel file) {
                    file.setRunSummarySupplier(() -> summaryOf(context));
                }
            }
            outputChannel.open(catalogDetails);

            ImageFetcher imageFetcher = catalogDetails.isImageEnabled()
                    ? new ImageFetcher(webCrawlerProperties.getImage())
                    : null;

            crawlRegistry.register(catalogDetails.getId(), context);
            // Ctrl+C asks the crawl to wind down rather than killing it, so pages already fetched
            // still reach the outputs and the frontier stays consistent for a resume
            shutdownHook = new Thread(() -> {
                log.info("Stopping '{}' ...", catalogDetails.getName());
                context.getGlobalStateManager()
                        .interrupt("interrupted: the process was asked to stop");
            }, "greenfinger-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            if (onReady != null) {
                onReady.accept(context);
            }

            coordinator = coordinatorFactory.create(new CrawlRun(context, action, refresh,
                    initiator));
            CrawlerEngine engine = new CrawlerEngine(webCrawlerProperties, context, outputChannel,
                    fileLayout, imageFetcher, recordStore, refresh, coordinator);
            if (threads != null && threads > 0) {
                engine.setWorkThreads(threads);
            }
            // A node that joined starts with nothing and waits: its work arrives from whoever
            // has it. Handing it the entry point as well would crawl that page a second time.
            CrawlerEngine.Result result =
                    engine.run(initiator ? seedOf(catalogDetails, action, from) : null);
            // Read from the shared state rather than worked out here. A crawl that hit
            // maxFetchSize, ran out of time, or ran out of urls has finished on its own terms:
            // what it saved is whole and worth serving, and what it did not reach is still on a
            // frontier for the next update. A crawl cut short from outside -- Ctrl+C, the
            // interrupt command, or a node that stopped answering -- leaves the previous version
            // in place. Which of the two it was, is a fact of the crawl, not of this node.
            completed = result.isSelfTerminated();
            // asked now rather than at the start: across a cluster this is "am I the leader", and
            // the leader may not be who it was when the crawl began
            publisher = coordinator.shouldPublish();
            // written before the outputs are closed, so the blob store is still open, and on
            // every node: each one accounts for the share of the crawl it actually did
            String report = reporter.write(blobStore, fileLayout, catalogDetails, context, result,
                    action, refresh, coordinator.nodeId());
            if (report != null && log.isInfoEnabled()) {
                log.info("Run report: {}", report);
            }
            CrawlerEngine.Result reported = result.toBuilder().reportPath(report).build();
            // one row per version, describing the whole crawl rather than this node's share of
            // it, so exactly one node writes it -- the one that started the run
            if (initiator && reportRecorder != null) {
                reportRecorder.record(catalogDetails, context, reported, action, refresh,
                        coordinator.nodeId(), blobStore, fileLayout);
            }
            return reported;
        } finally {
            if (shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException ignored) {
                    // already shutting down; the hook is doing its job
                }
            }
            // Everything this run still owes is done while it still holds the permit, and the
            // signals that say it is over are the last thing to happen. Released first, the run
            // was over as far as every other caller could see while it was still publishing:
            // "delete this catalog" answered "it is being crawled right now" to somebody looking
            // at a page that said the crawl had finished, and the wait for it to end was a wait
            // for a length of time nobody could name.
            //
            // The version is only made visible once it is whole, so a rebuild never empties
            // search the way 1.x did. Published by one node: every node reaching the same
            // conclusion would publish the same version repeatedly and prune the same files at
            // the same time. Which node that is, is the coordinator's to say
            if (completed && publisher) {
                catalogStore.publishSearchVersion(catalogDetails.getId(),
                        catalogDetails.getVersion());
                versionPruner.prune(catalogDetails);
            }
            if (initiator) {
                catalogStore.setRunningState(catalogDetails.getId(),
                        WebCrawlerConstants.RUNNING_STATE_NONE);
            }
            // the embedding client is not closed here: it belongs to the process, not to this run
            BeanLifeCycleUtils.destroyQuietly(blobStore);
            // before the permit: the next crawl needs the RocksDB directories this is holding
            BeanLifeCycleUtils.destroyQuietly(context);
            semaphore.release();
            // last, because this is what the api reads to say a crawl is running. By the time it
            // says no, there is nothing left that a delete or another crawl could collide with.
            crawlRegistry.unregister(catalogDetails.getId());
            // and then, with everything done and nothing holding a lock, whoever is listening is
            // told. Said once for the whole cluster by the node that publishes, because the
            // announcement comes back to its sender and every node publishes it locally.
            if (publisher && coordinator != null) {
                announceCompletion(catalogDetails, coordinator, completed);
            }
        }
    }

    /**
     * Tells the cluster, or this process alone, that the run is over.
     *
     * <p>
     * Not part of finishing it: the version is published and the stores are closed before this is
     * reached, and a listener that throws changes none of that. It is the moment a shared flag
     * does not have, for an application that wants to act on a crawl finishing rather than poll
     * to find out that it did.
     */
    private void announceCompletion(CatalogDetails catalogDetails, CrawlCoordinator coordinator,
            boolean completed) {
        Dashboard dashboard = crawlRegistry.getDashboard(catalogDetails.getId())
                .orElse(null);
        String reason = dashboard != null ? dashboard.getCompletionReason() : null;
        boolean interrupted = dashboard != null ? dashboard.isInterrupted() : !completed;
        try {
            // false means there was no cluster to tell, so this process publishes it itself
            if (!coordinator.announceCompleted(catalogDetails.getId(), catalogDetails.getVersion(),
                    reason, interrupted) && eventPublisher != null) {
                eventPublisher.publishEvent(new WebCrawlerCompletionEvent(this,
                        catalogDetails.getId(), catalogDetails.getVersion(), reason, interrupted));
            }
        } catch (RuntimeException e) {
            log.warn("Could not announce the completion of catalog '{}': {}",
                    catalogDetails.getName(), e.getMessage(), e);
        }
    }

    /**
     * Where a run begins.
     *
     * <p>
     * For an update the frontier is consulted first -- it holds exactly what was queued when the
     * last run stopped, so nothing is lost -- and only if it is empty does the 1.x fallback apply,
     * carrying on from the most recently saved url. The url filter is untouched either way, so
     * already crawled pages are skipped and only newly appeared ones are taken.
     */
    private CrawlTask seedOf(CatalogDetails catalogDetails, String action, String from) {
        String startUrl = StringUtils.isNotBlank(catalogDetails.getStartUrl())
                ? catalogDetails.getStartUrl()
                : catalogDetails.getUrl();
        String url = startUrl;
        if (CrawlTask.ACTION_UPDATE.equals(action)) {
            if (StringUtils.isNotBlank(from)) {
                url = from;
            } else {
                url = recordStore
                        .getLatestReferencePath(catalogDetails.getId(),
                                catalogDetails.getVersion())
                        .orElse(startUrl);
            }
        }
        return CrawlTask.seed(catalogDetails.getId(), action, catalogDetails.getUrl(), url,
                catalogDetails.getCategory(), catalogDetails.getPageEncoding(),
                catalogDetails.getVersion());
    }

    private Map<String, Object> summaryOf(WebCrawlerExecutionContext context) {
        Dashboard dashboard = context.getGlobalStateManager().getDashboard();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("startTime", new Date(dashboard.getStartTime()));
        summary.put("elapsedMillis", dashboard.getElapsedTime());
        summary.put("totalUrlCount", dashboard.getTotalUrlCount());
        summary.put("handledUrlCount", dashboard.getHandledUrlCount());
        summary.put("existingUrlCount", dashboard.getExistingUrlCount());
        summary.put("filteredUrlCount", dashboard.getFilteredUrlCount());
        summary.put("invalidUrlCount", dashboard.getInvalidUrlCount());
        summary.put("duplicatedContentCount", dashboard.getDuplicatedContentCount());
        summary.put("savedResourceCount", dashboard.getSavedResourceCount());
        summary.put("savedImageCount", dashboard.getSavedImageCount());
        summary.put("completionReason", context.getCompletionReason());
        // whether the version this run produced is one the search may serve
        summary.put("interrupted", context.isInterrupted());
        try {
            summary.put("remainingUrlCount", context.getCrawlFrontier().remaining());
        } catch (Exception ignored) {
            // the frontier may already be closed; a missing count must not fail the settings write
        }
        return summary;
    }

}
