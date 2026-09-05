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

import java.util.LinkedHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.core.WebCrawlerSemaphore;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.model.DeleteLayer;
import com.github.greenfinger.core.output.BlobStore;
import com.github.greenfinger.core.output.FileLayout;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.core.report.CrawlReportStore;
import com.github.greenfinger.core.utils.BeanLifeCycleUtils;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.core.output.IndexAdmin;
import com.github.greenfinger.output.vector.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Removes one or more versions from any combination of the four stores.
 *
 * <p>
 * Deletion runs in the reverse of the order writing runs in -- vector, index, file, then database
 * -- because the database is where the list of what to delete comes from, and removing it first
 * would leave the file paths unknown.
 *
 * <p>
 * Each layer is attempted independently and reported separately: four stores cannot be emptied
 * atomically, so a run that half succeeded must be visible and safely repeatable. Repeating is
 * safe because deleting a version that is already gone does nothing.
 * 
 * @Description: DeletionService
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class DeletionService {

    private final OutputFactory outputFactory;
    private final OutputProperties outputProperties;

    /** For the three RocksDB directories, which are named here and nowhere else. */
    private final WebCrawlerProperties webCrawlerProperties;

    private final ResourceRecordStore recordStore;
    private final WebCrawlerSemaphore semaphore;

    /** Only to put the version numbers back to zero once every version has gone. */
    private final CatalogStore catalogStore;

    /**
     * The version's row in {@code crawler_report}, which goes with the rows it accounts for. Null
     * when the application wired the crawler without it.
     */
    private final CrawlReportStore reportStore;

    /**
     * @param dryRun report what would go, and touch nothing
     * @param force allow removing the version search is currently serving
     */
    public DeleteReport delete(CatalogDetails catalogDetails, List<Integer> versions,
            Set<DeleteLayer> layers, boolean dryRun, boolean force) {
        return delete(catalogDetails, versions, layers, dryRun, force, Scope.VERSIONS);
    }

    /**
     * Every version of a catalog, leaving the catalog's containers in place.
     *
     * <p>
     * The middle of the three, and the one somebody means by "clean it out": the index is emptied
     * but still there, the collections keep their other catalogs, the tables keep everybody else's
     * rows, and the catalog can be crawled again into what it already had.
     */
    public DeleteReport cleanCatalog(CatalogDetails catalogDetails, Set<DeleteLayer> layers,
            boolean dryRun, boolean force) {
        return delete(catalogDetails, versionsOf(catalogDetails), layers, dryRun, force,
                Scope.ALL_VERSIONS);
    }

    /**
     * The catalog's data and the containers that held it.
     *
     * <p>
     * The strongest of the three, and the index is where it differs from cleaning: an index
     * belongs to one catalog, so it is dropped rather than emptied -- immediate, complete, nothing
     * to merge afterwards, and it takes with it any documents belonging to versions nothing else
     * remembers. The other three layers have nothing of their own to drop: a vector collection is
     * shared by every catalog, so is a table, and a directory is emptied by removing its contents.
     * For those two, cleaning and deleting are the same statements.
     *
     * <p>
     * The definition itself is not touched. {@code catalog-delete} removes that, and the two are
     * deliberately separate.
     */
    public DeleteReport deleteCatalog(CatalogDetails catalogDetails, Set<DeleteLayer> layers,
            boolean dryRun, boolean force) {
        return delete(catalogDetails, versionsOf(catalogDetails), layers, dryRun, force,
                Scope.CATALOG);
    }

    /**
     * Which of the three a request is. Only the index tells {@link #ALL_VERSIONS} from
     * {@link #CATALOG}; everywhere else they are the same statements.
     */
    private enum Scope {
        VERSIONS, ALL_VERSIONS, CATALOG
    }

    private DeleteReport delete(CatalogDetails catalogDetails, List<Integer> versions,
            Set<DeleteLayer> layers, boolean dryRun, boolean force, Scope scope) {
        DeleteReport report = new DeleteReport();
        if (!dryRun) {
            // a dry run removes nothing, so it is allowed to report on any version, including the
            // one search is serving -- which is exactly the one someone wants to size up first
            for (int version : versions) {
                guard(catalogDetails, version, force, scope);
            }
        }
        BlobStore blobStore = null;
        try {
            if (layers.contains(DeleteLayer.FILE)) {
                blobStore = outputFactory.getBlobStore();
                BeanLifeCycleUtils.afterPropertiesSet(blobStore);
            }
            // one statement per layer rather than one per layer per version, and for the index
            // the difference between emptying and dropping
            Map<DeleteLayer, Map<Integer, Long>> wholesale =
                    scope == Scope.VERSIONS ? Map.of()
                            : deleteWholesale(catalogDetails, versions, layers, dryRun, scope,
                                    blobStore);
            for (int version : versions) {
                deleteOne(catalogDetails, version, layers, dryRun, blobStore, report, wholesale);
            }
            // Every version is gone, so the catalog is back to defined and never crawled. Leaving
            // the numbers where they were would have the next crawl write v4 into a catalog whose
            // v0 to v3 exist nowhere -- a first crawl reporting itself as the fourth, and a
            // search version pointing at something that was deleted.
            if (!dryRun && scope != Scope.VERSIONS && layers.contains(DeleteLayer.DB)) {
                catalogStore.resetVersions(catalogDetails.getId());
                log.info("Catalog '{}' is empty: back to v0, with nothing to search",
                        catalogDetails.getName());
            }
        } catch (Exception e) {
            throw new WebCrawlerException("Delete failed", e);
        } finally {
            BeanLifeCycleUtils.destroyQuietly(blobStore);
        }
        return report;
    }

    private void guard(CatalogDetails catalogDetails, int version, boolean force, Scope scope) {
        if (catalogDetails.getId().equals(semaphore.getCatalogId())
                && version == catalogDetails.getVersion()) {
            throw new WebCrawlerException(
                    "Version " + version + " is being crawled right now. Stop it first.");
        }
        // Only when versions were named. Emptying a catalog, or deleting it, is a request for all
        // of it -- the version search is serving is not an oversight there, it is the point, and
        // asking for a flag to confirm what was just asked for would refuse both whole-catalog
        // operations on every catalog that has ever finished a crawl. Naming a version is
        // different: that is somebody removing one of several, and taking the served one out from
        // under a search that is answering with it deserves to be said out loud.
        if (scope == Scope.VERSIONS && !force && version == catalogDetails.getSearchVersion()) {
            throw new WebCrawlerException("Version " + version
                    + " is the one search is serving. Pass --force to remove it anyway.");
        }
    }

    /**
     * Removes a whole catalog from every layer asked for, one statement each.
     *
     * <p>
     * Counted per version before the statement rather than after, because afterwards there is
     * nothing to count and the report is still read version by version. Anything belonging to a
     * version nothing else remembers goes with the rest and appears only in the log line.
     *
     * @return per layer, the per version counts, so {@link #deleteOne} knows what to report and
     *         what it no longer has to do. A layer that is missing from the map is one this did
     *         not handle -- a dry run, or a statement that failed -- and is done version by
     *         version as it would have been anyway.
     */
    private Map<DeleteLayer, Map<Integer, Long>> deleteWholesale(CatalogDetails catalogDetails,
            List<Integer> versions, Set<DeleteLayer> layers, boolean dryRun, Scope scope,
            BlobStore blobStore) {
        if (dryRun) {
            return Map.of();
        }
        Map<DeleteLayer, Map<Integer, Long>> done = new LinkedHashMap<>();
        String catalogId = catalogDetails.getId();
        for (DeleteLayer layer : DeleteLayer.values()) {
            if (!layers.contains(layer)) {
                continue;
            }
            try {
                switch (layer) {
                    case VECTOR -> done.put(layer,
                            wholesaleVectors(catalogDetails, versions));
                    case INDEX -> done.put(layer,
                            wholesaleIndex(catalogDetails, versions, scope));
                    case FILE -> {
                        // the catalog's whole tree, versions and all: one prefix rather than one
                        // per version, and it takes any version the database has forgotten
                        Map<Integer, Long> counts = new LinkedHashMap<>();
                        for (int version : versions) {
                            counts.put(version, (long) blobStore.listPrefix(
                                    layoutOf(catalogDetails, version).versionPrefix()).size());
                        }
                        blobStore.deletePrefix(
                                layoutOf(catalogDetails, 0).catalogPrefix());
                        done.put(layer, counts);
                    }
                    case DB -> {
                        Map<Integer, Long> counts = new LinkedHashMap<>();
                        for (int version : versions) {
                            counts.put(version, recordStore.countByCatalog(catalogId, version)
                                    + recordStore.countImagesByCatalog(catalogId, version));
                        }
                        long removed = recordStore.deleteByCatalog(catalogId);
                        if (reportStore != null) {
                            removed += reportStore.deleteByCatalog(catalogId);
                        }
                        log.info("Removed {} row(s) of '{}'", removed, catalogDetails.getName());
                        // one tree per store rather than one per version, and it takes any
                        // version the database has forgotten with it
                        deleteStateOfCatalog(catalogDetails);
                        done.put(layer, counts);
                    }
                }
            } catch (Exception e) {
                // one layer refusing is not a reason to abandon the other three: it is left out of
                // the map and taken version by version below
                log.warn("Removing the whole catalog from {} failed, falling back to one version"
                        + " at a time: {}", layer, e.getMessage());
            }
        }
        return done;
    }

    private Map<Integer, Long> wholesaleIndex(CatalogDetails catalogDetails,
            List<Integer> versions, Scope scope) throws Exception {
        try (IndexAdmin admin = outputFactory.getIndexAdmin()) {
            Map<Integer, Long> counts = new LinkedHashMap<>();
            for (int version : versions) {
                counts.put(version,
                        admin.countByCatalogVersion(catalogDetails.getId() + ":" + version));
            }
            // emptied, or gone: the one place the two wholesale scopes differ
            long removed = scope == Scope.CATALOG
                    ? admin.deleteByCatalog(catalogDetails.getId())
                    : admin.deleteAllVersions(catalogDetails.getId());
            log.info("{} the index of '{}': {} document(s)",
                    scope == Scope.CATALOG ? "Dropped" : "Emptied", catalogDetails.getName(),
                    removed);
            return counts;
        }
    }

    private Map<Integer, Long> wholesaleVectors(CatalogDetails catalogDetails,
            List<Integer> versions) throws Exception {
        VectorStore vectorStore = outputFactory.getVectorStore();
        BeanLifeCycleUtils.afterPropertiesSet(vectorStore);
        try {
            Map<Integer, Long> counts = new LinkedHashMap<>();
            List<String> collections = collections(vectorStore);
            for (int version : versions) {
                long count = 0L;
                for (String collection : collections) {
                    count += vectorStore.count(collection,
                            catalogDetails.getId() + ":" + version);
                }
                counts.put(version, count);
            }
            for (String collection : collections) {
                vectorStore.deleteByCatalog(collection, catalogDetails.getId());
            }
            return counts;
        } finally {
            BeanLifeCycleUtils.destroyQuietly(vectorStore);
        }
    }

    /**
     * The three RocksDB stores a crawl keeps under the system data directory: the frontier, and
     * the url and content dedup filters. All three are laid out as
     * {@code {base}/{catalogId}/v{version}}.
     *
     * <p>
     * Nothing searches them, so they are not a layer somebody chooses. They go with the rows,
     * because that is what they are about: the filters answer "have I fetched this already" and
     * the frontier holds what is left to fetch, and both of those are questions about rows that
     * are being removed. Left behind, they are a directory per version that nothing will ever
     * open again -- and a {@code resume} that found the frontier of a deleted version would start
     * fetching urls whose rows are gone.
     */
    private List<Path> stateDirectories(String catalogId, Integer version) {
        String scope = version == null ? catalogId
                : catalogId + java.io.File.separator + "v" + version;
        return List.of(Paths.get(webCrawlerProperties.getFrontierDirectory(), scope),
                Paths.get(webCrawlerProperties.getDedup().getUrl().getDirectory(), scope),
                Paths.get(webCrawlerProperties.getDedup().getContent().getDirectory(), scope));
    }

    /**
     * @return the bytes those directories held. Reported beside the row count, because it is the
     *         only number in a delete that says how much disk came back from the system half.
     */
    private long deleteState(CatalogDetails catalogDetails, int version, boolean dryRun) {
        return removeState(stateDirectories(catalogDetails.getId(), version), dryRun);
    }

    /** Every version at once, for a delete that is taking the whole catalog. */
    private void deleteStateOfCatalog(CatalogDetails catalogDetails) {
        removeState(stateDirectories(catalogDetails.getId(), null), false);
    }

    private long removeState(List<Path> directories, boolean dryRun) {
        long bytes = 0L;
        for (Path directory : directories) {
            if (!Files.isDirectory(directory)) {
                // a version crawled on another node has none of these here, which is not a
                // failure: every node removes its own
                continue;
            }
            try (var walk = Files.walk(directory)) {
                List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
                for (Path path : paths) {
                    if (Files.isRegularFile(path)) {
                        bytes += Files.size(path);
                    }
                    if (!dryRun) {
                        Files.deleteIfExists(path);
                    }
                }
            } catch (Exception e) {
                log.warn("Could not remove the crawl state at {}: {}", directory, e.getMessage());
            }
        }
        return bytes;
    }

    private FileLayout layoutOf(CatalogDetails catalogDetails, int version) {
        return new FileLayout(catalogDetails.getId(), version,
                outputProperties.getFile().getShardDepth());
    }

    /**
     * @param wholesale what a whole-catalog statement has already removed, per layer and version.
     *        A layer present here is only reported, not repeated.
     */
    private void deleteOne(CatalogDetails catalogDetails, int version, Set<DeleteLayer> layers,
            boolean dryRun, BlobStore blobStore, DeleteReport report,
            Map<DeleteLayer, Map<Integer, Long>> wholesale) {
        String catalogVersion = catalogDetails.getId() + ":" + version;

        // the declaration order of the enum is the deletion order, reverse of the write order
        for (DeleteLayer layer : DeleteLayer.values()) {
            if (!layers.contains(layer)) {
                continue;
            }
            Long already = wholesale.getOrDefault(layer, Map.of()).get(version);
            try {
                if (already != null) {
                    report.add(version, layer, already, 0L, null);
                    continue;
                }
                switch (layer) {
                    case VECTOR -> report.add(version, layer,
                            deleteVectors(catalogVersion, dryRun), 0L, null);
                    case INDEX -> report.add(version, layer,
                            deleteIndex(catalogVersion, dryRun), 0L, null);
                    case FILE -> {
                        String prefix = layoutOf(catalogDetails, version).versionPrefix();
                        long bytes = blobStore.sizeOfPrefix(prefix);
                        long files = dryRun ? blobStore.listPrefix(prefix).size()
                                : blobStore.deletePrefix(prefix);
                        report.add(version, layer, files, bytes, null);
                    }
                    case DB -> {
                        long rows = deleteDb(catalogDetails, version, dryRun);
                        // the frontier and the two dedup filters of this version go with the
                        // rows they were answering questions about
                        report.add(version, layer, rows, deleteState(catalogDetails, version,
                                dryRun), null);
                    }
                }
            } catch (Exception e) {
                log.warn("Deleting {} of version {} failed: {}", layer, version, e.getMessage());
                report.add(version, layer, 0L, 0L, e.getMessage());
            }
        }
    }

    private long deleteVectors(String catalogVersion, boolean dryRun) throws Exception {
        VectorStore vectorStore = outputFactory.getVectorStore();
        BeanLifeCycleUtils.afterPropertiesSet(vectorStore);
        try {
            long removed = 0L;
            for (String collection : collections(vectorStore)) {
                removed += dryRun ? vectorStore.count(collection, catalogVersion)
                        : vectorStore.deleteByCatalogVersion(collection, catalogVersion);
            }
            return removed;
        } finally {
            BeanLifeCycleUtils.destroyQuietly(vectorStore);
        }
    }

    /**
     * Every collection whose name starts with a configured prefix.
     *
     * <p>
     * Asked of the store rather than assumed, because the width of the vectors is part of the
     * collection's name -- {@code greenfinger_text_384} -- and the width comes from the embedding
     * model, which is not loaded when a version is being deleted. Using the bare prefix as the
     * name, which is what this did until 2026-09-02, addressed a collection that has never
     * existed: it deleted nothing, counted nothing, and reported "0", which reads exactly like
     * "there was nothing there".
     */
    private List<String> collections(VectorStore vectorStore) throws Exception {
        OutputProperties.Vector config = outputProperties.getVector();
        List<String> collections = new java.util.ArrayList<>();
        collections.addAll(vectorStore.collectionsMatching(config.getTextCollection()));
        collections.addAll(vectorStore.collectionsMatching(config.getImageCollection()));
        return collections;
    }

    private long deleteIndex(String catalogVersion, boolean dryRun) throws Exception {
        try (IndexAdmin admin = outputFactory.getIndexAdmin()) {
            return dryRun ? admin.countByCatalogVersion(catalogVersion)
                    : admin.deleteByCatalogVersion(catalogVersion);
        }
    }

    private long deleteDb(CatalogDetails catalogDetails, int version, boolean dryRun) {
        if (dryRun) {
            return recordStore.countByCatalog(catalogDetails.getId(), version)
                    + recordStore.countImagesByCatalog(catalogDetails.getId(), version);
        }
        long deleted = recordStore.deleteByCatalogAndVersion(catalogDetails.getId(), version);
        if (reportStore != null) {
            // the report accounts for rows that no longer exist, so it goes with them rather than
            // becoming a row nothing can be joined back to
            deleted += reportStore.deleteByCatalogAndVersion(catalogDetails.getId(), version);
        }
        return deleted;
    }

    /**
     * Every version this catalog has data for, oldest first.
     */
    public List<Integer> versionsOf(CatalogDetails catalogDetails) {
        return recordStore.findVersions(catalogDetails.getId());
    }

    static String describe(Set<DeleteLayer> layers) {
        return layers.stream().map(DeleteLayer::getRepr).sorted()
                .reduce((a, b) -> a + "," + b).orElse("none");
    }

    static boolean isBlank(String value) {
        return StringUtils.isBlank(value);
    }

}
