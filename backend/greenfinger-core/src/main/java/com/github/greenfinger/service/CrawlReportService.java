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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsService;
import com.github.greenfinger.core.output.BlobStore;
import com.github.greenfinger.core.output.FileLayout;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.core.report.CrawlReportStore;
import com.github.greenfinger.core.utils.BeanLifeCycleUtils;
import com.github.greenfinger.output.OutputFactory;
import lombok.RequiredArgsConstructor;

/**
 * Reads back the reports {@link CrawlReporter} wrote.
 *
 * <p>
 * They live beside the version they describe rather than in a table, which means deleting a
 * version takes its history with it, and a directory copied elsewhere carries its own account of
 * how it was made. The cost is that listing them is a prefix scan rather than a query -- fine for
 * a handful of runs per version, which is what this is.
 * 
 * @Description: CrawlReportService
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@RequiredArgsConstructor
public class CrawlReportService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OutputFactory outputFactory;
    private final CatalogDetailsService catalogDetailsService;
    private final CatalogAdminService catalogAdminService;
    private final CrawlReportStore reportStore;
    private final ResourceRecordStore recordStore;

    /**
     * The version numbers this catalog still has, newest first, with what each one holds.
     *
     * <p>
     * From the records rather than from the reports: a version exists because there are rows in it,
     * and a report is something written afterwards that may not have been.
     */
    public List<Map<String, Object>> versions(String catalogRef) {
        CatalogDetails catalogDetails = detailsOf(catalogRef);
        List<Integer> versions = new ArrayList<>(recordStore.findVersions(catalogDetails.getId()));
        // the version currently being written has no rows yet on a crawl that has not saved
        // anything, and it is still a version somebody may want to ask about
        if (!versions.contains(catalogDetails.getVersion())) {
            versions.add(catalogDetails.getVersion());
        }
        versions.sort(Comparator.reverseOrder());
        return versions.stream().map(version -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("version", version);
            row.put("current", version.equals(catalogDetails.getVersion()));
            row.put("searchable", version.equals(catalogDetails.getSearchVersion()));
            row.put("pages", recordStore.countByCatalog(catalogDetails.getId(), version));
            row.put("images", recordStore.countImagesByCatalog(catalogDetails.getId(), version));
            reportStore.find(catalogDetails.getId(), version).ifPresentOrElse(report -> {
                row.put("createdAt", report.getCreatedAt());
                row.put("updatedAt", report.getUpdatedAt());
            }, () -> {
                row.put("createdAt", null);
                row.put("updatedAt", null);
            });
            return row;
        }).toList();
    }

    /**
     * The stored report of one version, or of the newest version that has one.
     *
     * @param version null for the latest.
     */
    public Optional<Map<String, Object>> stored(String catalogRef, Integer version) {
        CatalogDetails catalogDetails = detailsOf(catalogRef);
        if (version != null) {
            return reportStore.find(catalogDetails.getId(), version)
                    .map(report -> parse(report.getContent()));
        }
        return reportStore.findByCatalog(catalogDetails.getId()).stream().findFirst()
                .map(report -> parse(report.getContent()));
    }

    /**
     * Every run of this catalog, newest first, across every version it still has.
     */
    public List<Map<String, Object>> list(String catalogRef) throws Exception {
        CatalogDetails catalogDetails = detailsOf(catalogRef);
        BlobStore blobStore = outputFactory.getBlobStore();
        BeanLifeCycleUtils.afterPropertiesSet(blobStore);
        try {
            List<Map<String, Object>> reports = new ArrayList<>();
            for (int version = catalogDetails.getVersion(); version >= 0; version--) {
                FileLayout layout = new FileLayout(catalogDetails.getId(), version,
                        outputFactory.shardDepth());
                for (String path : blobStore
                        .listPrefix(layout.versionPrefix() + "/" + CrawlReporter.REPORTS + "/")) {
                    read(blobStore, path).ifPresent(report -> {
                        report.put("path", path);
                        reports.add(report);
                    });
                }
            }
            // newest first: the run somebody is asking about is almost always the last one
            reports.sort(Comparator.comparing(
                    (Map<String, Object> r) -> String.valueOf(r.get("path"))).reversed());
            return reports;
        } finally {
            BeanLifeCycleUtils.destroyQuietly(blobStore);
        }
    }

    /**
     * One report by the path {@link #list} gave for it.
     */
    public Map<String, Object> get(String path) throws Exception {
        BlobStore blobStore = outputFactory.getBlobStore();
        BeanLifeCycleUtils.afterPropertiesSet(blobStore);
        try {
            return read(blobStore, path).orElseThrow(
                    () -> new WebCrawlerException("No such run report: " + path));
        } finally {
            BeanLifeCycleUtils.destroyQuietly(blobStore);
        }
    }

    private Optional<Map<String, Object>> read(BlobStore blobStore, String path) {
        try {
            return blobStore.readText(path).map(this::parse);
        } catch (Exception e) {
            // one unreadable report must not hide the others
            return Optional.empty();
        }
    }

    private Map<String, Object> parse(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new WebCrawlerException("Could not read a run report", e);
        }
    }

    private CatalogDetails detailsOf(String catalogRef) {
        return catalogDetailsService
                .loadCatalogDetails(catalogAdminService.require(catalogRef).getId());
    }

}
