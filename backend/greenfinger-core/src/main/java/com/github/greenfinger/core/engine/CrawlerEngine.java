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
import com.github.greenfinger.core.component.extractor.ExtractorException;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import com.github.greenfinger.core.model.OutputType;

/**
 * Runs one crawl to completion.
 *
 * <p>
 * A crawl is a recursive function -- handle a page, then handle its links -- with a queue in the
 * middle of the recursive call, so the call can be made by another thread or another process. The
 * {@link CrawlCoordinator} decides where the next one happens, and also when it is over: an empty
 * frontier means this node has nothing to do right now, not that the crawl has finished.
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
    private final Set<String> visitedThisRun =
            ConcurrentHashMap.newKeySet();
    private final PageParser pageParser;
    private final ImageFetcher imageFetcher;
    private final ResourceRecordStore recordStore;
    private final CrawlCoordinator coordinator;

    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final AtomicLong failures = new AtomicLong(0);

    /**
     * Fetches that came back as nothing since the last one that came back as a page. See
     * {@link WebCrawlerProperties#getMaxConsecutiveFailures()}.
     */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    /** Fetches attempted, and the subset of them that came back as a page. */
    private final AtomicLong fetchesAttempted = new AtomicLong(0);
    private final AtomicLong fetchesSucceeded = new AtomicLong(0);

    /**
     * This run's worker count, overriding the configured one; zero to use the configuration. Per
     * run rather than per catalog: it is a property of the machine, not of the site.
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
     * Seeds the frontier when it is empty and crawls until the coordinator says it is over. A
     * frontier that already holds urls is an interrupted crawl, so it resumes rather than restarts.
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
            // The entry point is asked the same question every link will be asked. Exempting it
            // meant a site saying "Disallow: /" got exactly one page crawled -- the one it
            // forbade most plainly -- and published.
            String refusedBy = context.rejectedBy(seed.getReferUrl(), seed.getUrl(), seed);
            if (refusedBy != null) {
                String reason = String.format(
                        "the entry point %s was refused by %s; nothing was crawled and nothing "
                                + "is published",
                        seed.getUrl(), refusedBy);
                log.warn("Catalog '{}': {}", catalogDetails.getName(), reason);
                stateManager.interrupt(reason);
                return finish(catalogDetails, stateManager, frontier);
            }
            // The seed skips the dedup gate, not the dispatch: on an update its url has been
            // seen, and refusing it would mean never finding what has been added since.
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
                    // An empty frontier is not the end of anything -- this node may have handed
                    // its share to a peer. The counters decide, so this waits for them.
                    ThreadUtils.sleep(IDLE_POLL_INTERVAL);
                    continue;
                }
                permits.acquire();
                inFlight.incrementAndGet();
                workers.submit(() -> {
                    boolean handled = false;
                    try {
                        handled = handle(task);
                    } catch (Throwable e) {
                        // Throwable, not Exception: nobody reads this future, so anything not
                        // caught is lost in silence -- including a missing engine on the
                        // classpath, which arrives as an Error.
                        failures.incrementAndGet();
                        if (log.isErrorEnabled()) {
                            log.error("Failed to handle '{}': {}", task.getUrl(), e.getMessage(),
                                    e);
                        }
                    } finally {
                        // An abandoned task stays in the frontier for a resume, and is still
                        // reported handled: the counters answer "is anything still owed in this
                        // run", and leaving it unanswered would idle the crawl until the
                        // watchdog gave up.
                        if (handled) {
                            completeQuietly(frontier, task);
                        }
                        coordinator.afterHandled(task);
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
            // A safety net, not the decision: still running here means the loop left for a
            // reason nobody recorded. Writes nothing if a checker got here first.
            if (!stateManager.isCompleted()) {
                stateManager.interrupt("the run ended without reaching a limit");
            }
            // A crawl that read nothing has nothing to publish, however tidily it ended: one
            // url behind a challenge drains the frontier with the counters agreeing, and an empty
            // version would go over a good one.
            if (fetchesAttempted.get() > 0 && fetchesSucceeded.get() == 0) {
                String reason = String.format(
                        "not one of %d fetch(es) came back with a page; nothing was read and "
                                + "nothing is published",
                        fetchesAttempted.get());
                if (log.isWarnEnabled()) {
                    log.warn("Catalog '{}': {}", catalogDetails.getName(), reason);
                }
                stateManager.overrideAsUnproductive(reason);
            }
            // before the result is read: a batched counter is up to one flush interval behind,
            // and the report is rendered the instant this returns
            stateManager.flush();
        }

        return finish(catalogDetails, stateManager, frontier);
    }

    /**
     * The same ending whichever way the run got here -- after the loop, or the entry point being
     * refused before there was one. Touches neither the workers nor the output channel, so it is
     * safe where neither was created.
     */
    private Result finish(CatalogDetails catalogDetails, GlobalStateManager stateManager,
            CrawlFrontier frontier) throws Exception {
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

    /**
     * Counts a fetch that came back as nothing, and ends the crawl once enough have in a row --
     * as an interruption, so nothing is published: calling it a completion would replace a good
     * version with an empty one.
     */
    private void noteFailedFetch(CrawlTask task, Exception e) {
        int limit = webCrawlerProperties.getMaxConsecutiveFailures();
        int inARow = consecutiveFailures.incrementAndGet();
        GlobalStateManager stateManager = context.getGlobalStateManager();
        // Published on every failure, not only at the limit. A page watching a crawl should be
        // able to say "the site has refused the last twelve" while there is still time to lower
        // the rate, rather than finding out from the reason the run was abandoned.
        stateManager.noteFetchFailure(describe(e));
        if (limit <= 0 || inARow < limit) {
            return;
        }
        if (stateManager.isCompleted()) {
            return;
        }
        String reason = String.format(
                "%d fetch(es) in a row came back with nothing (last: %s); the site is not "
                        + "serving this crawler",
                inARow, describe(e));
        if (log.isWarnEnabled()) {
            log.warn("Ending the crawl of '{}': {}", context.getCatalogDetails().getName(),
                    reason);
        }
        stateManager.interrupt(reason);
    }

    /** The http status when there was one, and the message when the request never got that far. */
    private String describe(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ExtractorException extractorException
                    && extractorException.getHttpStatus() != null) {
                return String.valueOf(extractorException.getHttpStatus());
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
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
            // Same thing one step earlier: the limit fired while this task was queued, so it is
            // dropped before it costs a fetch. Counted the same way, because from outside it is
            // the same fact -- a url that was dispatched and produced nothing.
            stateManager.incrementCount(task.getTimestamp(), CountingType.ABANDONED_URL_COUNT);
            return false;
        }

        Charset charset = CharsetUtils.toCharset(task.getPageEncoding());
        // Only a merge has anything to ask with. Read once and used twice: as the conditional
        // request, and -- if the site sends the page anyway -- as the fingerprint that decides
        // whether it actually changed.
        Optional<PageState> lastCrawl = refresh ? findPageState(task) : Optional.empty();

        FetchedPage fetched;
        fetchesAttempted.incrementAndGet();
        try {
            fetched = context.getExtractor().fetch(catalogDetails, task.getReferUrl(),
                    task.getUrl(), charset, task, conditionsOf(lastCrawl));
            fetchesSucceeded.incrementAndGet();
            consecutiveFailures.set(0);
            context.getGlobalStateManager().noteFetchSuccess();
        } catch (Exception e) {
            stateManager.incrementCount(task.getTimestamp(), CountingType.INVALID_URL_COUNT);
            noteFailedFetch(task, e);
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

        // Re-checked before the write: the dispatcher runs ahead, so a limit that fires
        // mid-flight would be overshot by the whole batch rather than by one page per worker.
        if (context.checkCompletion()) {
            stateManager.incrementCount(task.getTimestamp(), CountingType.ABANDONED_URL_COUNT);
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
        if (catalogDetails.hasOutput(OutputType.INDEX)) {
            // counted here rather than inside the channel, which has no state manager. Nothing
            // incremented this until 2026-09-02, so the Monitor page reported "0 indexed" through
            // every crawl that indexed perfectly well
            stateManager.incrementCount(task.getTimestamp(), CountingType.INDEXED_RESOURCE_COUNT);
        }
        if (catalogDetails.hasOutput(OutputType.VECTOR)) {
            // and its counterpart, for the same reason: two outputs that can each be off, behind
            // or failing on their own need two numbers, or a crawl reports the healthy one
            stateManager.incrementCount(task.getTimestamp(), CountingType.VECTORED_RESOURCE_COUNT);
        }

        if (log.isInfoEnabled()) {
            log.info("Saved [{}] {} ({} image(s))", page.getTitle(), page.getUrl(),
                    page.getStoredImages().size());
        }
        return true;
    }

    /**
     * Adds a url to the frontier unless it has been seen. Deduplicated on the way in, so a task
     * that does not finish can be left there and retried -- checking on the way out would have
     * recorded it, and the retry would skip it.
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
     * Whether this page says what it said last time. Against the fingerprint stored for this
     * url, not the global content filter -- that answers "seen these words anywhere", which on a
     * refresh is true of every page.
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
     * The database write, and the two ways another writer interferes with it.
     *
     * <p>
     * A duplicate is not a failure: delivery is at-least-once, the unique constraint settles it,
     * and the loser's page is the one the winner just wrote. A busy database is a "not yet" --
     * SQLite locks the whole file, and letting that through loses the page, which shows up at the
     * end as a stalled run that publishes nothing.
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
     * rediscovering it by following links -- which fails for a page dropped from the navigation,
     * and for a 304, which carries no body and so no links.
     *
     * <p>
     * Only for a refresh: a plain update wants what has appeared since.
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
         * Urls dispatched that nobody reported finishing. Non-zero means a limit fired, the
         * crawl was interrupted, or a node stopped answering -- and all three leave the rest on a
         * frontier, so {@code update} picks them up without re-fetching.
         */
        private final long outstanding;

        private final long failures;

        /**
         * Whether the crawl reached an ending of its own rather than being stopped from outside.
         * It decides whether the version is published: one that ended on its own terms has
         * produced all it was going to, and one somebody stopped halfway has not.
         */
        private final boolean selfTerminated;

        /** Where this run's report was written, or null when it could not be. */
        private final String reportPath;

        public boolean isFullyCrawled() {
            return remaining == 0 && outstanding == 0;
        }

    }

}
