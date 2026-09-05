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

package com.github.greenfinger.api.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsService;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.core.WebCrawlerSemaphore;
import com.github.greenfinger.core.engine.CrawlRegistry;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.DeleteLayer;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.service.CatalogAdminService;
import com.github.greenfinger.service.CrawlerLauncher;
import com.github.greenfinger.service.DeleteReport;
import com.github.greenfinger.service.CrawlReportService;
import com.github.greenfinger.service.DeletionService;
import com.github.greenfinger.service.ReplayService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Starting, stopping and inspecting crawls, plus the delete and replay operations.
 *
 * <p>
 * A crawl runs for minutes or hours, so the three verbs return as soon as the run has been handed
 * to a background thread. Progress is read from {@code /status}, which is what a page polls.
 * 
 * @Description: CrawlApiController
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("${greenfinger.api.prefix:/v2}/crawl")
@RequiredArgsConstructor
public class CrawlApiController {

    private final CrawlerLauncher crawlerLauncher;
    private final CatalogAdminService catalogAdminService;
    private final CatalogDetailsService catalogDetailsService;
    private final CrawlRegistry crawlRegistry;
    private final DeletionService deletionService;
    private final ReplayService replayService;
    private final CrawlReportService crawlReportService;
    private final WebCrawlerSemaphore semaphore;

    private final ExecutorService background = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "greenfinger-api");
        thread.setDaemon(true);
        return thread;
    });

    @PreDestroy
    public void destroy() {
        background.shutdownNow();
    }

    @PostMapping("/{idOrName}")
    public ApiResult<String> crawl(@PathVariable("idOrName") String idOrName) {
        return launch(idOrName, "crawl", null);
    }

    @PostMapping("/{idOrName}/update")
    public ApiResult<String> update(@PathVariable("idOrName") String idOrName,
            @RequestParam(value = "from", required = false) String from) {
        return launch(idOrName, "update", from);
    }

    @PostMapping("/{idOrName}/rebuild")
    public ApiResult<String> rebuild(@PathVariable("idOrName") String idOrName) {
        return launch(idOrName, "rebuild", null);
    }

    private ApiResult<String> launch(String idOrName, String verb, String from) {
        Catalog catalog = catalogAdminService.require(idOrName);
        // Asked here as well as in the launcher. The launcher is the authority, but it runs on a
        // background thread and this method has already answered by then -- so without this the
        // caller is told "started" and the crawl is refused a moment later where nobody is
        // looking. A race can still slip past; the launcher then refuses it properly.
        if (!semaphore.available(catalog.getId())) {
            String inTheWay = crawlInTheWay(catalog);
            throw new WebCrawlerException("A crawl of '" + inTheWay + "' is already running."
                    + " One at a time, here and on every other node: two would divide the"
                    + " bandwidth rather than double it. Wait for it, or stop it with"
                    + " POST /crawl/" + inTheWay + "/interrupt");
        }
        background.submit(() -> {
            try {
                switch (verb) {
                    case "rebuild" -> crawlerLauncher.rebuild(catalog.getId(), null);
                    case "update" -> crawlerLauncher.update(catalog.getId(), from, null);
                    default -> crawlerLauncher.crawl(catalog.getId(), null);
                }
            } catch (Exception e) {
                log.error("Crawl of '{}' failed: {}", catalog.getName(), e.getMessage(), e);
            }
        });
        return ApiResult.ok(verb + " of '" + catalog.getName() + "' started");
    }

    /**
     * The name of whatever is in the way, because an id is not something anybody can act on.
     * This node's own crawl first -- it is the more likely answer and needs no lookup of the
     * table -- and then whatever the table says is running elsewhere.
     */
    private String crawlInTheWay(Catalog wanted) {
        String here = semaphore.getCatalogId();
        if (here != null) {
            return here.equals(wanted.getId()) ? wanted.getName()
                    : catalogAdminService.find(here).map(Catalog::getName).orElse(here);
        }
        return semaphore.running().stream().map(Catalog::getName).findFirst()
                .orElse("another catalog");
    }

    @PostMapping("/{idOrName}/interrupt")
    public ApiResult<Boolean> interrupt(@PathVariable("idOrName") String idOrName) {
        Catalog catalog = catalogAdminService.require(idOrName);
        return ApiResult.ok(crawlRegistry.interrupt(catalog.getId()));
    }

    @GetMapping("/status")
    public ApiResult<List<Map<String, Object>>> status() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Catalog catalog : catalogAdminService.findAll()) {
            CatalogDetails details = catalogDetailsService.loadCatalogDetails(catalog.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", catalog.getId());
            row.put("name", catalog.getName());
            row.put("running", crawlRegistry.isRunning(catalog.getId()));
            row.put("runningState", catalog.getRunningState());
            row.put("indexVersion", details.getVersion());
            row.put("searchVersion", details.getSearchVersion());
            crawlRegistry.getDashboard(catalog.getId()).ifPresent(dashboard -> {
                row.put("savedResourceCount", dashboard.getSavedResourceCount());
                row.put("savedImageCount", dashboard.getSavedImageCount());
                row.put("totalUrlCount", dashboard.getTotalUrlCount());
                row.put("handledUrlCount", dashboard.getHandledUrlCount());
            });
            rows.add(row);
        }
        return ApiResult.ok(rows);
    }

    /**
     * Removes versions from whichever stores are named. Defaults to a dry run, because this is the
     * one endpoint that cannot be undone.
     */
    /**
     * Every run of this catalog, newest first. One entry per crawl, update or rebuild -- not per
     * version, because a version is crawled once and then updated, and each of those is its own
     * thing that happened.
     */
    @GetMapping("/{idOrName}/reports")
    public ApiResult<List<Map<String, Object>>> reports(
            @PathVariable("idOrName") String idOrName) throws Exception {
        return ApiResult.ok(crawlReportService.list(idOrName));
    }

    /**
     * One report, by the path the listing gave for it.
     *
     * <p>
     * The path is a query parameter rather than part of the url because it contains slashes: a
     * report lives at {@code {catalog}/v{n}/reports/{stamp}-{action}.json}, and a path variable
     * would have to be escaped by every caller.
     */
    @GetMapping("/{idOrName}/report")
    public ApiResult<Map<String, Object>> report(@PathVariable("idOrName") String idOrName,
            @RequestParam("path") String path) throws Exception {
        return ApiResult.ok(crawlReportService.get(path));
    }

    @DeleteMapping("/{idOrName}/versions")
    public ApiResult<List<DeleteReport.Line>> delete(@PathVariable("idOrName") String idOrName,
            @RequestParam(value = "version", required = false) Integer version,
            @RequestParam(value = "keepLatest", required = false) Integer keepLatest,
            @RequestParam(value = "layers", defaultValue = "all") String layers,
            @RequestParam(value = "dryRun", defaultValue = "true") boolean dryRun,
            @RequestParam(value = "force", defaultValue = "false") boolean force,
            @RequestParam(value = "purge", defaultValue = "false") boolean purge) {
        Catalog catalog = catalogAdminService.require(idOrName);
        CatalogDetails details = catalogDetailsService.loadCatalogDetails(catalog.getId());
        List<Integer> present = deletionService.versionsOf(details);

        Set<DeleteLayer> selected = DeleteLayer.parse(layers);
        // naming no versions means the whole catalog, which is a different operation from naming
        // every one of them: emptied, or -- with purge -- the index dropped as well
        if (version == null && keepLatest == null) {
            return ApiResult.ok(purge
                    ? deletionService.deleteCatalog(details, selected, dryRun, force).getLines()
                    : deletionService.cleanCatalog(details, selected, dryRun, force).getLines());
        }

        List<Integer> targets = new ArrayList<>();
        if (version != null) {
            targets.add(version);
        } else {
            present.stream().sorted().limit(Math.max(0, present.size() - keepLatest))
                    .forEach(targets::add);
        }
        if (targets.isEmpty()) {
            return ApiResult.ok(List.of());
        }
        return ApiResult.ok(
                deletionService.delete(details, targets, selected, dryRun, force).getLines());
    }

    @PostMapping("/{idOrName}/replay")
    public ApiResult<Long> replay(@PathVariable("idOrName") String idOrName,
            @RequestParam(value = "version", required = false) Integer version,
            @RequestParam(value = "layers", defaultValue = "index,vector") String layers)
            throws Exception {
        Catalog catalog = catalogAdminService.require(idOrName);
        CatalogDetails details = catalogDetailsService.loadCatalogDetails(catalog.getId());
        int target = version != null ? version : details.getVersion();
        return ApiResult
                .ok(replayService.replay(catalog.getId(), target, OutputType.parseExact(layers)));
    }

}
