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

import java.nio.charset.Charset;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.component.state.Dashboard;
import com.github.greenfinger.core.component.state.GlobalStateManager;
import com.github.greenfinger.core.output.OutputChannel;
import com.github.greenfinger.core.output.FileLayout;
import com.github.greenfinger.core.output.OutputPayload;
import com.github.greenfinger.core.component.extractor.ConditionalGet;
import com.github.greenfinger.core.component.extractor.FetchedPage;
import com.github.greenfinger.core.record.ResourceRecord;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.core.record.ResourceRecordStore.PageState;
import com.github.greenfinger.core.utils.CharsetUtils;
import com.github.greenfinger.core.utils.HashUtils;
import com.github.greenfinger.core.utils.ThreadUtils;
import com.github.greenfinger.core.utils.UrlUtils;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs one crawl to completion.
 *
 * <p>
 * A crawl is a recursive function -- handle a page, then handle every link on it -- and this class
 * is that function with a queue in the middle of the recursive call, so that the call can be made
 * by a different thread, or by a different process. Take a url from the frontier, hand it to a
 * worker, and give whatever links it yields to the {@link CrawlCoordinator}, which decides where
 * the next call happens: this node's frontier, or a peer's.
 *
 * <p>
 * That is also why the loop does not decide on its own when to stop. An empty frontier means this
 * node has nothing to do right now, not that the crawl is over -- a peer may hand it more work a
 * millisecond later. The coordinator answers that question, and for a single process it answers
 * it the plain way.
 * 
 * @Description: CrawlerEngine
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class CrawlerEngine {

    /** Read in batches: a merge of a large site has every url of it to queue. */
    private static final int KNOWN_URL_PAGE_SIZE = 500;

    private static final long IDLE_POLL_INTERVAL = 50L;

    /** How many times a write is offered again to a database that said it was busy. */
    private static final int SAVE_RETRIES = 5;

    /** Milliseconds, multiplied by the attempt number. */
    private static final long SAVE_RETRY_BACKOFF = 40L;

    private final WebCrawlerProperties webCrawlerProperties;
    private final WebCrawlerExecutionContext context;
    private final OutputChannel outputChannel;
    private final FileLayout fileLayout;
    private final ContentExtractor contentExtractor;

    /**
     * Revisit pages already crawled and merge what changed, rather than only looking for urls that
     * have appeared since.
     */
    private final boolean refresh;

    /** Stands in for the persistent url filter during a refresh, which bypasses it. */
    private final java.util.Set<String> visitedThisRun =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final PageParser pageParser;
    private final ImageFetcher imageFetcher;
    private final ResourceRecordStore recordStore;
    private final CrawlCoordinator coordinator;

    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final AtomicLong failures = new AtomicLong(0);

    /**
     * This run's worker count, overriding the configured one. Zero to use the configuration.
     *
     * <p>
     * Per run rather than per catalog: how many threads to give a crawl is a property of the
     * machine it is running on and of what else that machine is doing, not of the site.
     */
    @lombok.Setter
    private int workThreads;

    public CrawlerEngine(WebCrawlerProperties webCrawlerProperties,
            WebCrawlerExecutionContext context, OutputChannel outputChannel,
            FileLayout fileLayout, ImageFetcher imageFetcher, ResourceRecordStore recordStore) {
        this(webCrawlerProperties, context, outputChannel, fileLayout, imageFetcher, recordStore,
                false, null);
    }

    public CrawlerEngine(WebCrawlerProperties webCrawlerProperties,
            WebCrawlerExecutionContext context, OutputChannel outputChannel,
            FileLayout fileLayout, ImageFetcher imageFetcher, ResourceRecordStore recordStore,
            boolean refresh) {
        this(webCrawlerProperties, context, outputChannel, fileLayout, imageFetcher, recordStore,
                refresh, null);
    }

    public CrawlerEngine(WebCrawlerProperties webCrawlerProperties,
            WebCrawlerExecutionContext context, OutputChannel outputChannel,
            FileLayout fileLayout, ImageFetcher imageFetcher, ResourceRecordStore recordStore,
            boolean refresh, CrawlCoordinator coordinator) {
        this.refresh = refresh;
        this.coordinator = coordinator != null ? coordinator
                : new LocalCrawlCoordinator(context.getCrawlFrontier(),
                        context.getGlobalStateManager());
        this.webCrawlerProperties = webCrawlerProperties;
        this.context = context;
        this.outputChannel = outputChannel;
        this.fileLayout = fileLayout;
        WebCrawlerProperties.Content content = webCrawlerProperties.getContent();
        this.contentExtractor = new ContentExtractor(content.isExtractArticle(),
                content.getMinBlockLength(), content.getMinContentLength());
        this.imageFetcher = imageFetcher;
        this.recordStore = recordStore;
        this.pageParser = new PageParser(webCrawlerProperties.getImage());
    }

    /**
     * Seeds the frontier when it is empty and crawls until the coordinator says it is over.
     *
     * <p>
     * A frontier that already holds urls is a crawl that was interrupted, so it is resumed rather
     * than restarted and the seed is not added again.
     *
     * @param seed where to start, or null for a node joining a crawl that is already under way --
     *        it has no entry point of its own and works on whatever it is sent.
     */
    public Result run(CrawlTask seed) throws Exception {
        CatalogDetails catalogDetails = context.getCatalogDetails();
        CrawlFrontier frontier = context.getCrawlFrontier();
        GlobalStateManager stateManager = context.getGlobalStateManager();

        long recovered = frontier.recoveredCount();
        if (seed == null) {
            log.info("Joining the crawl of catalog '{}'", catalogDetails.getName());
        } else if (recovered > 0) {
            log.info("Resuming catalog '{}' with {} url(s) left from the previous run.",
                    catalogDetails.getName(), recovered);
        } else {
            // The seed skips the dedup gate rather than the dispatch. On an incremental update
            // its url has been seen before, and refusing to revisit the entry point would mean
            // never discovering what has been added to it since -- but it is still one url that
            // somebody has to fetch, so it is counted and routed like any other.
            context.getExistingUrlPathFilter().mightExist(seed.getUrl());
            coordinator.dispatch(seed);
            log.info("Starting catalog '{}' from {}", catalogDetails.getName(), seed.getUrl());
            seedFromSitemap(seed, catalogDetails);
            seedFromLastCrawl(seed, catalogDetails);
        }

        outputChannel.open(catalogDetails);
        AtomicInteger threadSequence = new AtomicInteger(0);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("greenfinger-worker-" + threadSequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        int workThreads = this.workThreads > 0 ? this.workThreads
                : Math.max(1, webCrawlerProperties.getWorkThreads());
        ExecutorService workers = Executors.newFixedThreadPool(workThreads, threadFactory);
        // bounds how far the dispatcher may run ahead of the workers
        Semaphore permits = new Semaphore(workThreads * 2);

        try {
            while (true) {
                if (context.checkCompletion()) {
                    break;
                }
                CrawlTask task = frontier.poll();
                if (task == null) {
                    // An empty frontier here is not the end of anything. This node may simply have
                    // handed its share to a peer, and even alone it means only that no url is
                    // queued at this instant. Whether the crawl is over is decided against the
                    // shared counters, by the checkers and the watchdog, so this waits for them.
                    ThreadUtils.sleep(IDLE_POLL_INTERVAL);
                    continue;
                }
                permits.acquire();
                inFlight.incrementAndGet();
                workers.submit(() -> {
                    boolean handled = false;
                    try {
                        handled = handle(task);
                    } catch (Exception e) {
                        failures.incrementAndGet();
                        if (log.isErrorEnabled()) {
                            log.error("Failed to handle '{}': {}", task.getUrl(), e.getMessage(),
                                    e);
                        }
                    } finally {
                        // a task abandoned because a limit fired stays in the frontier, so a
                        // later resume picks it up instead of losing the page for good
                        if (handled) {
                            completeQuietly(frontier, task);
                            coordinator.afterHandled(task);
                        }
                        inFlight.decrementAndGet();
                        permits.release();
                    }
                });
            }
            // let whatever is already running finish before the output channel is closed
            while (inFlight.get() > 0) {
                ThreadUtils.sleep(IDLE_POLL_INTERVAL);
            }
        } finally {
            ThreadUtils.gracefulShutdown(workers, 60000L);
            coordinator.close();
            outputChannel.flush();
            outputChannel.close();
            // A safety net, not the decision. Reaching here with the crawl still marked running
            // means the loop left for a reason nobody recorded -- an exception on the way out --
            // so the run is ended and marked an intervention, which is what it was. When a
            // checker or the watchdog got here first this writes nothing: the reason is kept by
            // whoever wrote it.
            if (!stateManager.isCompleted()) {
                stateManager.interrupt("the run ended without reaching a limit");
            }
            // before the result is read: a batched counter is up to one flush interval behind,
            // and the report is rendered the instant this returns
            stateManager.flush();
        }

        Dashboard dashboard = stateManager.getDashboard();
        // Whoever ended the crawl wrote why, in the shared state, so every node reports the same
        // sentence rather than each one guessing from what it happened to see.
        String reason = StringUtils.defaultIfBlank(context.getCompletionReason(),
                "the run ended");
        long outstanding = Math.max(0L, dashboard.getTotalUrlCount()
                - dashboard.getHandledUrlCount());
        log.info("Catalog '{}' finished: {} ({} url(s) dispatched, {} handled, {} outstanding)",
                catalogDetails.getName(), reason, dashboard.getTotalUrlCount(),
                dashboard.getHandledUrlCount(), outstanding);
        return Result.builder().catalogDetails(catalogDetails).dashboard(dashboard).reason(reason)
                .remaining(frontier.remaining()).outstanding(outstanding)
                .selfTerminated(!context.isInterrupted())
                .failures(failures.get()).build();
    }

    private void completeQuietly(CrawlFrontier frontier, CrawlTask task) {
        try {
            frontier.complete(task);
        } catch (Exception e) {
            if (log.isWarnEnabled()) {
                log.warn("Could not remove '{}' from the frontier: {}", task.getUrl(),
                        e.getMessage());
            }
        }
    }

    /**
     * @return true when the task reached a conclusion and may leave the frontier; false when a
     *         limit cut it short, in which case it is left to be retried.
     */
    private boolean handle(CrawlTask task) throws Exception {
        CatalogDetails catalogDetails = context.getCatalogDetails();
        GlobalStateManager stateManager = context.getGlobalStateManager();
        if (context.isCompleted()) {
            return false;
        }

        Charset charset = CharsetUtils.toCharset(task.getPageEncoding());
        // Only a merge has anything to ask with. Read once and used twice: as the conditional
        // request, and -- if the site sends the page anyway -- as the fingerprint that decides
        // whether it actually changed.
        Optional<PageState> lastCrawl = refresh ? findPageState(task) : Optional.empty();

        FetchedPage fetched;
        try {
            fetched = context.getExtractor().fetch(catalogDetails, task.getReferUrl(),
                    task.getUrl(), charset, task, conditionsOf(lastCrawl));
        } catch (Exception e) {
            stateManager.incrementCount(task.getTimestamp(), CountingType.INVALID_URL_COUNT);
            fetched = FetchedPage.of(context.getExtractor().defaultHtml(catalogDetails,
                    task.getReferUrl(), task.getUrl(), charset, task, e));
        }
        if (fetched.notModified()) {
            // the site answered the question without sending the page: nothing was downloaded,
            // nothing parsed, and the outcome is the same "unchanged" a hash comparison reaches
            stateManager.incrementCount(task.getTimestamp(),
                    CountingType.DUPLICATED_CONTENT_COUNT);
            if (log.isDebugEnabled()) {
                log.debug("Not modified since the last crawl: {}", task.getUrl());
            }
            return true;
        }
        String html = fetched.html();
        if (StringUtils.isBlank(html)) {
            return true;
        }

        Document document;
        try {
            document = pageParser.parse(html, task.getUrl());
        } catch (Exception e) {
            stateManager.incrementCount(task.getTimestamp(), CountingType.INVALID_URL_COUNT);
            return true;
        }

        // the article rather than the whole body: navigation and footers in the index are the
        // biggest single source of noise in both search and the embeddings
        String text = contentExtractor.extract(document);
        // links are followed even from a page with no text of its own, since index pages have none
        enqueueLinks(task, document);

        if (StringUtils.isBlank(text)) {
            return true;
        }

        // A refresh revisits pages it has already crawled, so the question is not "have I seen
        // this content anywhere" but "has this particular page changed". Unchanged is the common
        // answer and costs nothing beyond the fetch: no row, no file, no document, no vector.
        if (refresh && isUnchanged(lastCrawl, text)) {
            stateManager.incrementCount(task.getTimestamp(),
                    CountingType.DUPLICATED_CONTENT_COUNT);
            if (log.isDebugEnabled()) {
                log.debug("Unchanged since the last crawl: {}", task.getUrl());
            }
            return true;
        }
        if (!refresh && context.getContentDedupFilter().isDuplicate(text)) {
            stateManager.incrementCount(task.getTimestamp(),
                    CountingType.DUPLICATED_CONTENT_COUNT);
            if (log.isDebugEnabled()) {
                log.debug("Dropped '{}': content already seen under another url", task.getUrl());
            }
            return true;
        }

        CrawledPage page = new CrawledPage();
        page.setCatalogId(catalogDetails.getId());
        page.setCatalogName(catalogDetails.getName());
        page.setCat(task.getCat());
        page.setVersion(task.getVersion());
        page.setUrl(task.getUrl());
        page.setReferer(task.getReferer());
        page.setDepth(task.getDepth());
        page.setTitle(document.title());
        page.setHtml(document.html());
        page.setText(text);
        page.setContentHash(context.getContentDedupFilter().fingerprint(text));
        page.setFetchedAt(new Date());
        // stored so the next merge can offer them back; null from an engine that cannot report any
        page.setEtag(fetched.etag());
        page.setLastModified(fetched.lastModified());
        page.setImages(pageParser.extractImages(document));
        // recorded on the page, not just followed: the number of outgoing links is what separates
        // a listing from a detail page when search ranks the two
        page.setLinks(pageParser.extractLinks(document));
        page.setLinkTextLength(pageParser.linkTextLength(document));

        if (catalogDetails.isImageEnabled() && imageFetcher != null) {
            imageFetcher.fetchAll(page);
            if (!page.getStoredImages().isEmpty()) {
                stateManager.incrementCount(task.getTimestamp(), CountingType.SAVED_IMAGE_COUNT,
                        page.getStoredImages().size());
            }
        }

        // Re-check right before the write. The dispatcher runs ahead of the workers, so a limit
        // that fires while this page was in flight would otherwise be overshot by the whole
        // in-flight batch rather than by at most one page per worker. Reporting the task as
        // unhandled leaves it in the frontier for a resume to finish.
        if (context.checkCompletion()) {
            return false;
        }

        // The database first, and only then everything else. It holds the unique constraint, so
        // nothing reaches the files, the index or the vector store that is not already recorded;
        // and because it hands back the ids, the file paths are settled before a byte is written.
        ResourceRecord record = save(catalogDetails, page, fileLayout, task);
        if (record == null) {
            return true;
        }
        outputChannel.write(new OutputPayload(catalogDetails, record, page));
        stateManager.incrementCount(task.getTimestamp(), CountingType.SAVED_RESOURCE_COUNT);
        if (catalogDetails.hasOutput(com.github.greenfinger.core.model.OutputType.INDEX)) {
            // counted here rather than inside the channel, which has no state manager. Nothing
            // incremented this until 2026-09-02, so the Monitor page reported "0 indexed" through
            // every crawl that indexed perfectly well
            stateManager.incrementCount(task.getTimestamp(), CountingType.INDEXED_RESOURCE_COUNT);
        }

        if (log.isInfoEnabled()) {
            log.info("Saved [{}] {} ({} image(s))", page.getTitle(), page.getUrl(),
                    page.getStoredImages().size());
        }
        return true;
    }

    /**
     * Adds a url to the frontier unless it has been seen before.
     *
     * <p>
     * Deduplication happens here, on the way in, rather than on the way out. A task taken from the
     * frontier is then known to be unique, so one that does not finish can simply be left there and
     * retried -- whereas checking on the way out would have already recorded the url, and a retry
     * would find it "seen" and skip it.
     */
    private void enqueue(CrawlTask task) throws Exception {
        // On a refresh the persistent filter is deliberately bypassed -- its whole purpose is to
        // stop a page being fetched twice, and a refresh exists to fetch it again. A per-run set
        // takes over, so each url is still visited exactly once within this run.
        if (refresh) {
            if (!visitedThisRun.add(UrlUtils.normalize(task.getUrl()))) {
                context.getGlobalStateManager().incrementCount(task.getTimestamp(),
                        CountingType.EXISTING_URL_COUNT);
                return;
            }
            coordinator.dispatch(task);
            return;
        }
        if (context.getExistingUrlPathFilter().mightExist(task.getUrl())) {
            context.getGlobalStateManager().incrementCount(task.getTimestamp(),
                    CountingType.EXISTING_URL_COUNT);
            return;
        }
        coordinator.dispatch(task);
    }

    /**
     * Whether this page says the same thing it said last time.
     *
     * <p>
     * Compared against the fingerprint stored for this url, not against the global content filter:
     * that filter answers "have I seen these words anywhere", which on a refresh is true of every
     * page and would discard the whole site.
     */
    private boolean isUnchanged(Optional<PageState> lastCrawl, String text) {
        String fingerprint = context.getContentDedupFilter().fingerprint(text);
        return fingerprint != null
                && lastCrawl.map(PageState::contentHash).map(fingerprint::equals).orElse(false);
    }

    private Optional<PageState> findPageState(CrawlTask task) {
        return recordStore.findPageState(task.getCatalogId(), task.getVersion(),
                HashUtils.sha256(task.getUrl()));
    }

    /**
     * Skipped when the crawl is told to ignore what it was told last time. A site that changes a
     * page without changing its ETag would otherwise stay invisible to every future merge.
     */
    private ConditionalGet conditionsOf(Optional<PageState> lastCrawl) {
        if (!webCrawlerProperties.isConditionalGet()) {
            return ConditionalGet.NONE;
        }
        return lastCrawl.map(state -> ConditionalGet.of(state.etag(), state.lastModified()))
                .orElse(ConditionalGet.NONE);
    }

    /**
     * Adds whatever the site publishes about itself, before the crawl starts guessing.
     *
     * <p>
     * These are candidates, not exceptions: each one goes through the same acceptors and the same
     * deduplication as a link found on a page, so the domain boundary and the path patterns hold.
     * A site with no sitemap costs one request that returns 404.
     */
    /**
     * The database write, and the two ways another writer can interfere with it.
     *
     * <p>
     * A duplicate is not a failure. Delivery is at-least-once, so the same url can reach two
     * workers, both find no row and both insert; the unique constraint is exactly what settles
     * that, and the one that loses has nothing left to do because the page the winner wrote is the
     * page it was about to write. It is counted as seen before rather than logged with a stack
     * trace, which reads like data loss and is a duplicate being refused correctly.
     *
     * <p>
     * A busy database is not a failure either, only a "not yet". SQLite locks the whole file to
     * write, so two workers finishing within the same millisecond means one of them is told the
     * database is locked -- and Spring's own name for that class of exception is transient, which
     * says what to do about it. Letting it through loses the page for good: the url is reported
     * unhandled, the counters never meet, and at the end the run is declared stalled and publishes
     * nothing. Measured on the twelve page regression site, four threads on SQLite lost two pages
     * out of six without this.
     *
     * @return the record, or null when this url was already written by somebody else
     */
    private ResourceRecord save(CatalogDetails catalogDetails, CrawledPage page, FileLayout layout,
            CrawlTask task) throws Exception {
        GlobalStateManager stateManager = context.getGlobalStateManager();
        for (int attempt = 1;; attempt++) {
            try {
                return recordStore.save(catalogDetails, page, layout);
            } catch (DataIntegrityViolationException e) {
                stateManager.incrementCount(task.getTimestamp(), CountingType.EXISTING_URL_COUNT);
                if (log.isDebugEnabled()) {
                    log.debug("'{}' was already written by another worker", page.getUrl());
                }
                return null;
            } catch (TransientDataAccessException e) {
                if (attempt > SAVE_RETRIES) {
                    throw e;
                }
                // backing off a little further each time, because the writer being waited for is
                // itself a worker that will be finished shortly
                ThreadUtils.sleep(SAVE_RETRY_BACKOFF * attempt);
                if (log.isDebugEnabled()) {
                    log.debug("The database was busy writing '{}'; retry {} of {}", page.getUrl(),
                            attempt, SAVE_RETRIES);
                }
            }
        }
    }

    private void seedFromSitemap(CrawlTask seed, CatalogDetails catalogDetails) {
        if (!webCrawlerProperties.getSitemap().isEnabled()) {
            return;
        }
        int accepted = 0;
        for (String url : new SitemapSeeder(webCrawlerProperties.getSitemap())
                .discover(catalogDetails.getUrl(), catalogDetails.getSitemapUrl())) {
            if (!context.isUrlAcceptable(seed.getReferUrl(), url, seed)) {
                continue;
            }
            try {
                enqueue(seed.child(url));
                accepted++;
            } catch (Exception e) {
                log.debug("Could not queue sitemap url '{}': {}", url, e.getMessage());
            }
        }
        if (accepted > 0) {
            log.info("Queued {} url(s) from the sitemap of '{}'", accepted,
                    catalogDetails.getName());
        }
    }

    /**
     * Queues every page the last crawl saved, so a merge revisits what it knows rather than
     * rediscovering it.
     *
     * <p>
     * A merge used to reach its pages by following links from the entry point, which works only
     * while every page is still linked from somewhere. Two things break that. A page dropped from
     * the site's navigation would silently stop being merged, though it is still there and still
     * indexed. And a conditional request answered with 304 carries no body, so there are no links
     * on it to follow -- the first unchanged page would end the traversal.
     *
     * <p>
     * Only for a refresh. A plain update is looking for what has appeared since, and the pages it
     * already has are precisely the ones it is entitled to skip.
     */
    private void seedFromLastCrawl(CrawlTask seed, CatalogDetails catalogDetails) {
        if (!refresh) {
            return;
        }
        int queued = 0;
        for (int offset = 0;; offset += KNOWN_URL_PAGE_SIZE) {
            List<ResourceRecord> batch = recordStore.load(catalogDetails.getId(),
                    catalogDetails.getVersion(), offset, KNOWN_URL_PAGE_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            for (ResourceRecord record : batch) {
                String url = record.resource().getUrl();
                if (url == null || url.equals(seed.getUrl())) {
                    continue;
                }
                try {
                    enqueue(seed.child(url));
                    queued++;
                } catch (Exception e) {
                    log.debug("Could not queue known url '{}': {}", url, e.getMessage());
                }
            }
        }
        if (queued > 0) {
            log.info("Queued {} page(s) from the last crawl of '{}' to revisit", queued,
                    catalogDetails.getName());
        }
    }

    private void enqueueLinks(CrawlTask task, Document document) {
        if (context.isCompleted()) {
            return;
        }
        GlobalStateManager stateManager = context.getGlobalStateManager();
        for (String href : pageParser.extractLinks(document)) {
            if (context.isUrlAcceptable(task.getReferUrl(), href, task)) {
                try {
                    enqueue(task.child(href));
                } catch (Exception e) {
                    if (log.isWarnEnabled()) {
                        log.warn("Could not enqueue '{}': {}", href, e.getMessage());
                    }
                }
            } else {
                stateManager.incrementCount(task.getTimestamp(), CountingType.FILTERED_URL_COUNT);
            }
        }
    }

    /**
     * 
     * @Description: Result
     * @Author: Fred Feng
     * @Date: 29/08/2026
     * @Version 2.0.0
     */
    @Getter
    @Builder(toBuilder = true)
    public static class Result {

        private final CatalogDetails catalogDetails;
        private final Dashboard dashboard;

        /** Why the crawl stopped: a limit that fired, or the frontier draining. */
        private final String reason;

        /** Urls still on <em>this node's</em> frontier, non-zero when a limit stopped it early. */
        private final long remaining;

        /**
         * Urls the crawl dispatched and nobody reported finishing, across every node.
         *
         * <p>
         * Zero is the ordinary ending. Non-zero has three causes and they are worth telling
         * apart: a limit fired and the rest were left queued; the crawl was interrupted; or a node
         * stopped answering while holding work. All three end the same way -- what was crawled is
         * whole, and what was not is still on a frontier somewhere, so {@code update} picks it up
         * without re-fetching anything already saved.
         */
        private final long outstanding;

        private final long failures;

        /**
         * Whether the crawl reached an ending of its own -- it ran out of urls, a limit fired, or
         * the cluster gave up on urls a departed node was holding -- rather than being stopped
         * from outside by Ctrl+C or the interrupt command.
         *
         * <p>
         * It is what decides whether the version is published. A crawl that ended on its own
         * terms has produced everything it was ever going to produce, so serving it is better
         * than serving the older version, even when some urls were left over; a crawl somebody
         * stopped halfway has not, and the previous version stays.
         */
        private final boolean selfTerminated;

        /** Where this run's report was written, or null when it could not be. */
        private final String reportPath;

        public boolean isFullyCrawled() {
            return remaining == 0 && outstanding == 0;
        }

    }

}
