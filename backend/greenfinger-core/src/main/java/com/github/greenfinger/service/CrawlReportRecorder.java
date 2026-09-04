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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.sql.DataSource;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.state.Dashboard;
import com.github.greenfinger.core.engine.CrawlerEngine;
import com.github.greenfinger.core.engine.WebCrawlerExecutionContext;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.output.BlobStore;
import com.github.greenfinger.core.output.FileLayout;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.core.report.CrawlReportStore;
import com.github.greenfinger.output.OutputProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes the {@code crawler_report} row for a version, once the run that built it is over.
 *
 * <p>
 * Written by the node that started the run, and by that node only. Every node writes its own file
 * report because a file report is that node's account of its own share; this is the opposite kind
 * of document -- one per version, describing the whole crawl -- and three nodes each writing their
 * own idea of "the whole crawl" would be three rows disagreeing about the same number.
 *
 * <p>
 * Everything it can find out, it writes down, including the things that are empty: a section that
 * is missing reads as "nobody looked", and a section that says zero reads as what happened. That
 * is the whole point of keeping it -- somebody asking in six months why a version is the size it
 * is needs an answer, and "the index section is absent" is not one.
 * 
 * @Description: CrawlReportRecorder
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class CrawlReportRecorder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final CrawlReportStore reportStore;
    private final ResourceRecordStore recordStore;
    private final OutputProperties outputProperties;
    private final ClusterSnapshot clusterSnapshot;

    /** Null when the application built its own persistence and did not expose one. */
    private final DataSource dataSource;

    /**
     * Never throws. Losing the account of a run that worked would be a poor trade for the run.
     *
     * @return the json written, or null when it could not be written.
     */
    public String record(CatalogDetails catalogDetails, WebCrawlerExecutionContext context,
            CrawlerEngine.Result result, String action, boolean refresh, String node,
            BlobStore blobStore, FileLayout fileLayout) {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                    report(catalogDetails, context, result, action, refresh, node, blobStore,
                            fileLayout));
            reportStore.save(catalogDetails.getId(), catalogDetails.getVersion(), json);
            return json;
        } catch (Exception e) {
            log.warn("Could not record the report of catalog '{}' v{}: {}",
                    catalogDetails.getName(), catalogDetails.getVersion(), e.getMessage());
            return null;
        }
    }

    Map<String, Object> report(CatalogDetails catalogDetails, WebCrawlerExecutionContext context,
            CrawlerEngine.Result result, String action, boolean refresh, String node,
            BlobStore blobStore, FileLayout fileLayout) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("catalogId", catalogDetails.getId());
        report.put("catalog", catalogDetails.getName());
        report.put("version", catalogDetails.getVersion());
        report.put("recordedAt", new Date());
        report.put("recordedBy", node);

        Map<String, Object> run = new LinkedHashMap<>();
        run.put("action", action);
        run.put("refresh", refresh);
        run.put("startTime", new Date(result.getDashboard().getStartTime()));
        run.put("endTime", new Date());
        run.put("elapsedMillis", result.getDashboard().getElapsedTime());
        run.put("reason", result.getReason());
        run.put("fullyCrawled", result.isFullyCrawled());
        run.put("published", result.isSelfTerminated());
        run.put("failures", result.getFailures());
        run.put("outstanding", result.getOutstanding());
        run.put("remainingOnThisNode", result.getRemaining());
        run.put("filePath", result.getReportPath() != null ? result.getReportPath() : "");
        report.put("run", run);

        report.put("dashboard", counters(result.getDashboard()));
        report.put("nodes", perNode(context));
        report.put("cluster", clusterSnapshot.snapshot());
        report.put("database", database(catalogDetails));
        report.put("storage", storage(catalogDetails, blobStore, fileLayout));
        report.put("outputs", outputs(catalogDetails));
        report.put("settings", settings(blobStore, fileLayout));
        return report;
    }

    /**
     * The totals, which are the cluster's rather than this node's whenever there is a cluster.
     */
    private Map<String, Object> counters(Dashboard dashboard) {
        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put("totalUrlCount", dashboard.getTotalUrlCount());
        counters.put("handledUrlCount", dashboard.getHandledUrlCount());
        counters.put("existingUrlCount", dashboard.getExistingUrlCount());
        counters.put("filteredUrlCount", dashboard.getFilteredUrlCount());
        counters.put("invalidUrlCount", dashboard.getInvalidUrlCount());
        counters.put("duplicatedContentCount", dashboard.getDuplicatedContentCount());
        counters.put("savedResourceCount", dashboard.getSavedResourceCount());
        counters.put("savedImageCount", dashboard.getSavedImageCount());
        counters.put("indexedResourceCount", dashboard.getIndexedResourceCount());
        counters.put("startTime", new Date(dashboard.getStartTime()));
        counters.put("elapsedMillis", dashboard.getElapsedTime());
        counters.put("averageExecutionTime", dashboard.getAverageExecutionTime());
        counters.put("completed", dashboard.isCompleted());
        return counters;
    }

    /**
     * Who did what, turned the way round a reader wants it.
     *
     * <p>
     * The state manager keeps the counters by counter and then by node, because that is how they
     * are incremented. A report is read by node -- "what did node c3d4 do" -- so it is inverted
     * here rather than in the caller.
     */
    private List<Map<String, Object>> perNode(WebCrawlerExecutionContext context) {
        return perNode(context.getGlobalStateManager().perNodeCounters(),
                context.getGlobalStateManager().getMembers());
    }

    /**
     * @param byCounter what the state manager keeps: counter, then node.
     * @param members every node in the crawl, which is a different list and not always in the
     *        same spelling.
     */
    static List<Map<String, Object>> perNode(Map<String, Map<String, Long>> byCounter,
            List<String> members) {
        Map<String, Map<String, Long>> byNode = new TreeMap<>();
        if (byCounter != null) {
            byCounter.forEach((counter, nodes) -> nodes
                    .forEach((nodeId, value) -> byNode
                            .computeIfAbsent(nodeId, key -> new LinkedHashMap<>())
                            .put(counter, value)));
        }
        // A member that fetched nothing is still a member, and its empty row is the finding.
        // Matched loosely because the two halves of the cluster name a node differently: the
        // membership list holds the full uuid and the counters are keyed by its short form, so an
        // exact lookup would add a second, empty row for every node that had in fact done work.
        if (members != null) {
            members.stream().filter(member -> byNode.keySet().stream().noneMatch(
                    counted -> member.startsWith(counted) || counted.startsWith(member)))
                    .forEach(member -> byNode.put(member, new LinkedHashMap<>()));
        }
        return byNode.entrySet().stream().map(entry -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("node", entry.getKey());
            row.put("dashboard", entry.getValue());
            return row;
        }).toList();
    }

    /**
     * What the rows landed in, and how many of them there are.
     */
    private Map<String, Object> database(CatalogDetails catalogDetails) {
        Map<String, Object> database = new LinkedHashMap<>();
        database.put("product", "");
        database.put("version", "");
        database.put("url", "");
        database.put("driver", "");
        if (dataSource != null) {
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                database.put("product", metaData.getDatabaseProductName());
                database.put("version", metaData.getDatabaseProductVersion());
                // no credentials: a jdbc url may carry them, and this row is read by anybody who
                // can read the catalog
                database.put("url", stripCredentials(metaData.getURL()));
                database.put("driver", metaData.getDriverName());
            } catch (Exception e) {
                log.debug("Could not read the database metadata: {}", e.getMessage());
            }
        }
        database.put("resourceCount", count(() -> recordStore
                .countByCatalog(catalogDetails.getId(), catalogDetails.getVersion())));
        database.put("imageCount", count(() -> recordStore
                .countImagesByCatalog(catalogDetails.getId(), catalogDetails.getVersion())));
        return database;
    }

    /**
     * A jdbc url may carry a password in its query string, and this report is not a secret store.
     */
    static String stripCredentials(String url) {
        if (url == null) {
            return "";
        }
        int query = url.indexOf('?');
        return query >= 0 ? url.substring(0, query) : url;
    }

    /**
     * Where the pages went, and how much of it there is. Counted from the store rather than from
     * the counters: the counters say what this run wrote, and this says what the version holds.
     */
    private Map<String, Object> storage(CatalogDetails catalogDetails, BlobStore blobStore,
            FileLayout fileLayout) {
        OutputProperties.File file = outputProperties.getFile();
        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("target", file.getTarget());
        storage.put("store", blobStore != null ? blobStore.getName() : "");
        storage.put("directory", file.getDirectory());
        storage.put("shardDepth", file.getShardDepth());
        storage.put("bucket", file.getMinio().getBucket());
        storage.put("endpoint", file.getMinio().getEndpoint());
        storage.put("prefix", fileLayout != null ? fileLayout.versionPrefix() : "");
        storage.put("fileCount", 0);
        storage.put("bytes", 0L);
        if (blobStore != null && fileLayout != null) {
            try {
                storage.put("fileCount", blobStore.listPrefix(fileLayout.versionPrefix()).size());
                storage.put("bytes", blobStore.sizeOfPrefix(fileLayout.versionPrefix()));
            } catch (Exception e) {
                log.debug("Could not measure the blob store: {}", e.getMessage());
            }
        }
        return storage;
    }

    /**
     * The index and the vector store this version was written to, named whether or not it was.
     */
    private Map<String, Object> outputs(CatalogDetails catalogDetails) {
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("types",
                catalogDetails.getOutputTypes().stream().map(OutputType::getRepr).toList());
        OutputProperties.Index indexConfig = outputProperties.getIndex();
        Map<String, Object> index = new LinkedHashMap<>();
        index.put("enabled", catalogDetails.hasOutput(OutputType.INDEX));
        index.put("provider", indexConfig.getProvider());
        // both, whichever is in use: a report read a year from now is read by somebody who does
        // not know which provider that machine was configured with
        index.put("uris", indexConfig.getUris());
        index.put("directory", indexConfig.getLucene().getDirectory());
        index.put("index", com.github.greenfinger.core.output.IndexAdmin
                .indexOf(indexConfig.getPrefix(), catalogDetails.getId()));
        index.put("analyzer", "lucene".equalsIgnoreCase(indexConfig.getProvider())
                ? indexConfig.getLucene().getAnalyzer()
                : indexConfig.getAnalyzer());
        outputs.put("index", index);

        Map<String, Object> vector = new LinkedHashMap<>();
        vector.put("enabled", catalogDetails.hasOutput(OutputType.VECTOR));
        vector.put("store", outputProperties.getVector().getStore());
        vector.put("directory", outputProperties.getVector().getLucene().getDirectory());
        vector.put("textCollection", outputProperties.getVector().getTextCollection());
        vector.put("imageCollection", outputProperties.getVector().getImageCollection());
        outputs.put("vector", vector);
        return outputs;
    }

    /**
     * The settings file the version wrote, embedded whole. It is the definition the crawl actually
     * ran under, and the catalog row it came from is editable afterwards.
     */
    private Map<String, Object> settings(BlobStore blobStore, FileLayout fileLayout) {
        if (blobStore == null || fileLayout == null) {
            return Map.of();
        }
        try {
            return blobStore.readText(fileLayout.settings())
                    .map(this::parse).orElse(Map.of());
        } catch (Exception e) {
            log.debug("Could not read settings.json: {}", e.getMessage());
            return Map.of();
        }
    }

    private Map<String, Object> parse(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private long count(java.util.function.LongSupplier supplier) {
        try {
            return supplier.getAsLong();
        } catch (Exception e) {
            return -1L;
        }
    }

}
