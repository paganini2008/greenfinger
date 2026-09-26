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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import com.github.greenfinger.shell.ConsoleIO;
import com.github.greenfinger.shell.CrawlOptions;
import com.github.greenfinger.shell.RunningCrawls;
import com.github.greenfinger.shell.UsageException;
import com.github.greenfinger.shell.render.Ansi;
import com.github.greenfinger.shell.render.DashboardRenderer;
import com.github.greenfinger.shell.render.LiveDashboard;
import com.github.greenfinger.shell.render.TextTable;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.core.catalog.CatalogDetailsNotFoundException;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawlRegistry;
import com.github.greenfinger.core.engine.CrawlerEngine;
import com.github.greenfinger.core.engine.WebCrawlerExecutionContext;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.DeleteLayer;
import com.github.greenfinger.core.model.ExtractorType;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.service.CrawlerLauncher;
import com.github.greenfinger.service.DeleteReport;
import com.github.greenfinger.service.ops.CatalogSnapshot;
import com.github.greenfinger.service.ops.GreenfingerOperations;
import com.github.greenfinger.service.ops.GreenfingerOperations.DeleteAsk;
import com.github.greenfinger.service.ops.GreenfingerOperations.Live;
import com.github.greenfinger.service.ops.GreenfingerOperations.Overview;
import com.github.greenfinger.service.ops.GreenfingerOperations.ReplayAnswer;
import com.github.greenfinger.service.ops.GreenfingerOperations.ReplayAsk;
import com.github.greenfinger.service.ops.GreenfingerOperations.StartAsk;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/**
 * The crawl verbs.
 *
 * <p>
 * Each acts on a catalog by its id, never a url: defining one is {@code catalog-save}'s job, and
 * a verb that also saved a definition turned a typo into a catalog nobody asked for.
 *
 * <p>
 * A crawl runs behind the prompt: {@code q} leaves the live view without touching it, and
 * {@code pause} stops it. On one line there is nothing to go back to, so the command waits.
 * 
 * @Description: CrawlCommands
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Component
@RequiredArgsConstructor
public class CrawlCommands {

    /**
     * What this face asks of a crawler. On a crawler node it is the services in this process; at
     * the {@code greenfinger-shell} prompt it is the leader of the cluster, asked over gossip.
     */
    private final GreenfingerOperations ops;

    /**
     * The engine, when this process has one. Absent at the prompt, which runs no crawl of its own
     * -- it is a terminal on a cluster, and the crawl belongs to the cluster.
     */
    private final ObjectProvider<CrawlerLauncher> launchers;
    private final ObjectProvider<CrawlRegistry> registries;
    private final RunningCrawls runningCrawls;
    private final ConsoleIO io;
    private final CatalogCommands catalogCommands;

    /**
     * Whether this is a one-line invocation rather than the prompt. It decides one thing: whether
     * leaving the live view is possible. On the command line there is no prompt to go back to, so
     * the command waits.
     */
    private volatile boolean oneShot;

    /**
     * True when the catalog was found from a url or a name rather than given by id. Whoever typed
     * a url does not have the id yet, and every later verb takes one.
     */
    private volatile boolean addressedWithoutAnId;

    /**
     * The cluster this process is in. It goes into the lines printed as "what to do next", because
     * a command line that leaves --cluster out is refused by the launcher.
     */
    @Value("${spring.spreader.name:}")
    private String cluster;

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
        addressedWithoutAnId = false;
        try {
            run(command, options);
        } finally {
            oneShot = false;
        }
    }

    /**
     * What the one-line form runs: the crawl verbs, and nothing else.
     *
     * <p>
     * A cron entry or a deploy script wants a verb and an exit code. Everything else the shell can
     * do is looking at the installation rather than working it, and that is the prompt's job -- a
     * one-liner that did both would be a third face to keep in step. Anything else is refused by
     * name rather than ignored: a script is entitled to know its request did not happen.
     */
    private void run(String command, CrawlOptions options) throws Exception {
        switch (canonical(command)) {
            case "crawl" -> crawl(catalogOf(options), options.getIntegerOrNull("node"),
                    options.getIntegerOrNull("threads"));
            case "update" -> update(catalogOf(options), options.get("from", null),
                    options.getBooleanOrNull("refresh"), options.getIntegerOrNull("threads"));
            // merge is update with the pages it already has revisited: one verb for the thing
            // people were writing --refresh=true for, and the word they were using for it
            case "merge" -> update(catalogOf(options), options.get("from", null), true,
                    options.getIntegerOrNull("threads"));
            case "resume" -> resume(catalogOf(options), options.getIntegerOrNull("threads"));
            case "rebuild" -> rebuild(catalogOf(options), options.getIntegerOrNull("threads"));
            case "replay" -> replay(options);
            case "pause" -> pause(catalogOf(options));
            case "help" -> oneLineHelp();
            default -> throw notAVerb(command);
        }
    }

    /**
     * Which catalog the verb is about: an id, or a url.
     *
     * <p>
     * A url with no catalog behind it yet is created, with every default the installation is
     * configured with, and then crawled. That is for the one-line form only -- a script that has
     * a url and wants pages should not have to make two calls and parse an id out of the first --
     * and the prompt still defines catalogs with {@code catalog-save}, where a typo is a question
     * rather than a catalog nobody asked for.
     */
    private String catalogOf(CrawlOptions options) {
        String id = options.get("id", null);
        if (StringUtils.isNotBlank(id)) {
            // --id is an id. A name is unique and would work -- until somebody renames a catalog
            // and a year-old script either fails or finds whatever took the name. --name is for
            // that, and says so.
            CatalogSnapshot found = ops.catalog(id);
            if (!id.trim().equals(found.getId())) {
                throw new CatalogDetailsNotFoundException("No catalog has the id '" + id
                        + "'. That is the name of one: use --name=" + id + " instead.");
            }
            return found.getId();
        }
        String url = options.get("url", null);
        String name = options.get("name", null);
        if (StringUtils.isBlank(url) && StringUtils.isBlank(name)) {
            throw new UsageException("Name a catalog: --id=<id>, --name=<name>, or --url=<url>",
                    "A url creates the catalog if there is not one yet:",
                    "  ./greenfinger-cli.sh --cluster=<name> crawl --url=https://books.toscrape.com");
        }
        CatalogSnapshot catalog = ops.ensureCatalog(name, url);
        addressedWithoutAnId = true;
        print(Ansi.dim("Catalog '" + catalog.getName() + "'  id " + catalog.getId()));
        return catalog.getId();
    }

    /**
     * The one-line form's own help: seven verbs and where everything else went.
     *
     * <p>
     * Not the prompt's help table, which lists twenty commands this form no longer runs. A help
     * that answers with things that will be refused when typed is worse than no help.
     */
    private void oneLineHelp() {
        TextTable table = TextTable.of("Command", "What it does")
                .title("greenfinger-cli.sh -- the crawl verbs");
        table.row("crawl --id=<id>", "Crawl from the start url");
        table.row("crawl --url=<url>", "The same, creating the catalog if there is none");
        table.row("update --id=<id>", "Take the urls that have appeared since");
        table.row("merge --id=<id>", "Update, and revisit the pages already held");
        table.row("rebuild --id=<id>", "A new version, the whole site again");
        table.row("replay --id=<id> --layers=index", "Rebuild an output from the database");
        table.row("resume --id=<id>", "Continue after a pause");
        table.row("pause --id=<id>", "Stop a running crawl where it is");
        print(table.render());
        print(Ansi.dim("Ids come from the prompt or the page. Everything else this can do --"
                + " lists, reports, search, the state of the index -- is in"
                + "  ./greenfinger-shell.sh"));
    }

    /**
     * The one spelling of each verb. {@code crawl} is what the one-line form is documented as;
     * {@code catalog-crawl} is the prompt's name for the same command and is accepted here so a
     * line copied from one face runs on the other.
     */
    private static String canonical(String command) {
        String name = command == null ? "" : command.trim().toLowerCase();
        return switch (name) {
            case "catalog-crawl" -> "crawl";
            case "refresh" -> "merge";
            default -> name;
        };
    }

    /**
     * Said in the words of whoever typed it, and pointing at where the thing they wanted lives.
     * A command that exists in the prompt gets a different sentence from one that exists nowhere,
     * because "not here" and "not at all" are different problems with different next steps.
     */
    private UsageException notAVerb(String command) {
        boolean elsewhere = ELSEWHERE.contains(canonical(command));
        return new UsageException(
                elsewhere ? "'" + command + "' is not a crawl verb, so the one-line form does not"
                        + " run it."
                        : "Unknown command: " + command,
                elsewhere
                        ? "It lives in the prompt, beside the rest of what this can show you:"
                                + "  ./greenfinger-shell.sh"
                        : "The one-line form runs the crawl verbs: crawl, update, merge, rebuild,"
                                + " replay, resume, pause. Everything else is in"
                                + "  ./greenfinger-shell.sh");
    }

    /**
     * The commands that exist, in the prompt, and are deliberately not on the one-line form.
     * Listed rather than inferred so the message can tell somebody where their command went
     * instead of calling it unknown, which it is not.
     */
    private static final Set<String> ELSEWHERE = Set.of("status", "delete", "versions",
            "crawler-report", "test-url", "options", "catalog-list", "catalogs", "catalog-show",
            "catalog", "catalog-save", "catalog-delete", "catalog-cats", "cats", "search",
            "query", "index-info", "index", "vector-info", "vector");

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
        if (node != null && node > 1) {
            print(Ansi.dim("--node belongs to the launcher: ./greenfinger-cli.sh --cluster="
                    + clusterName() + " crawl --id=" + id + " --node=" + node));
        }
        start("crawl", id, null, threads);
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
        start(Boolean.TRUE.equals(refresh) ? "merge" : "update", id, from, threads);
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
        start("resume", id, null, threads);
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
        start("rebuild", id, null, threads);
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
        CatalogSnapshot catalog = ops.catalog(id);
        if (!ops.interrupt(catalog.getId())) {
            print(Ansi.dim("'" + catalog.getName() + "' is not running."));
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
     * Watched at the prompt, read once on the command line: a session outlives the crawl and can
     * redraw, while a one-shot process would be holding a terminal open on somebody else's crawl.
     */
    @Command(name = "status", group = "Crawl",
            description = "What is running. A live view at the prompt, a snapshot from the"
                    + " command line")
    public void status(@Option(longName = "all",
            description = "true | false; true adds a row per node. Default false") Boolean all) {
        Overview overview = ops.overview();
        if (overview.running().isEmpty()) {
            print(Ansi.dim("Nothing is crawling."));
            catalogTable(overview);
            return;
        }
        String catalogId = overview.running().get(0);
        boolean perNode = Boolean.TRUE.equals(all);
        if (oneShot) {
            snapshot(catalogId, perNode);
            return;
        }
        follow(catalogId, perNode);
    }

    /** One frame of the same view the prompt animates, drawn once and left on the screen. */
    private void snapshot(String catalogId, boolean perNode) {
        Live frame = ops.live(catalogId, perNode);
        if (frame == null || frame.dashboard() == null) {
            print(Ansi.dim("Nothing is crawling."));
            return;
        }
        print(new DashboardRenderer().render(frame.dashboard().getCatalogDetails(),
                frame.dashboard(), frame.remaining(), frame.perNode()));
    }

    /**
     * What every catalog is, when there is nothing to watch.
     */
    private void catalogTable(Overview overview) {
        TextTable table = TextTable.of("Id", "Catalog", "State", "Version", "Search", "Pages",
                "Images").title("Catalogs");
        for (CatalogSnapshot catalog : overview.catalogs()) {
            Object counters = overview.lastRuns().getOrDefault(catalog.getId(), Map.of())
                    .get("lastRun");
            table.row(Ansi.cyan(catalog.getId()), catalog.getName(),
                    overview.running().contains(catalog.getId()) ? Ansi.green("running")
                            : StringUtils.defaultIfBlank(catalog.getRunningState(), "none"),
                    "v" + catalog.getVersion(),
                    catalog.getSearchVersion() >= 0 ? "v" + catalog.getSearchVersion() : "-",
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

    /**
     * Two ways to begin, and which one is not a setting: a process with an engine runs the crawl
     * and waits for it, and a terminal asks the cluster to run it and watches.
     */
    private void start(String verb, String id, String from, Integer threads) throws Exception {
        CrawlerLauncher launcher = launchers.getIfAvailable();
        if (launcher != null) {
            runHere(launcher, verb, id, from, threads);
        } else {
            runThere(verb, id, from, threads);
        }
    }

    private void runHere(CrawlerLauncher launcher, String verb, String id, String from,
            Integer threads) throws Exception {
        CatalogSnapshot catalog = ops.catalog(id);
        switch (verb) {
            case "rebuild" -> watch(catalog, "rebuild",
                    live -> launcher.rebuild(catalog.getId(), threads, live));
            case "merge" -> watch(catalog, "merge",
                    live -> launcher.update(catalog.getId(), from, true, threads, live));
            case "update", "resume" -> watch(catalog, verb,
                    live -> launcher.update(catalog.getId(), from, false, threads, live));
            default -> watch(catalog, "crawl",
                    live -> launcher.crawl(catalog.getId(), threads, live));
        }
    }

    /**
     * The crawl is the cluster's, not this terminal's: it is started on whichever nodes are in
     * the cluster and it outlives the session that asked for it. Leaving the view with q, or
     * closing the terminal altogether, stops the watching and nothing else.
     */
    private void runThere(String verb, String id, String from, Integer threads) {
        CatalogSnapshot catalog = ops.catalog(id);
        print(Ansi.bold(verb + " '" + catalog.getName() + "' v" + catalog.getVersion()) + "  "
                + Ansi.dim(catalog.getUrl() + "  ->  " + String.join("+", catalog.getOutputTypes()
                        .stream().map(OutputType::getRepr).toList())));
        print(Ansi.dim(ops.start(new StartAsk(verb, catalog.getId(), from, threads))));
        boolean finished = follow(catalog.getId(), false);
        if (finished) {
            print(Ansi.green("'" + catalog.getName() + "' has finished."));
            print(Ansi.dim("What it did:  crawler-report --id=" + catalog.getId()));
        } else {
            print(Ansi.dim("Still crawling '" + catalog.getName() + "'. Watch it again with"
                    + " 'status', stop it with 'pause --id=" + catalog.getId() + "'."));
        }
    }

    /**
     * Draws a crawl running somewhere else, one frame a second, until it ends or the reader
     * leaves.
     *
     * @return true when the crawl finished
     */
    private boolean follow(String catalogId, boolean perNode) {
        try (LiveDashboard dashboard =
                new LiveDashboard(() -> ops.live(catalogId, perNode), System.out)) {
            boolean detachable = !oneShot && io.isInteractive();
            if (detachable) {
                print(Ansi.dim("Type q then return to stop watching; the crawl keeps going."));
            }
            dashboard.start();
            return dashboard.await(dashboard::finished, detachable ? io : null);
        }
    }

    /**
     * What every verb does: start the run behind the prompt, show the live view, and report.
     */
    private void watch(CatalogSnapshot catalog, String verb, Run run) throws Exception {
        if (registries.getObject().isRunning(catalog.getId())) {
            throw new WebCrawlerException("'" + catalog.getName() + "' is already running."
                    + " Watch it with 'status', or stop it with 'pause --id=" + catalog.getId()
                    + "'.");
        }
        print(Ansi.bold(verb + " '" + catalog.getName() + "' v" + catalog.getVersion()) + "  "
                + Ansi.dim(catalog.getUrl() + "  ->  " + String.join("+", catalog.getOutputTypes()
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
            BooleanSupplier finished) {
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
    private void report(CatalogSnapshot catalog, Future<CrawlerEngine.Result> future,
            boolean finished) throws Exception {
        if (!finished) {
            print(Ansi.dim("Still crawling '" + catalog.getName() + "'. Watch it again with"
                    + " 'status', stop it with 'pause --id=" + catalog.getId() + "'."));
            return;
        }
        try {
            CrawlerEngine.Result result = future.get();
            print(summary(result));
            whatToDoNext(catalog);
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
     * The id, and the verbs that take one.
     *
     * <p>
     * Only after a run that was given a url: the id was made here, and everything afterwards --
     * updating it, merging it, looking at what it got -- is addressed by it. Somebody who typed an
     * id already has it, and a cron line does not want three more lines of output.
     */
    private void whatToDoNext(CatalogSnapshot catalog) {
        if (!addressedWithoutAnId) {
            return;
        }
        String id = catalog.getId();
        // printed as lines rather than a table: an id is thirty-six characters and a column that
        // truncates one is a hint nobody can copy
        print("");
        print(Ansi.bold("Catalog '" + catalog.getName() + "' is ") + Ansi.cyan(id));
        print(Ansi.dim("Next, by id:"));
        String cli = "  ./greenfinger-cli.sh --cluster=" + clusterName() + " ";
        print(cli + "update --id=" + id
                + Ansi.dim("     the urls that have appeared since"));
        print(cli + "merge --id=" + id
                + Ansi.dim("      revisit what is held, merge changes"));
        print(cli + "rebuild --id=" + id
                + Ansi.dim("    a new version, the whole site again"));
        print(Ansi.dim("Look at what it got, search it, delete it:  ./greenfinger-shell.sh"));
    }

    /**
     * A run that achieved nothing at all is a failure, whatever the exit code would say.
     *
     * <p>
     * A url that does not resolve throws nothing -- it is counted as another failed fetch -- so
     * without this the command exits zero having done nothing. Strictly nothing: saving no pages
     * is fine for a merge of an unchanged site, because those pages were still reached.
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

        CrawlerEngine.Result execute(Consumer<WebCrawlerExecutionContext> onReady)
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
        CatalogSnapshot catalog = ops.catalog(options.get("id", null));
        Integer version = options.getIntegerOrNull("version");
        Integer keepLatest = options.getIntegerOrNull("keepLatest");
        boolean purge = options.getBoolean("purge", false);
        boolean all = options.getBoolean("all", false);
        if (version == null && keepLatest == null && !purge && !all) {
            throw new UsageException(
                    "Say what to remove: --version, --keep-latest, --all or --purge");
        }
        boolean everyVersion = version == null && keepLatest == null;
        boolean dryRun = options.getBoolean("dryRun", false);
        List<DeleteReport.Line> lines = ops.delete(new DeleteAsk(catalog.getId(), version,
                keepLatest, all, purge, DeleteLayer.parse(options.get("layers", "all")), dryRun,
                options.getBoolean("force", false)));
        if (lines.isEmpty()) {
            print(Ansi.dim("Nothing matches."));
            return;
        }

        TextTable table = TextTable.of("Version", "Layer", "Count", "Bytes", "Problem")
                .title(dryRun ? "Would delete" : "Deleted");
        for (DeleteReport.Line line : lines) {
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
        CatalogSnapshot catalog = ops.catalog(options.get("id", null));
        ReplayAnswer answer = ops.replay(new ReplayAsk(catalog.getId(),
                options.getIntegerOrNull("version"),
                OutputType.parseExact(options.get("layers", "index+vector"))));
        print(Ansi.green(
                "Replayed " + answer.replayed() + " page(s) of v" + answer.version()));
        // the file layer is the one that can come back incomplete -- a page taken down since the
        // crawl cannot be restored at all -- so what it could not do is said out loud
        if (answer.files() != null) {
            print(Ansi.dim("Files: " + answer.files().pages() + " page(s) and "
                    + answer.files().images() + " image(s) written, " + answer.files().intact()
                    + " already there, " + answer.files().unreachable() + " unreachable, "
                    + answer.files().changed() + " changed since the crawl"));
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
        table.row("search --query=<words>", "Search crawled pages by the words on them");
        table.row("search --query=<words> --mode=meaning", "Pages about it, whether or not they"
                + " say it");
        table.row("search --query=<words> --mode=pictures", "Find pictures by describing them");
        table.row("search", "With no query: everything that was kept");
        table.row("index-info", "The full text index, and what is in it");
        table.row("vector-info", "The vector store, and what is in it");
        table.row("", "");
        table.row("options", "Every catalog setting and its default");
        table.row("help", "This list");
        print(table.render());
        print("Every option is long form. Id comes from catalog-list.");
        print("Quick start:  ./greenfinger-shell.sh          the prompt, then  catalog-save");
        print("One line:     ./greenfinger-cli.sh --cluster=<name> crawl --id=<id> --node=3");
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

    /** Its own cluster when there is one, and a placeholder to fill in when there is not. */
    private String clusterName() {
        return StringUtils.isNotBlank(cluster) ? cluster : "<name>";
    }

    private boolean isKnown(String command) {
        return command != null && List.of("crawl", "catalog-crawl", "update", "resume",
                "rebuild", "pause",
                "status", "delete", "replay", "versions", "crawler-report", "test-url", "options",
                "help", "catalog-list", "catalogs", "catalog-show", "catalog", "catalog-save",
                "catalog-delete", "catalog-cats", "cats", "search", "query", "index-info",
                "index", "vector-info", "vector").contains(command);
    }

    /**
     * Commands that live on the other command classes, routed here so one dispatcher serves the
     * whole command line.
     */
    private void print(String text) {
        System.out.println(text);
    }

}
