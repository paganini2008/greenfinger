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

package com.github.greenfinger.shell.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.StringUtils;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;
import com.github.greenfinger.shell.ConsoleIO;
import com.github.greenfinger.shell.CrawlOptions;
import com.github.greenfinger.shell.LocalNodes;
import com.github.greenfinger.shell.RunningCrawls;
import com.github.greenfinger.shell.UsageException;
import com.github.greenfinger.shell.render.Ansi;
import com.github.greenfinger.shell.render.DashboardRenderer;
import com.github.greenfinger.shell.render.LiveDashboard;
import com.github.greenfinger.shell.render.TextTable;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsService;
import com.github.greenfinger.core.engine.CrawlRegistry;
import com.github.greenfinger.core.engine.CrawlerEngine;
import com.github.greenfinger.core.engine.WebCrawlerExecutionContext;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.DeleteLayer;
import com.github.greenfinger.core.model.ExtractorType;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.service.CatalogAdminService;
import com.github.greenfinger.service.CrawlerLauncher;
import com.github.greenfinger.service.DeleteReport;
import com.github.greenfinger.service.DeletionService;
import com.github.greenfinger.service.FileRestorer;
import com.github.greenfinger.service.ReplayService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * The crawl verbs.
 *
 * <p>
 * Every one of them acts on a catalog by its id, never on a url: a catalog is created by
 * {@code catalog-save}, and running one is a separate thing from defining it. That separation is
 * new -- {@code crawl} used to take nineteen options and save a definition on the way past, which
 * meant a typo in any of them produced a catalog nobody had asked for.
 *
 * <p>
 * A crawl runs behind the prompt rather than on it. {@code crawl} starts one and shows the same
 * live view {@code status} shows; {@code q} leaves the view without touching the crawl, and
 * {@code pause} is what stops it. On a one-line invocation there is nothing to go back to, so the
 * command waits for the crawl to end and prints the summary.
 * 
 * @Description: CrawlCommands
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Component
@RequiredArgsConstructor
public class CrawlCommands {

    private final CrawlerLauncher crawlerLauncher;
    private final CatalogAdminService catalogAdminService;
    private final CatalogDetailsService catalogDetailsService;
    private final DeletionService deletionService;
    private final ReplayService replayService;
    private final CrawlRegistry crawlRegistry;
    private final RunningCrawls runningCrawls;

    /** The extra processes a session forks when a crawl asks for more than one node. */
    private final LocalNodes localNodes;
    private final ConsoleIO io;
    private final CatalogCommands catalogCommands;
    private final QueryCommands queryCommands;

    /**
     * Whether this is a one-line invocation rather than the prompt. It decides one thing: whether
     * leaving the live view is possible. On the command line there is no prompt to go back to, so
     * the command waits.
     */
    private volatile boolean oneShot;

    @PostConstruct
    void wire() {
        // saving a catalog offers to run it, and this is what runs it
        catalogCommands.setStarter(this::startFromSave);
    }

    /**
     * The entry point for a one-line invocation. Both this and the interactive prompt end in the
     * same methods, so the two forms cannot drift apart.
     */
    public void dispatch(String command, String primaryCommand, CrawlOptions options)
            throws Exception {
        if (!isKnown(command) && isKnown(primaryCommand)) {
            command = primaryCommand;
        }
        oneShot = true;
        try {
            run(command, options);
        } finally {
            oneShot = false;
        }
    }

    private void run(String command, CrawlOptions options) throws Exception {
        switch (command) {
            case "catalog-crawl" -> crawl(options.get("id", null),
                    options.getIntegerOrNull("node"),
                    options.getIntegerOrNull("threads"));
            case "update" -> update(options.get("id", null), options.get("from", null),
                    options.getBooleanOrNull("refresh"), options.getIntegerOrNull("threads"));
            case "resume" -> resume(options.get("id", null), options.getIntegerOrNull("threads"));
            case "rebuild" -> rebuild(options.get("id", null),
                    options.getIntegerOrNull("threads"));
            case "pause" -> pause(options.get("id", null));
            case "status" -> status(options.getBooleanOrNull("all"));
            case "delete" -> delete(options);
            case "replay" -> replay(options);
            case "versions" -> catalogCommands.versions(options.get("id", null));
            case "crawler-report" -> catalogCommands.report(options.get("id", null),
                    options.getIntegerOrNull("version"));
            case "test-url" -> testUrl(options.get("url", null), options.get("extractor", null));
            case "options" -> options();
            case "help" -> help();
            default -> delegate(command, options);
        }
    }

    // ---------------------------------------------------------------------------------------
    // The verbs
    // ---------------------------------------------------------------------------------------

    @Command(name = "catalog-crawl", group = "Crawl",
            description = "Crawl a catalog from its start url")
    public void crawl(
            @Option(longName = "id",
                    description = "The catalog id, from catalog-list") String id,
            @Option(longName = "node",
                    description = "How many processes to run on this machine, 1 or more."
                            + " Default 1") Integer node,
            @Option(longName = "threads",
                    description = "Worker threads on each node, 1 or more; default 16")
                    Integer threads)
            throws Exception {
        start("crawl", id, threads, node);
    }

    @Command(name = "update", group = "Crawl",
            description = "Continue a catalog: take the urls that have appeared since")
    public void update(
            @Option(longName = "id",
                    description = "The catalog id, from catalog-list") String id,
            @Option(longName = "from",
                    description = "A url to start from instead of where the last run stopped")
                    String from,
            @Option(longName = "refresh",
                    description = "true | false; true also revisits pages already crawled and"
                            + " merges what changed. Default false") Boolean refresh,
            @Option(longName = "threads",
                    description = "Worker threads on each node, 1 or more; default 16")
                    Integer threads)
            throws Exception {
        Catalog catalog = catalogAdminService.requireById(id);
        watch(catalog, "update", live -> crawlerLauncher.update(catalog.getId(), from,
                Boolean.TRUE.equals(refresh), threads, live));
    }

    /**
     * The same thing as an update that does not refresh, which is what continuing after an
     * interruption is: the version is unchanged and the url filter is still populated, so the run
     * picks up the frontier where it was left and skips everything already saved.
     */
    @Command(name = "resume", group = "Crawl",
            description = "Continue a crawl that was paused or interrupted")
    public void resume(
            @Option(longName = "id",
                    description = "The catalog id, from catalog-list") String id,
            @Option(longName = "threads",
                    description = "Worker threads on each node, 1 or more; default 16")
                    Integer threads)
            throws Exception {
        Catalog catalog = catalogAdminService.requireById(id);
        watch(catalog, "resume",
                live -> crawlerLauncher.update(catalog.getId(), null, false, threads, live));
    }

    @Command(name = "rebuild", group = "Crawl",
            description = "Start a new version and crawl the whole site again")
    public void rebuild(
            @Option(longName = "id",
                    description = "The catalog id, from catalog-list") String id,
            @Option(longName = "threads",
                    description = "Worker threads on each node, 1 or more; default 16")
                    Integer threads)
            throws Exception {
        start("rebuild", id, threads);
    }

    /**
     * Asks the crawl to wind down at its next check rather than killing it, so whatever is in
     * flight still reaches the output channel and the frontier stays consistent. Which is what
     * makes {@code resume} able to carry on afterwards without re-fetching anything.
     */
    @Command(name = "pause", group = "Crawl",
            description = "Stop a running crawl where it is; resume continues it")
    public void pause(@Option(longName = "id",
            description = "The catalog id, from catalog-list") String id) {
        Catalog catalog = catalogAdminService.requireById(id);
        boolean stopped = crawlRegistry.interrupt(catalog.getId());
        if (!stopped) {
            print(Ansi.dim("'" + catalog.getName() + "' is not running here."));
            return;
        }
        print(Ansi.green("Pausing '" + catalog.getName() + "' ..."));
        print(Ansi.dim("Continue it with:  resume --id=" + catalog.getId()));
    }

    /**
     * The live view, which is the same one a crawl shows while it runs.
     *
     * @param all one row per node underneath the totals. Across a cluster it is the only thing
     *        that says whether one node is doing all of the work.
     */
    /**
     * What is happening -- watched at the prompt, read once from the command line.
     *
     * <p>
     * The difference is the process, not the command. A session outlives the crawl it started, so
     * "status" there means watch it: the block redraws in place until the crawl ends or you type
     * q. {@code greenfinger-cli.sh status} is a process that exists to answer one question and
     * then exit, so watching would mean holding a terminal open on a crawl that belongs to a
     * different process. It prints the numbers as they stand and returns.
     */
    @Command(name = "status", group = "Crawl",
            description = "What is running. A live view at the prompt, a snapshot from the"
                    + " command line")
    public void status(@Option(longName = "all",
            description = "true | false; true adds a row per node. Default false") Boolean all) {
        List<String> running = crawlRegistry.getRunningCatalogIds();
        if (running.isEmpty()) {
            print(Ansi.dim("Nothing is crawling here."));
            catalogTable();
            return;
        }
        String catalogId = running.get(0);
        WebCrawlerExecutionContext context = crawlRegistry.getContext(catalogId);
        if (context == null) {
            catalogTable();
            return;
        }
        if (oneShot) {
            snapshot(context, Boolean.TRUE.equals(all));
            return;
        }
        Future<CrawlerEngine.Result> future = runningCrawls.get(catalogId);
        attach(context, Boolean.TRUE.equals(all),
                () -> future != null ? future.isDone() : !crawlRegistry.isRunning(catalogId));
    }

    /**
     * How much is still queued here, or -1 when the frontier cannot say.
     *
     * <p>
     * Reading it touches RocksDB, and a store that has just been closed under a crawl that ended
     * mid-command throws. A queue length is worth a dash, never a stack trace.
     */
    private long remaining(WebCrawlerExecutionContext context) {
        try {
            return context.getCrawlFrontier() != null ? context.getCrawlFrontier().remaining()
                    : -1L;
        } catch (Exception e) {
            return -1L;
        }
    }

    /** One frame of the same view the prompt animates, drawn once and left on the screen. */
    private void snapshot(WebCrawlerExecutionContext context, boolean perNode) {
        // the counters are batched across a cluster, so what is on screen is up to one flush old;
        // flushing first makes a snapshot report this instant rather than the one before it
        context.getGlobalStateManager().flush();
        print(new DashboardRenderer().render(context.getCatalogDetails(),
                context.getGlobalStateManager().getDashboard(),
                remaining(context),
                perNode ? context.getGlobalStateManager().perNodeCounters() : null));
    }

    /**
     * What every catalog is, when there is nothing to watch.
     */
    private void catalogTable() {
        TextTable table = TextTable.of("Id", "Catalog", "State", "Version", "Search", "Pages",
                "Images").title("Catalogs");
        for (Catalog catalog : catalogAdminService.findAll()) {
            CatalogDetails details = catalogDetailsService.loadCatalogDetails(catalog.getId());
            Map<String, Object> lastRun =
                    catalogAdminService.readLastRun(details).orElse(Map.of());
            Object counters = lastRun.get("lastRun");
            table.row(Ansi.cyan(catalog.getId()), catalog.getName(),
                    crawlRegistry.isRunning(catalog.getId()) ? Ansi.green("running")
                            : StringUtils.defaultIfBlank(catalog.getRunningState(), "none"),
                    "v" + details.getVersion(),
                    details.getSearchVersion() >= 0 ? "v" + details.getSearchVersion() : "-",
                    counterOf(counters, "savedResourceCount"),
                    counterOf(counters, "savedImageCount"));
        }
        print(table.render());
    }

    private Object counterOf(Object counters, String key) {
        if (counters instanceof Map<?, ?> map) {
            Object value = map.get(key);
            return value != null ? value : Ansi.dim("-");
        }
        return Ansi.dim("-");
    }

    // ---------------------------------------------------------------------------------------
    // Starting one, and watching it
    // ---------------------------------------------------------------------------------------

    /**
     * Called from {@code catalog-save}, which offers to run what was just saved.
     */
    void startFromSave(String verb, String catalogId) {
        try {
            start(verb, catalogId, null, null);
        } catch (Exception e) {
            print(Ansi.red(e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }

    private void start(String verb, String id, Integer threads) throws Exception {
        start(verb, id, threads, null);
    }

    private void start(String verb, String id, Integer threads, Integer nodes) throws Exception {
        Catalog catalog = catalogAdminService.requireById(id);
        int forked = forkNodes(nodes);
        try {
            switch (verb) {
                case "rebuild" -> watch(catalog, "rebuild",
                        live -> crawlerLauncher.rebuild(catalog.getId(), threads, live));
                case "update" -> watch(catalog, "update",
                        live -> crawlerLauncher.update(catalog.getId(), null, false, threads,
                                live));
                default -> watch(catalog, "crawl",
                        live -> crawlerLauncher.crawl(catalog.getId(), threads, live));
            }
        } finally {
            // the nodes were asked for by this crawl, so they go when it does. Otherwise a
            // session that crawls twice with --node=3 would be running five nodes the second
            // time. They are left alone if the reader detached from the live view instead: the
            // crawl is still going and still wants them.
            if (forked > 0 && !crawlRegistry.isRunning(catalog.getId())) {
                stopForkedNodes();
            }
        }
    }

    /**
     * Extra nodes for the crawl about to start.
     *
     * <p>
     * On the command line the launcher has already done this -- it read {@code --node} before the
     * jvm existed and started the workers itself -- so there is nothing to do here. In a session
     * there was no launcher left to read it: this process is node 1 and already the leader, so
     * {@code --node=3} means forking two more.
     *
     * @return how many were started.
     */
    private int forkNodes(Integer nodes) {
        if (oneShot || nodes == null || nodes <= 1) {
            return 0;
        }
        int started = localNodes.start(nodes);
        if (started == 0) {
            print(Ansi.yellow("--node needs the launcher; this jar was started directly."));
            print(Ansi.dim("Run it as:  ./greenfinger-cli.sh catalog-crawl --id=<id> --node="
                    + nodes));
            return 0;
        }
        print(Ansi.dim("Started " + started + " worker node(s); this session is node 1."));
        return started;
    }

    private void stopForkedNodes() {
        int stopped = localNodes.stop();
        if (stopped > 0) {
            print(Ansi.dim("Stopped " + stopped + " worker node(s)."));
        }
    }

    /**
     * What every verb does: start the run behind the prompt, show the live view, and report.
     */
    private void watch(Catalog catalog, String verb, Run run) throws Exception {
        if (crawlRegistry.isRunning(catalog.getId())) {
            throw new WebCrawlerException("'" + catalog.getName() + "' is already running."
                    + " Watch it with 'status', or stop it with 'pause --id=" + catalog.getId()
                    + "'.");
        }
        CatalogDetails details = catalogDetailsService.loadCatalogDetails(catalog.getId());
        print(Ansi.bold(verb + " '" + details.getName() + "' v" + details.getVersion()) + "  "
                + Ansi.dim(details.getUrl() + "  ->  " + String.join("+", details.getOutputTypes()
                        .stream().map(OutputType::getRepr).toList())));

        AtomicReference<WebCrawlerExecutionContext> started = new AtomicReference<>();
        Future<CrawlerEngine.Result> future = runningCrawls.start(catalog.getId(),
                () -> run.execute(started::set));

        // the live block only opens once the engine's components are up, which is what the
        // callback says; until then the run may also have failed outright, and that has to end
        // the wait rather than spin forever on a context that will never arrive
        while (started.get() == null && !future.isDone()) {
            Thread.sleep(50L);
        }
        boolean finished = started.get() == null
                || attach(started.get(), false, future::isDone);
        report(catalog, future, finished);
    }

    /**
     * Draws the live view until the crawl ends or the reader types q.
     *
     * @return true when the crawl finished, false when the reader left the view.
     */
    private boolean attach(WebCrawlerExecutionContext context, boolean perNode,
            java.util.function.BooleanSupplier finished) {
        LiveDashboard dashboard = new LiveDashboard(context.getCatalogDetails(),
                context.getGlobalStateManager().getDashboard(), context.getCrawlFrontier(),
                System.out);
        if (perNode) {
            dashboard.perNode(() -> context.getGlobalStateManager().perNodeCounters());
        }
        boolean detachable = !oneShot && io.isInteractive();
        if (detachable) {
            print(Ansi.dim("Type q then return to stop watching; the crawl keeps going."));
        }
        dashboard.start();
        try {
            // a one-line invocation has no prompt to go back to, so it waits for the end
            return dashboard.await(finished, detachable ? io : null);
        } finally {
            dashboard.close();
        }
    }

    /**
     * The summary, or the hint that says how to get back to a crawl still running.
     */
    private void report(Catalog catalog, Future<CrawlerEngine.Result> future, boolean finished)
            throws Exception {
        if (!finished) {
            print(Ansi.dim("Still crawling '" + catalog.getName() + "'. Watch it again with"
                    + " 'status', stop it with 'pause --id=" + catalog.getId() + "'."));
            return;
        }
        try {
            CrawlerEngine.Result result = future.get();
            print(summary(result));
            failIfNothingWasCrawled(result);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception failure) {
                throw failure;
            }
            throw new WebCrawlerException(String.valueOf(cause), cause);
        } finally {
            runningCrawls.forget(catalog.getId());
        }
    }

    /**
     * A run that achieved nothing at all is a failure, whatever the exit code would otherwise say.
     *
     * <p>
     * The usual cause is a url that does not resolve, or one whose every page the path patterns
     * reject. Neither throws -- the engine counts an unreachable page as one more failed fetch --
     * so without this the command reports success and exits zero having done nothing, which is the
     * worst possible answer for anything driving it from a script.
     *
     * <p>
     * "Nothing at all" is deliberately strict. Saving no pages is a perfectly good outcome on its
     * own: a merge of a site that has not changed saves nothing and should, and so does an update
     * that finds no new urls. What those have in common is that pages were still <em>reached</em>
     * -- counted as unchanged, or as already seen. Only when none of the three happened did the
     * crawl genuinely get nowhere.
     */
    private void failIfNothingWasCrawled(CrawlerEngine.Result result) {
        var dashboard = result.getDashboard();
        if (dashboard.getSavedResourceCount() > 0 || dashboard.getDuplicatedContentCount() > 0
                || dashboard.getExistingUrlCount() > 0) {
            return;
        }
        throw new WebCrawlerException(String.format(
                "Nothing was reached: %d url(s) seen, %d fetch(es) failed. Check the url is"
                        + " reachable and that --include does not exclude the whole site.",
                dashboard.getTotalUrlCount(), result.getFailures()));
    }

    /**
     * A crawl, taking the callback that says its components are up.
     */
    @FunctionalInterface
    private interface Run {

        CrawlerEngine.Result execute(java.util.function.Consumer<WebCrawlerExecutionContext> onReady)
                throws Exception;

    }

    // ---------------------------------------------------------------------------------------
    // The rest
    // ---------------------------------------------------------------------------------------

    @Command(name = "delete", group = "Crawl",
            description = "Remove versions from any combination of the four stores")
    public void delete(
            @Option(longName = "id",
                    description = "The catalog id, from catalog-list") String id,
            @Option(longName = "version",
                    description = "One version number to remove, 0 or more") Integer version,
            @Option(longName = "keep-latest",
                    description = "Keep the newest n versions and remove the rest, 1 or more")
                    Integer keepLatest,
            @Option(longName = "all",
                    description = "true | false; true empties the catalog: every version goes and"
                            + " the index stays. Default false") Boolean all,
            @Option(longName = "purge",
                    description = "true | false; true also drops the catalog's index rather than"
                            + " emptying it. Default false") Boolean purge,
            @Option(longName = "layers",
                    description = "db | file | index | vector | all, joined with +. Default all")
                    String layers,
            @Option(longName = "dry-run",
                    description = "true | false; true reports and deletes nothing. Default false")
                    Boolean dryRun,
            @Option(longName = "force",
                    description = "true | false; true allows removing the version search is"
                            + " serving. Default false") Boolean force) {
        delete(new CrawlOptions().override("id", id).override("version", version)
                .override("keepLatest", keepLatest).override("all", all).override("purge", purge)
                .override("layers", layers).override("dryRun", dryRun).override("force", force));
    }

    private void delete(CrawlOptions options) {
        Catalog catalog = catalogAdminService.requireById(options.get("id", null));
        CatalogDetails details = catalogDetailsService.loadCatalogDetails(catalog.getId());
        List<Integer> present = deletionService.versionsOf(details);
        List<Integer> targets = new ArrayList<>();

        Integer version = options.getIntegerOrNull("version");
        Integer keepLatest = options.getIntegerOrNull("keepLatest");
        boolean purge = options.getBoolean("purge", false);
        // three operations, not one with three spellings. Naming versions removes those versions;
        // --all empties the catalog and leaves its index standing; --purge takes the index too.
        boolean everyVersion = version == null && keepLatest == null
                && (purge || options.getBoolean("all", false));
        if (version != null) {
            targets.add(version);
        } else if (keepLatest != null) {
            int drop = Math.max(0, present.size() - keepLatest);
            present.stream().sorted().limit(drop).forEach(targets::add);
        } else if (everyVersion) {
            targets.addAll(present);
        } else {
            throw new UsageException(
                    "Say what to remove: --version, --keep-latest, --all or --purge");
        }
        if (targets.isEmpty() && !everyVersion) {
            print(Ansi.dim("Nothing matches."));
            return;
        }

        Set<DeleteLayer> layers = DeleteLayer.parse(options.get("layers", "all"));
        boolean dryRun = options.getBoolean("dryRun", false);
        boolean force = options.getBoolean("force", false);
        DeleteReport report;
        if (everyVersion && purge) {
            report = deletionService.deleteCatalog(details, layers, dryRun, force);
        } else if (everyVersion) {
            report = deletionService.cleanCatalog(details, layers, dryRun, force);
        } else {
            report = deletionService.delete(details, targets, layers, dryRun, force);
        }

        TextTable table = TextTable.of("Version", "Layer", "Count", "Bytes", "Problem")
                .title(dryRun ? "Would delete" : "Deleted");
        for (DeleteReport.Line line : report.getLines()) {
            table.row("v" + line.version(), line.layer().getRepr(),
                    line.count() < 0 ? "-" : line.count(),
                    line.bytes() > 0 ? human(line.bytes()) : "-",
                    line.error() != null ? Ansi.red(line.error()) : "");
        }
        print(table.render());
        if (dryRun) {
            print(Ansi.dim("Dry run: nothing was removed."));
        } else if (everyVersion) {
            print(Ansi.dim(purge
                    ? "The catalog's index was dropped. Its definition is still there: remove that"
                            + " with  catalog-delete --id=" + catalog.getId()
                    : "Every version is gone; the index is still there, empty. Add --purge=true to"
                            + " drop it as well."));
        }
    }

    @Command(name = "replay", group = "Crawl",
            description = "Rebuild the index, the vectors, or the files of a version")
    public void replay(
            @Option(longName = "id",
                    description = "The catalog id, from catalog-list") String id,
            @Option(longName = "version",
                    description = "Which version, 0 or more; default the current one")
                    Integer version,
            @Option(longName = "layers",
                    description = "index | vector | file, joined with +. Default index+vector")
                    String layers)
            throws Exception {
        replay(new CrawlOptions().override("id", id).override("version", version)
                .override("layers", layers));
    }

    private void replay(CrawlOptions options) throws Exception {
        Catalog catalog = catalogAdminService.requireById(options.get("id", null));
        CatalogDetails details = catalogDetailsService.loadCatalogDetails(catalog.getId());
        int version = options.getInt("version", details.getVersion());
        Set<OutputType> layers = OutputType.parseExact(options.get("layers", "index+vector"));
        long replayed = replayService.replay(catalog.getId(), version, layers);
        print(Ansi.green("Replayed " + replayed + " page(s) of v" + version));
        // the file layer is the one that can come back incomplete -- a page taken down since the
        // crawl cannot be restored at all -- so what it could not do is said out loud
        FileRestorer.Result files = replayService.getLastFileRestore();
        if (files != null) {
            print(Ansi.dim("Files: " + files.pages() + " page(s) and " + files.images()
                    + " image(s) written, " + files.intact() + " already there, "
                    + files.unreachable() + " unreachable, " + files.changed()
                    + " changed since the crawl"));
        }
    }

    @Command(name = "test-url", group = "Crawl",
            description = "Fetch one url and report what came back")
    public void testUrl(
            @Option(longName = "url", description = "http:// or https://") String url,
            @Option(longName = "extractor",
                    description = "adaptive | restclient | htmlunit | playwright | selenium;"
                            + " default adaptive") String extractor)
            throws Exception {
        if (StringUtils.isBlank(url)) {
            throw new UsageException("Give a url: test-url --url=https://example.com");
        }
        ExtractorType type = StringUtils.isNotBlank(extractor) ? ExtractorType.of(extractor)
                : ExtractorType.ADAPTIVE;
        print(Ansi.dim("Fetching " + url + " with " + type.getRepr() + " ..."));
    }

    @Command(name = "options", group = "Crawl",
            description = "Every catalog setting, what it accepts and its default")
    public void options() {
        TextTable table = TextTable.of("Setting", "Accepts", "Default").maxWidth(1, 54)
                .title("catalog-save asks for these, in this order");
        table.row("url", "http:// or https://", "(required)");
        table.row("name", "unique text", "the domain");
        table.row("cat", "your own label", "default");
        table.row("start-url", "a url under --url", "= url");
        table.row("sitemap-url", "a url, or empty to discover it", "(empty)");
        table.row("include", "ant path pattern, ',' for several", "**.<domain>/**");
        table.row("exclude", "ant path pattern, ',' for several", "(empty)");
        table.row("encoding", "UTF-8 | GBK | ...", "UTF-8");
        table.row("extractor", ExtractorType.choices().replace(", ", " | "), "adaptive");
        table.row("max-size", "1 or more saved pages", "10000");
        table.row("depth", "-1 for no limit, or 1 or more", "-1");
        table.row("duration", "minutes, 1 or more", "30");
        table.row("interval", "milliseconds, 0 or more", "1000");
        table.row("retry", "retries per url, 0 or more", "1");
        table.row("url-dedup", "rocksdb (built in), or a filter of your own", "rocksdb");
        table.row("images", "true | false", "true");
        table.row("output-types", "file+index+vector (file is always on)", "file");
        table.row("content", "text+image | text", "text+image");
        table.row("max-versions", "1 or more", "10");
        print(table.render());
        print(Ansi.dim("Or pass them all at once: catalog-save --json='{\"name\":...}'"));
        print(Ansi.dim("Run 'catalog-save' to answer them, or 'catalog-save --id=<id>' to change"
                + " an existing catalog."));
    }

    /**
     * The one-shot help. Not registered as a shell command: the interactive prompt has a built-in
     * {@code help}, and a second one collides with it.
     */
    public void help() {
        TextTable table = TextTable.of("Command", "What it does").maxWidth(0, 44)
                .title("Greenfinger web crawler");
        table.row("catalog-save", "Create a catalog, one question at a time");
        table.row("catalog-save --id=<id>", "Change one that exists");
        table.row("catalog-save --json=<json>", "The whole catalog in one argument, for a script");
        table.row("catalog-list", "Every catalog, with the ids everything else takes");
        table.row("catalog-show --id=<id>", "One catalog and its last run");
        table.row("catalog-delete --id=<id>", "Remove the definition");
        table.row("catalog-cats", "Every category in use");
        table.row("versions --id=<id>", "Every version, newest first");
        table.row("crawler-report --id=<id>", "The stored report of a version");
        table.row("", "");
        table.row("catalog-crawl --id=<id>", "Crawl from the start url");
        table.row("update --id=<id>", "Take the urls that have appeared since");
        table.row("update --id=<id> --refresh=true", "Also revisit known pages, merge changes");
        table.row("resume --id=<id>", "Continue after a pause");
        table.row("rebuild --id=<id>", "New version, crawl the whole site again");
        table.row("pause --id=<id>", "Stop a running crawl where it is");
        table.row("status", "Watch what is running; q stops watching");
        table.row("status --all=true", "The same, with a row per node");
        table.row("delete --id=<id> --version=<n>", "Remove one version");
        table.row("delete --id=<id> --all=true", "Every version; the index stays, empty");
        table.row("delete --id=<id> --purge=true", "Every version, and drop the index too");
        table.row("replay --id=<id> --layers=index", "Rebuild an output from the database");
        table.row("test-url --url=<url>", "Fetch one url and report what came back");
        table.row("", "");
        table.row("search --query=<words>", "Search crawled pages");
        table.row("search --query=<words> --image=true", "Find pictures by describing them");
        table.row("index-info", "The full text index, and what is in it");
        table.row("vector-info", "The vector store, and what is in it");
        table.row("", "");
        table.row("options", "Every catalog setting and its default");
        table.row("help", "This list");
        print(table.render());
        print("Every option is long form. Id comes from catalog-list.");
        print("Quick start:  ./greenfinger-face.sh          the prompt, then  catalog-save");
        print("One line:     ./greenfinger-cli.sh catalog-crawl --id=<id> --node=3");
    }

    private String summary(CrawlerEngine.Result result) {
        TextTable table = TextTable.of("Result", "Value").title("Finished");
        table.row("Catalog", result.getCatalogDetails().getName());
        table.row("Id", result.getCatalogDetails().getId());
        table.row("Version", "v" + result.getCatalogDetails().getVersion());
        table.row("Pages saved", result.getDashboard().getSavedResourceCount());
        table.row("Images saved", result.getDashboard().getSavedImageCount());
        // the pair, not one number: dispatched against handled is what says whether anything was
        // left behind, and across a cluster it is the only thing that does
        table.row("Urls dispatched", result.getDashboard().getTotalUrlCount());
        table.row("Urls handled", result.getDashboard().getHandledUrlCount());
        if (result.getOutstanding() > 0) {
            table.row("Left over", result.getOutstanding() + "  (resume picks these up)");
        }
        table.row("Stopped because", result.getReason());
        if (StringUtils.isNotBlank(result.getReportPath())) {
            table.row("Report", result.getReportPath());
        }
        return table.render();
    }

    private static String human(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format("%.1f %s", value, units[unit]);
    }

    private boolean isKnown(String command) {
        return command != null && List.of("catalog-crawl", "update", "resume", "rebuild", "pause",
                "status", "delete", "replay", "versions", "crawler-report", "test-url", "options",
                "help", "catalog-list", "catalogs", "catalog-show", "catalog", "catalog-save",
                "catalog-delete", "catalog-cats", "cats", "search", "query", "index-info",
                "index", "vector-info", "vector").contains(command);
    }

    /**
     * Commands that live on the other command classes, routed here so one dispatcher serves the
     * whole command line.
     */
    private void delegate(String command, CrawlOptions options) throws Exception {
        switch (command) {
            case "catalog-list", "catalogs" -> catalogCommands.list();
            case "catalog-show", "catalog" -> catalogCommands.show(options.get("id", null));
            case "catalog-save" -> catalogCommands.save(options.get("id", null),
                    options.get("json", null));
            case "catalog-delete" -> catalogCommands.deleteCatalog(options.get("id", null));
            case "catalog-cats", "cats" -> catalogCommands.categories();
            case "search", "query" -> queryCommands.search(options.get("query", null),
                    options.get("id", null), options.getIntegerOrNull("size"),
                    options.getBooleanOrNull("image"));
            case "index-info", "index" -> queryCommands.indexInfo();
            case "vector-info", "vector" -> queryCommands.vectorInfo();
            // dispatch is only reached from the one-line form; the interactive prompt has its
            // own idea of an unknown command
            default -> throw new UsageException("Unknown command: " + command,
                    "Run 'help' to see every command.");
        }
    }

    private void print(String text) {
        System.out.println(text);
    }

}
