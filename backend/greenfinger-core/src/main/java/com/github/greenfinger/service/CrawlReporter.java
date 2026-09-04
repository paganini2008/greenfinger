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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.state.Dashboard;
import com.github.greenfinger.core.engine.CrawlerEngine;
import com.github.greenfinger.core.engine.WebCrawlerExecutionContext;
import com.github.greenfinger.core.output.BlobStore;
import com.github.greenfinger.core.output.FileLayout;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes one report per crawl, beside the version it produced.
 *
 * <p>
 * Every run, not every version: a version is crawled once and then updated, and each of those is a
 * separate thing that happened with its own numbers. {@code settings.json} keeps only the latest,
 * which answers "how is this version configured" and cannot answer "what happened on Tuesday".
 *
 * <h2>What it is for</h2>
 * Two questions, and the second is the reason it exists at all. <em>Did this run work?</em> --
 * pages saved, images, how long. And <em>what did it not finish?</em> -- urls dispatched against
 * urls handled, with the difference spelled out. A crawl that stopped at a limit, or lost a node
 * part way, ends normally and reports what it left behind; those urls are still on a frontier, so
 * {@code update} picks them up without re-fetching anything already saved.
 *
 * <p>
 * A failure to write the report never fails the crawl. Losing the account of a run that worked
 * would be a poor trade for the run itself.
 * 
 * @Description: CrawlReporter
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class CrawlReporter {

    static final String REPORTS = "reports";

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @return the path written, or null when it could not be written.
     */
    public String write(BlobStore blobStore, FileLayout layout, CatalogDetails catalogDetails,
            WebCrawlerExecutionContext context, CrawlerEngine.Result result, String action,
            boolean refresh, String node) {
        // the node is in the name because every node writes its own and they are then copied to
        // each other: without it, three files from one crawl look like three crawls
        String path = layout.versionPrefix() + "/" + REPORTS + "/" + STAMP.format(Instant.now())
                + "-" + action + "-" + node + ".json";
        try {
            blobStore.writeText(path, objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(
                            report(catalogDetails, context, result, action, refresh, node)));
            return path;
        } catch (Exception e) {
            log.warn("Could not write the run report for '{}': {}", catalogDetails.getName(),
                    e.getMessage());
            return null;
        }
    }

    Map<String, Object> report(CatalogDetails catalogDetails, WebCrawlerExecutionContext context,
            CrawlerEngine.Result result, String action, boolean refresh, String node) {
        Dashboard dashboard = result.getDashboard();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("node", node);
        report.put("catalog", catalogDetails.getName());
        report.put("catalogId", catalogDetails.getId());
        report.put("version", catalogDetails.getVersion());
        report.put("action", action);
        report.put("refresh", refresh);
        report.put("startTime", new Date(dashboard.getStartTime()));
        report.put("endTime", new Date());
        report.put("elapsedMillis", dashboard.getElapsedTime());

        Map<String, Object> produced = new LinkedHashMap<>();
        produced.put("savedResourceCount", dashboard.getSavedResourceCount());
        produced.put("savedImageCount", dashboard.getSavedImageCount());
        produced.put("indexedResourceCount", dashboard.getIndexedResourceCount());
        report.put("produced", produced);

        // the pair that says whether anything was left behind, and how much
        Map<String, Object> urls = new LinkedHashMap<>();
        urls.put("dispatched", dashboard.getTotalUrlCount());
        urls.put("handled", dashboard.getHandledUrlCount());
        urls.put("outstanding", result.getOutstanding());
        urls.put("alreadySeen", dashboard.getExistingUrlCount());
        urls.put("filtered", dashboard.getFilteredUrlCount());
        urls.put("unreachable", dashboard.getInvalidUrlCount());
        urls.put("duplicateContent", dashboard.getDuplicatedContentCount());
        urls.put("failures", result.getFailures());
        report.put("urls", urls);

        Map<String, Object> ending = new LinkedHashMap<>();
        ending.put("reason", result.getReason());
        ending.put("fullyCrawled", result.isFullyCrawled());
        ending.put("published", result.isSelfTerminated());
        ending.put("remainingOnThisNode", result.getRemaining());
        if (result.getOutstanding() > 0) {
            ending.put("note", result.getOutstanding() + " url(s) were dispatched and never"
                    + " reported finished. They are still on a frontier; run"
                    + " 'update --catalog " + catalogDetails.getName() + "' to pick them up.");
        }
        report.put("ending", ending);

        List<String> members = context.getGlobalStateManager().getMembers();
        report.put("nodes", members == null ? List.of() : members);
        // who did what. The totals say the crawl saved 44 pages; only this says whether one node
        // saved forty of them because the other two spent the run unable to reach the site
        Map<String, Map<String, Long>> byNode = context.getGlobalStateManager().perNodeCounters();
        if (!byNode.isEmpty()) {
            report.put("byNode", byNode);
        }
        return report;
    }

}
