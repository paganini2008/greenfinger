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

package com.github.greenfinger.service.ops;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.lang3.StringUtils;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.WebCrawlerSemaphore;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsService;
import com.github.greenfinger.core.engine.CrawlRegistry;
import com.github.greenfinger.core.engine.WebCrawlerExecutionContext;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.Category;
import com.github.greenfinger.core.model.ContentMode;
import com.github.greenfinger.core.model.ExtractorType;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.output.IndexAdmin;
import com.github.greenfinger.core.utils.BeanLifeCycleUtils;
import com.github.greenfinger.core.utils.UrlUtils;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.core.output.SearchRequest;
import com.github.greenfinger.core.output.SearchResponse;
import com.github.greenfinger.core.output.SearchResult;
import com.github.greenfinger.core.output.Searcher;
import com.github.greenfinger.output.vector.EmbeddingClient;
import com.github.greenfinger.output.vector.VectorHit;
import com.github.greenfinger.output.vector.VectorSearcher;
import com.github.greenfinger.output.vector.VectorStore;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.service.CatalogAdminService;
import com.github.greenfinger.service.CrawlReportService;
import com.github.greenfinger.service.CrawlerLauncher;
import com.github.greenfinger.service.DeleteReport;
import com.github.greenfinger.service.DeletionService;
import com.github.greenfinger.service.FileRestorer;
import com.github.greenfinger.service.ReplayService;
import com.github.greenfinger.service.StoredListing;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The operations, performed here -- what a crawler node runs, whichever face asked. Not a second
 * implementation of anything: every method is a call into the service that owns the work.
 *
 * <p>
 * A crawl is started on a thread of its own and the call returns at once, so it outlives the
 * terminal that asked for it and is watched with {@link #live}, as the page does.
 *
 * @Description: LocalOperations
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class LocalOperations implements GreenfingerOperations {

    private final CatalogAdminService catalogAdminService;
    private final CatalogDetailsService catalogDetailsService;
    private final CrawlReportService crawlReportService;
    private final CrawlRegistry crawlRegistry;
    private final CrawlerLauncher crawlerLauncher;
    private final DeletionService deletionService;
    private final ReplayService replayService;
    private final WebCrawlerSemaphore semaphore;
    private final ResourceRecordStore recordStore;
    private final OutputFactory outputFactory;
    private final OutputProperties outputProperties;
    private final WebCrawlerProperties webCrawlerProperties;

    private final ExecutorService background = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "greenfinger-ops");
        thread.setDaemon(true);
        return thread;
    });

    @PreDestroy
    public void destroy() {
        background.shutdownNow();
    }

    // ---- catalogs ---------------------------------------------------------------------------

    @Override
    public Overview overview() {
        List<CatalogSnapshot> catalogs = new ArrayList<>();
        Map<String, Map<String, Object>> lastRuns = new java.util.LinkedHashMap<>();
        for (Catalog catalog : catalogAdminService.findAll()) {
            CatalogDetails details = catalogDetailsService.loadCatalogDetails(catalog.getId());
            catalogs.add(CatalogSnapshot.of(details));
            catalogAdminService.readLastRun(details)
                    .ifPresent(run -> lastRuns.put(catalog.getId(), run));
        }
        return new Overview(catalogs, crawlRegistry.getRunningCatalogIds(), lastRuns);
    }

    @Override
    public CatalogSnapshot catalog(String idOrName) {
        return CatalogSnapshot.of(StringUtils.isNotBlank(idOrName) ? details(idOrName)
                : catalogDetailsService.loadRunningCatalogDetails());
    }

    @Override
    public List<String> categories() {
        return catalogAdminService.findAllCategories();
    }

    @Override
    public Catalog form(String idOrName) {
        if (StringUtils.isNotBlank(idOrName)) {
            return catalogAdminService.require(idOrName);
        }
        Catalog catalog = new Catalog();
        catalog.setCat(Category.OTHER.getRepr());
        catalog.setPageEncoding(webCrawlerProperties.getDefaultPageEncoding());
        catalog.setExtractorType(ExtractorType.of(webCrawlerProperties.getDefaultExtractor()));
        catalog.setMaxFetchSize(webCrawlerProperties.getDefaultMaxFetchSize());
        catalog.setDepth(webCrawlerProperties.getDefaultMaxFetchDepth());
        catalog.setDuration(webCrawlerProperties.getDefaultFetchDuration());
        catalog.setFetchInterval(webCrawlerProperties.getDefaultFetchInterval());
        catalog.setMaxRetryCount(webCrawlerProperties.getDefaultMaxRetryCount());
        catalog.setImageEnabled(webCrawlerProperties.getImage().isEnabled());
        catalog.setMaxVersions(webCrawlerProperties.getDefaultMaxVersions());
        catalog.setContentMode(ContentMode.TEXT_IMAGE);
        return catalog;
    }

    @Override
    public CatalogSnapshot saveCatalog(Catalog form) {
        Catalog saved = catalogAdminService.save(form);
        return CatalogSnapshot.of(catalogDetailsService.loadCatalogDetails(saved.getId()));
    }

    @Override
    public CatalogSnapshot ensureCatalog(String name, String url) {
        if (StringUtils.isNotBlank(name)) {
            Catalog byName = catalogAdminService.find(name).orElse(null);
            if (byName != null) {
                return snapshotOf(byName);
            }
            if (StringUtils.isBlank(url)) {
                throw new WebCrawlerException("There is no catalog called '" + name + "'."
                        + " Give a url as well to create one.");
            }
            return create(name, url);
        }
        if (StringUtils.isBlank(url)) {
            throw new WebCrawlerException("Name a catalog, or give a url to crawl.");
        }
        // A url is not an identity: nothing stops two catalogs crawling one site with different
        // settings, and picking either of them would be a coin toss. One is the answer, none is a
        // new catalog, and several is a question only the caller can settle.
        List<Catalog> sharing = catalogAdminService.findAll().stream()
                .filter(catalog -> url.equals(catalog.getUrl())).toList();
        if (sharing.size() == 1) {
            return snapshotOf(sharing.get(0));
        }
        if (sharing.size() > 1) {
            throw new WebCrawlerException(sharing.size() + " catalogs crawl " + url + " ("
                    + sharing.stream().map(Catalog::getName).collect(Collectors.joining(", "))
                    + "). Say which one by name.");
        }
        return create(UrlUtils.getDomainName(url), url);
    }

    private CatalogSnapshot create(String name, String url) {
        Catalog form = form(null);
        form.setUrl(url);
        form.setName(name);
        return saveCatalog(form);
    }

    private CatalogSnapshot snapshotOf(Catalog catalog) {
        return CatalogSnapshot.of(catalogDetailsService.loadCatalogDetails(catalog.getId()));
    }

    @Override
    public String deleteCatalog(String idOrName) {
        Catalog catalog = catalogAdminService.require(idOrName);
        String name = catalog.getName();
        catalogAdminService.delete(catalog.getId());
        return name;
    }

    @Override
    public Versions versions(String idOrName) {
        Catalog catalog = catalogAdminService.require(idOrName);
        return new Versions(catalog.getId(), catalog.getName(),
                crawlReportService.versions(catalog.getId()));
    }

    @Override
    public Map<String, Object> report(String idOrName, Integer version) {
        Catalog catalog = catalogAdminService.require(idOrName);
        return crawlReportService.stored(catalog.getId(), version)
                .orElseThrow(() -> new WebCrawlerException(version != null
                        ? "No report for v" + version + " of '" + catalog.getName() + "'."
                                + " Run 'versions --id=" + catalog.getId() + "' to see which"
                                + " versions there are."
                        : "'" + catalog.getName() + "' has no report yet. One is written when a"
                                + " crawl finishes."));
    }

    // ---- crawling ---------------------------------------------------------------------------

    @Override
    public String start(StartAsk ask) {
        Catalog catalog = catalogAdminService.require(ask.idOrName());
        String verb = StringUtils.defaultIfBlank(ask.verb(), "crawl");
        // Asked here as well as in the launcher. The launcher is the authority, but it runs on a
        // thread this method has already returned from, so without this the caller is told
        // "started" and the crawl is refused a moment later where nobody is looking.
        if (!semaphore.available(catalog.getId())) {
            throw new WebCrawlerException("A crawl is already running. One at a time, here and on"
                    + " every other node: two would divide the bandwidth rather than double it."
                    + " Stop it with 'pause', or wait for it.");
        }
        background.submit(() -> {
            try {
                switch (verb) {
                    case "rebuild" -> crawlerLauncher.rebuild(catalog.getId(), ask.threads(), null);
                    case "merge" -> crawlerLauncher.update(catalog.getId(), ask.from(), true,
                            ask.threads(), null);
                    case "update", "resume" -> crawlerLauncher.update(catalog.getId(), ask.from(),
                            false, ask.threads(), null);
                    default -> crawlerLauncher.crawl(catalog.getId(), ask.threads(), null);
                }
            } catch (Exception e) {
                log.error("{} of '{}' failed: {}", verb, catalog.getName(), e.getMessage(), e);
            }
        });
        return verb + " of '" + catalog.getName() + "' started";
    }

    @Override
    public boolean interrupt(String idOrName) {
        return crawlRegistry.interrupt(catalogAdminService.require(idOrName).getId());
    }

    /**
     * What is queued is this node's own frontier: a url is dispatched to exactly one node, so the
     * number is a share rather than the whole. The counters beside it are the cluster's.
     */
    @Override
    public Live live(String catalogId, boolean perNode) {
        WebCrawlerExecutionContext context = crawlRegistry.getContext(catalogId);
        if (context == null) {
            return null;
        }
        context.getGlobalStateManager().flush();
        return new Live(DashboardSnapshot.of(context.getGlobalStateManager().getDashboard()),
                remaining(context),
                perNode ? context.getGlobalStateManager().perNodeCounters() : null,
                !crawlRegistry.isRunning(catalogId));
    }

    private long remaining(WebCrawlerExecutionContext context) {
        try {
            return context.getCrawlFrontier() != null ? context.getCrawlFrontier().remaining()
                    : -1L;
        } catch (Exception e) {
            // a store closed under a crawl that ended mid-call. A queue length is worth a dash
            return -1L;
        }
    }

    /**
     * Three operations, not one with three spellings: naming versions removes those versions,
     * {@code all} empties the catalog and leaves its index standing, and {@code purge} takes the
     * index too.
     */
    @Override
    public List<DeleteReport.Line> delete(DeleteAsk ask) {
        CatalogDetails details = details(ask.idOrName());
        boolean everyVersion =
                ask.version() == null && ask.keepLatest() == null && (ask.purge() || ask.all());
        DeleteReport report;
        if (everyVersion && ask.purge()) {
            report = deletionService.deleteCatalog(details, ask.layers(), ask.dryRun(),
                    ask.force());
        } else if (everyVersion) {
            report = deletionService.cleanCatalog(details, ask.layers(), ask.dryRun(), ask.force());
        } else {
            report = deletionService.delete(details, targets(details, ask), ask.layers(),
                    ask.dryRun(), ask.force());
        }
        return report.getLines();
    }

    private List<Integer> targets(CatalogDetails details, DeleteAsk ask) {
        if (ask.version() != null) {
            return List.of(ask.version());
        }
        if (ask.keepLatest() == null) {
            throw new WebCrawlerException(
                    "Say what to remove: --version, --keep-latest, --all or --purge");
        }
        List<Integer> present = deletionService.versionsOf(details);
        int drop = Math.max(0, present.size() - ask.keepLatest());
        return present.stream().sorted().limit(drop).toList();
    }

    @Override
    public ReplayAnswer replay(ReplayAsk ask) {
        CatalogDetails details = details(ask.idOrName());
        int version = ask.version() != null ? ask.version() : details.getVersion();
        Set<OutputType> layers = ask.layers();
        try {
            long replayed = replayService.replay(details.getId(), version, layers);
            FileRestorer.Result files = replayService.getLastFileRestore();
            return new ReplayAnswer(replayed, version,
                    files == null ? null
                            : new FileLines(files.pages(), files.images(), files.intact(),
                                    files.unreachable(), files.changed()));
        } catch (WebCrawlerException e) {
            throw e;
        } catch (Exception e) {
            throw new WebCrawlerException("Replay of v" + version + " failed: " + e.getMessage(),
                    e);
        }
    }

    // ---- search -----------------------------------------------------------------------------

    @Override
    public SearchAnswer search(SearchAsk ask) {
        String mode = StringUtils.defaultIfBlank(ask.mode(), "words");
        int size = ask.size() != null && ask.size() > 0 ? ask.size() : 10;
        List<String> versions = searchableVersions(ask.idOrName());
        if (versions.isEmpty()) {
            return null;
        }
        try {
            if (StringUtils.isBlank(ask.query())) {
                return listEverything(mode, versions, size);
            }
            return switch (mode) {
                case "pictures" -> hits(
                        vectorSearcher().searchImages(ask.query(), versions, size),
                        "Pictures matching '" + ask.query() + "'", true);
                case "meaning" -> hits(
                        vectorSearcher().searchText(ask.query(), versions, size, true),
                        "Pages about '" + ask.query() + "' (meaning)", false);
                default -> words(ask.query(), versions, size);
            };
        } catch (UnsupportedOperationException e) {
            // the store cannot do this at all -- a failure, not an empty result
            throw new WebCrawlerException(e.getMessage(), e);
        } catch (WebCrawlerException e) {
            throw e;
        } catch (Exception e) {
            throw new WebCrawlerException("The search failed: " + e.getMessage(), e);
        }
    }

    private SearchAnswer words(String query, List<String> versions, int size) throws Exception {
        Searcher searcher = outputFactory.getSearcher();
        SearchResponse response = searcher.search(SearchRequest.builder().keyword(query)
                .catalogVersions(versions).pageSize(size).build());
        List<Hit> hits = new ArrayList<>();
        for (SearchResult result : response.getResults()) {
            hits.add(new Hit(0d, StringUtils.defaultIfBlank(result.getTitle(), "(no title)"),
                    result.getUrl()));
        }
        return new SearchAnswer(response.getTotal() + " match(es) for '" + query + "'", false,
                false, response.getTotal(), hits);
    }

    private SearchAnswer listEverything(String mode, List<String> versions, int size) {
        StoredListing listing = new StoredListing(recordStore, catalogAdminService);
        if ("pictures".equals(mode)) {
            return hits(listing.images(versions, size, 0), "Every picture kept", true);
        }
        return hits(listing.pages(versions, size, 0), "Every page kept", false);
    }

    /**
     * The score is dropped when nothing was compared: a listing carries zero on every row -- it is
     * a table read in order, not a ranking -- and a column of 0.0000 invites somebody to wonder
     * what they did wrong.
     */
    private SearchAnswer hits(List<VectorHit> found, String title, boolean images) {
        boolean ranked = found.stream().anyMatch(hit -> hit.score() > 0d);
        List<Hit> rows = new ArrayList<>();
        for (VectorHit hit : found) {
            rows.add(new Hit(hit.score(), images ? hit.text("imageFilePath") : hit.text("title"),
                    hit.text("url")));
        }
        return new SearchAnswer(title, ranked, images, rows.size(), rows);
    }

    private VectorSearcher vectorSearcher() throws Exception {
        EmbeddingClient embeddingClient = outputFactory.sharedEmbeddingClient();
        return outputFactory.getVectorSearcher(embeddingClient);
    }

    /** The published version of each catalog, as the pairs the index filters on. */
    private List<String> searchableVersions(String catalogId) {
        List<Catalog> catalogs = StringUtils.isNotBlank(catalogId)
                ? List.of(catalogAdminService.require(catalogId))
                : catalogAdminService.findAll();
        List<String> versions = new ArrayList<>();
        for (Catalog catalog : catalogs) {
            CatalogDetails details = catalogDetailsService.loadCatalogDetails(catalog.getId());
            if (details.getSearchVersion() >= 0) {
                versions.add(details.getId() + ":" + details.getSearchVersion());
            }
        }
        return versions;
    }

    @Override
    public Info indexInfo() {
        OutputProperties.Index config = outputProperties.getIndex();
        boolean lucene = "lucene".equalsIgnoreCase(config.getProvider());
        List<InfoRow> about = new ArrayList<>();
        List<CountRow> counts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try (IndexAdmin admin = outputFactory.getIndexAdmin()) {
            about.add(new InfoRow("Index", admin.getName()));
            about.add(new InfoRow(lucene ? "Directory" : "Uris", admin.getLocation()));
            about.add(new InfoRow("Index per catalog", admin.getIndexPrefix() + "-<catalog id>"));
            about.add(new InfoRow("Analyzer",
                    lucene ? config.getLucene().getAnalyzer() : config.getAnalyzer()));
            if (lucene) {
                about.add(new InfoRow("Commit every",
                        config.getLucene().getCommitEvery() + " document(s)"));
            } else {
                about.add(new InfoRow("Shards", String.valueOf(config.getNumberOfShards())));
                about.add(new InfoRow("Replicas", String.valueOf(config.getNumberOfReplicas())));
                about.add(new InfoRow("Batch size", String.valueOf(config.getBatchSize())));
            }
            for (Catalog catalog : catalogAdminService.findAll()) {
                CatalogDetails details = catalogDetailsService.loadCatalogDetails(catalog.getId());
                if (!admin.indexExists(catalog.getId())) {
                    continue;
                }
                for (int version = 0; version <= details.getVersion(); version++) {
                    long count = admin.countByCatalogVersion(details.getId() + ":" + version);
                    if (count > 0) {
                        counts.add(new CountRow(catalog.getId(), catalog.getName(), version,
                                admin.indexOf(catalog.getId()), count));
                    }
                }
            }
            admin.listIndices().forEach(names::add);
        } catch (Exception e) {
            throw new WebCrawlerException("Could not read the index: " + e.getMessage(), e);
        }
        return new Info(about, counts, names);
    }

    @Override
    public Info vectorInfo() {
        OutputProperties.Vector config = outputProperties.getVector();
        List<InfoRow> about = new ArrayList<>();
        about.add(new InfoRow("Store", config.getStore()));
        about.add(new InfoRow("Where", switch (config.getStore().toLowerCase(Locale.ROOT)) {
            case "lucene" -> config.getLucene().getDirectory();
            case "qdrant" -> config.getQdrant().getUrl();
            default -> config.getWeaviate().getUrl();
        }));
        about.add(new InfoRow("Text collection", config.getTextCollection()));
        about.add(new InfoRow("Image collection", config.getImageCollection()));
        about.add(new InfoRow("Chunk size", String.valueOf(config.getChunkSize())));
        about.add(new InfoRow("Chunk overlap", String.valueOf(config.getChunkOverlap())));
        about.add(new InfoRow("Max chunks per page",
                String.valueOf(config.getMaxChunksPerPage())));

        List<CountRow> counts = new ArrayList<>();
        VectorStore vectorStore = outputFactory.getVectorStore();
        try {
            BeanLifeCycleUtils.afterPropertiesSet(vectorStore);
            // by prefix, never by the configured name alone: the width is appended when the
            // vectors are written, because it is a property of the model
            List<String> collections = new ArrayList<>();
            collections.addAll(vectorStore.collectionsMatching(config.getTextCollection()));
            collections.addAll(vectorStore.collectionsMatching(config.getImageCollection()));
            for (Catalog catalog : catalogAdminService.findAll()) {
                CatalogDetails details = catalogDetailsService.loadCatalogDetails(catalog.getId());
                for (int version = 0; version <= details.getVersion(); version++) {
                    String catalogVersion = details.getId() + ":" + version;
                    for (String collection : collections) {
                        long count = count(vectorStore, collection, catalogVersion);
                        if (count > 0) {
                            counts.add(new CountRow(catalog.getId(), catalog.getName(), version,
                                    collection, count));
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new WebCrawlerException("Could not read the vector store: " + e.getMessage(), e);
        } finally {
            BeanLifeCycleUtils.destroyQuietly(vectorStore);
        }
        return new Info(about, counts, List.of());
    }

    /**
     * A collection that does not exist is zero points, not a failure: the text collection is made
     * by the first text crawl and the image one by the first crawl that keeps pictures, so one of
     * the two is routinely missing.
     */
    private long count(VectorStore vectorStore, String collection, String catalogVersion) {
        try {
            return vectorStore.count(collection, catalogVersion);
        } catch (Exception e) {
            return 0L;
        }
    }

    private CatalogDetails details(String idOrName) {
        return catalogDetailsService
                .loadCatalogDetails(catalogAdminService.require(idOrName).getId());
    }

}
