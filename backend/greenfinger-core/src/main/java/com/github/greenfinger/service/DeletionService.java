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
import java.util.ArrayList;
import java.io.File;

/**
 * Removes one or more versions from any combination of the four stores.
 *
 * <p>
 * In the reverse of the order writing runs in -- vector, index, file, then database -- because
 * the database is where the list of what to delete comes from. Each layer is attempted and
 * reported separately: four stores cannot be emptied atomically, so a half-done run has to be
 * visible and repeatable, which it is because deleting what is already gone does nothing.
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
     * How the other nodes are told to remove their own copy of the two layers that do not
     * replicate themselves. {@link DeletionBroadcast#NONE} in a single process.
     */
    private final DeletionBroadcast broadcast;

    /**
     * @param dryRun report what would go, and touch nothing
     * @param force allow removing the version search is currently serving
     */
    public DeleteReport delete(CatalogDetails catalogDetails, List<Integer> versions,
            Set<DeleteLayer> layers, boolean dryRun, boolean force) {
        return delete(catalogDetails, versions, layers, dryRun, force, Scope.VERSIONS);
    }

    /**
     * Every version of a catalog, leaving its containers in place -- what somebody means by
     * "clean it out": the index is emptied but still there, and the catalog can be crawled again
     * into what it already had.
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
     * The index is where this differs from cleaning: it belongs to one catalog, so it is dropped
     * rather than emptied, taking documents of versions nothing else remembers with it. The other
     * layers are shared, so for them cleaning and deleting are the same statements.
     *
     * <p>
     * The definition itself is not touched; {@code catalog-delete} removes that.
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
            // Back to defined and never crawled: leaving the numbers would have the next crawl
            // write v4 into a catalog whose v0 to v3 exist nowhere.
            if (!dryRun && scope != Scope.VERSIONS && layers.contains(DeleteLayer.DB)) {
                catalogStore.resetVersions(catalogDetails.getId());
                log.info("Catalog '{}' is empty: back to v0, with nothing to search",
                        catalogDetails.getName());
            }
            // Last, and only for a real delete: what is left is the two layers that do not
            // replicate themselves, and their paths differ on every node.
            if (!dryRun) {
                announcePurge(catalogDetails, versions, layers, scope);
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
        // Only when versions were named: emptying or deleting a catalog is a request for all of
        // it, served version included. Naming one version is different -- taking the served one
        // out from under a live search deserves to be said out loud.
        if (scope == Scope.VERSIONS && !force && version == catalogDetails.getSearchVersion()) {
            throw new WebCrawlerException("Version " + version
                    + " is the one search is serving. Pass --force to remove it anyway.");
        }
    }

    /**
     * Removes a whole catalog from every layer asked for, one statement each. Counted per
     * version before the statement, because afterwards there is nothing to count.
     *
     * @return per layer, the per version counts. A layer missing from the map was not handled
     *         here -- a dry run, or a failed statement -- and is done version by version.
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
     * The three RocksDB stores a crawl keeps: the frontier and the two dedup filters, laid out
     * as {@code {base}/{catalogId}/v{version}}.
     *
     * <p>
     * Not a layer somebody chooses -- they go with the rows, because they are about the rows. Left
     * behind, a {@code resume} would find the frontier of a deleted version and fetch urls whose
     * rows are gone.
     */
    private List<Path> stateDirectories(String catalogId, Integer version) {
        String scope = version == null ? catalogId
                : catalogId + File.separator + "v" + version;
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

    /**
     * One announcement per version when versions were named, and one for the whole catalog
     * otherwise -- which is also what tells the other side whether the index is being emptied or
     * dropped.
     */
    private void announcePurge(CatalogDetails catalogDetails, List<Integer> versions,
            Set<DeleteLayer> layers, Scope scope) {
        if (!layers.contains(DeleteLayer.INDEX) && !layers.contains(DeleteLayer.DB)) {
            // the other two layers replicate themselves, so there is nothing left to repeat
            return;
        }
        try {
            if (scope == Scope.VERSIONS) {
                for (int version : versions) {
                    broadcast.purgeElsewhere(catalogDetails.getId(), version, layers, false);
                }
            } else {
                broadcast.purgeElsewhere(catalogDetails.getId(), null, layers,
                        scope == Scope.CATALOG);
            }
        } catch (RuntimeException e) {
            // the delete here succeeded; failing it now would be a lie about what happened
            log.error("Could not ask the other nodes to remove their copy of '{}': {}",
                    catalogDetails.getName(), e.getMessage(), e);
        }
    }

    /**
     * The half of a delete that only removes this node's own copy: the embedded index and the
     * three RocksDB directories. Public because the cluster calls it on the receiving side.
     *
     * <p>
     * Repeating it is safe and expected -- both removals are no-ops when already done, which is
     * what lets the announcement be sent without waiting to hear who acted on it.
     *
     * @param version null for every version of the catalog
     * @return documents removed from this node's index. Zero when the index is shared, because
     *         then there is nothing here that is only here
     */
    public long purgeNodeLocal(String catalogId, Integer version, Set<DeleteLayer> layers,
            boolean dropIndex) {
        long documents = 0L;
        if (layers.contains(DeleteLayer.INDEX) && indexIsPerNode()) {
            try (IndexAdmin admin = outputFactory.getIndexAdmin()) {
                documents = version != null
                        ? admin.deleteByCatalogVersion(catalogId + ":" + version)
                        : dropIndex ? admin.deleteByCatalog(catalogId)
                                : admin.deleteAllVersions(catalogId);
            } catch (Exception e) {
                log.warn("Could not remove catalog {} from this node's index: {}", catalogId,
                        e.getMessage());
            }
        }
        if (layers.contains(DeleteLayer.DB)) {
            removeState(stateDirectories(catalogId, version), false);
        }
        return documents;
    }

    /**
     * Whether this node's index is its own or one every node dials.
     *
     * <p>
     * Only the embedded one has a copy that is only here. Running the same delete against
     * Elasticsearch once per node would remove nothing the first statement had not already
     * removed, and would do it as many times as there are nodes.
     */
    private boolean indexIsPerNode() {
        return "lucene".equalsIgnoreCase(outputProperties.getIndex().getProvider());
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
     * Every collection whose name starts with a configured prefix. Asked of the store rather
     * than assumed: the vector width is part of the name -- {@code greenfinger_text_384} -- and
     * comes from a model that is not loaded during a delete. The bare prefix names nothing, and
     * deleting nothing reports "0", which reads like "there was nothing there".
     */
    private List<String> collections(VectorStore vectorStore) throws Exception {
        OutputProperties.Vector config = outputProperties.getVector();
        List<String> collections = new ArrayList<>();
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

    /**
     * The pages and pictures of one version. Counted the same way whether predicted or
     * performed -- a preview of 293 followed by "removed 397" was both numbers being true and the
     * pair not. The one worth showing is what somebody recognises: pages, not the rows under them.
     */
    private long deleteDb(CatalogDetails catalogDetails, int version, boolean dryRun) {
        long kept = recordStore.countByCatalog(catalogDetails.getId(), version)
                + recordStore.countImagesByCatalog(catalogDetails.getId(), version);
        if (dryRun) {
            return kept;
        }
        long rows = recordStore.deleteByCatalogAndVersion(catalogDetails.getId(), version);
        if (reportStore != null) {
            // the report accounts for rows that no longer exist, so it goes with them rather than
            // becoming a row nothing can be joined back to
            rows += reportStore.deleteByCatalogAndVersion(catalogDetails.getId(), version);
        }
        log.info("Removed v{} of '{}': {} page(s) and picture(s), {} row(s) in all", version,
                catalogDetails.getName(), kept, rows);
        return kept;
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
