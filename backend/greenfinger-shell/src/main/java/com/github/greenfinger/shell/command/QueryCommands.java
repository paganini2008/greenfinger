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
import org.apache.commons.lang3.StringUtils;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;
import com.github.greenfinger.shell.UsageException;
import com.github.greenfinger.shell.render.Ansi;
import com.github.greenfinger.shell.render.TextTable;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsService;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.output.IndexAdmin;
import com.github.greenfinger.core.output.SearchRequest;
import com.github.greenfinger.core.output.SearchResponse;
import com.github.greenfinger.core.output.SearchResult;
import com.github.greenfinger.core.output.Searcher;
import com.github.greenfinger.core.utils.BeanLifeCycleUtils;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.vector.EmbeddingClient;
import com.github.greenfinger.output.vector.VectorHit;
import com.github.greenfinger.output.vector.VectorStore;
import com.github.greenfinger.service.CatalogAdminService;
import lombok.RequiredArgsConstructor;

/**
 * Searching what was crawled.
 *
 * <p>
 * Search never touches the database: the index carries its own copy of the metadata, so a catalog
 * table that has been emptied does not take the search results with it. Which versions to look at
 * comes from each catalog's {@code search_version}, so a rebuild in progress is invisible until it
 * finishes.
 * 
 * @Description: QueryCommands
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Component
@RequiredArgsConstructor
public class QueryCommands {

    private final CatalogAdminService catalogAdminService;
    private final CatalogDetailsService catalogDetailsService;
    private final OutputProperties outputProperties;
    private final OutputFactory outputFactory;

    /**
     * Words by default, pictures when asked for.
     *
     * <p>
     * There is no {@code --semantic} any more. Two search commands wearing one name, told apart by
     * a flag, meant every result table had to be read twice -- once for what it said and once for
     * which engine had produced it. Text is the index, which is what somebody typing words means;
     * pictures are the one genuinely different question, and they get the flag.
     */
    @Command(name = "search", group = "Search", description = "Search crawled pages")
    public void search(
            @Option(longName = "query", description = "Words to look for") String query,
            @Option(longName = "id",
                    description = "A catalog id to search within; omit for all of them")
                    String id,
            @Option(longName = "size",
                    description = "How many results, 1 or more; default 10") Integer size,
            @Option(longName = "image",
                    description = "true | false; true finds pictures by describing them and shows"
                            + " where they are. Default false") Boolean image)
            throws Exception {
        if (StringUtils.isBlank(query)) {
            throw new UsageException("Give something to search for: search --query=<words>");
        }
        int pageSize = size != null && size > 0 ? size : 10;
        if (Boolean.TRUE.equals(image)) {
            searchImages(query, id, pageSize);
            return;
        }
        List<String> versions = searchableVersions(id);
        if (versions.isEmpty()) {
            print(Ansi.dim("Nothing has finished crawling yet."));
            return;
        }
        Searcher searcher = outputFactory.getSearcher();
        SearchResponse response = searcher.search(SearchRequest.builder().keyword(query)
                .catalogVersions(versions).pageSize(pageSize).build());

        if (response.getResults().isEmpty()) {
            print(Ansi.dim("No matches for '" + query + "'"));
            return;
        }
        TextTable table = TextTable.of("Title", "Url", "Catalog").maxWidth(0, 45).maxWidth(1, 55)
                .title(response.getTotal() + " match(es) for '" + query + "'");
        for (SearchResult result : response.getResults()) {
            table.row(Ansi.cyan(StringUtils.defaultIfBlank(result.getTitle(), "(no title)")),
                    result.getUrl(), result.getCatalog());
        }
        print(table.render());
    }

    /**
     * Pictures found by describing them, which needs an embedding client that does images.
     */
    private void searchImages(String keyword, String catalogId, int size) throws Exception {
        List<String> versions = searchableVersions(catalogId);
        if (versions.isEmpty()) {
            print(Ansi.dim("Nothing has finished crawling yet."));
            return;
        }
        EmbeddingClient embeddingClient = outputFactory.sharedEmbeddingClient();
        try {
            List<VectorHit> hits = outputFactory.getVectorSearcher(embeddingClient)
                    .searchImages(keyword, versions, size);
            renderHits(hits, "Pictures matching '" + keyword + "'", true);
        } catch (UnsupportedOperationException e) {
            // the store cannot do this at all -- a failure, not an empty result, and the shell
            // should hear about it as one
            throw new WebCrawlerException(e.getMessage(), e);
        }
    }

    private void renderHits(List<VectorHit> hits, String title, boolean images) {
        if (hits.isEmpty()) {
            print(Ansi.dim("No matches."));
            return;
        }
        TextTable table = images
                ? TextTable.of("Score", "Image", "From page").maxWidth(1, 46).maxWidth(2, 46)
                : TextTable.of("Score", "Title", "Url").maxWidth(1, 42).maxWidth(2, 46);
        table.title(title);
        for (VectorHit hit : hits) {
            table.row(String.format("%.4f", hit.score()),
                    images ? hit.text("imageFilePath") : Ansi.cyan(hit.text("title")),
                    hit.text("url"));
        }
        print(table.render());
    }

    /**
     * The published version of each catalog, as the {@code <catalogId>:<version>} pairs the index
     * filters on.
     */
    private List<String> searchableVersions(String catalogId) {
        List<Catalog> catalogs = StringUtils.isNotBlank(catalogId)
                ? List.of(catalogAdminService.requireById(catalogId))
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

    /**
     * The full text index: where it is, what it is called, and how many documents each version
     * put in it.
     *
     * <p>
     * One command rather than the two there were. "How many documents" and "which indices exist"
     * are the same question asked at two zoom levels, and answering them separately meant running
     * both and reading them side by side to find out that the count was zero because the crawl had
     * written to a different index than the one being counted.
     */
    @Command(name = "index-info", group = "Search",
            description = "The full text index: where it is, and what is in it")
    public void indexInfo() throws Exception {
        OutputProperties.Index config = outputProperties.getIndex();
        boolean lucene = "lucene".equalsIgnoreCase(config.getProvider());
        try (IndexAdmin admin = outputFactory.getIndexAdmin()) {
            TextTable about = TextTable.of("Setting", "Value").maxWidth(1, 70)
                    .title("Index (" + admin.getName() + ")");
            about.row(lucene ? "Directory" : "Uris", admin.getLocation());
            about.row("Index per catalog", admin.getIndexPrefix() + "-<catalog id>");
            about.row("Analyzer", lucene ? config.getLucene().getAnalyzer() : config.getAnalyzer());
            if (lucene) {
                about.row("Commit every", config.getLucene().getCommitEvery() + " document(s)");
            } else {
                about.row("Shards", config.getNumberOfShards());
                about.row("Replicas", config.getNumberOfReplicas());
                about.row("Batch size", config.getBatchSize());
            }
            print(about.render());

            TextTable documents = TextTable.of("Id", "Catalog", "Version", "Index", "Documents")
                    .rightAlign(4).title("Documents");
            boolean any = false;
            for (Catalog catalog : catalogAdminService.findAll()) {
                CatalogDetails details = catalogDetailsService.loadCatalogDetails(catalog.getId());
                if (!admin.indexExists(catalog.getId())) {
                    continue;
                }
                for (int version = 0; version <= details.getVersion(); version++) {
                    long count = admin.countByCatalogVersion(details.getId() + ":" + version);
                    if (count > 0) {
                        documents.row(Ansi.cyan(catalog.getId()), catalog.getName(),
                                "v" + version, admin.indexOf(catalog.getId()), count);
                        any = true;
                    }
                }
            }
            if (!any) {
                print(Ansi.dim("Nothing has been indexed yet. A crawl whose output-types include"
                        + " 'index' creates an index of its own."));
                return;
            }
            print(documents.render());

            TextTable indices = TextTable.of("Index").title("Every index under the prefix");
            admin.listIndices().forEach(indices::row);
            print(indices.render());
        }
    }

    /**
     * The vector store: which one, which collections, and how many points each version put in
     * them.
     */
    @Command(name = "vector-info", group = "Search",
            description = "The vector store: where it is, and what is in it")
    public void vectorInfo() throws Exception {
        OutputProperties.Vector config = outputProperties.getVector();
        TextTable about = TextTable.of("Setting", "Value").maxWidth(1, 70).title("Vector store");
        about.row("Store", config.getStore());
        about.row("Where", switch (config.getStore().toLowerCase(java.util.Locale.ROOT)) {
            case "lucene" -> config.getLucene().getDirectory();
            case "qdrant" -> config.getQdrant().getUrl();
            default -> config.getWeaviate().getUrl();
        });
        about.row("Text collection", config.getTextCollection());
        about.row("Image collection", config.getImageCollection());
        about.row("Chunk size", config.getChunkSize());
        about.row("Chunk overlap", config.getChunkOverlap());
        about.row("Max chunks per page", config.getMaxChunksPerPage());
        print(about.render());

        VectorStore vectorStore = outputFactory.getVectorStore();
        BeanLifeCycleUtils.afterPropertiesSet(vectorStore);
        try {
            // asked for by prefix, never by the configured name alone: the width of the vectors
            // is appended when they are written -- greenfinger_text_384 -- because it is a
            // property of the embedding model, and counting the bare name counts a collection
            // that has never existed and reports zero
            List<String> collections = new java.util.ArrayList<>();
            collections.addAll(vectorStore.collectionsMatching(config.getTextCollection()));
            collections.addAll(vectorStore.collectionsMatching(config.getImageCollection()));

            TextTable points = TextTable.of("Id", "Catalog", "Version", "Collection", "Points")
                    .rightAlign(4).title("Points");
            boolean any = false;
            for (Catalog catalog : catalogAdminService.findAll()) {
                CatalogDetails details = catalogDetailsService.loadCatalogDetails(catalog.getId());
                for (int version = 0; version <= details.getVersion(); version++) {
                    String catalogVersion = details.getId() + ":" + version;
                    for (String collection : collections) {
                        long count = count(vectorStore, collection, catalogVersion);
                        if (count > 0) {
                            points.row(Ansi.cyan(catalog.getId()), catalog.getName(),
                                    "v" + version, collection, count);
                            any = true;
                        }
                    }
                }
            }
            if (!any) {
                print(Ansi.dim("Nothing has been embedded yet. A crawl whose output-types include"
                        + " 'vector' fills these."));
                return;
            }
            print(points.render());
        } finally {
            BeanLifeCycleUtils.destroyQuietly(vectorStore);
        }
    }

    /**
     * A collection that does not exist is zero points, not a failure: the text collection is
     * created by the first text crawl and the image one by the first crawl that keeps pictures,
     * so one of the two is routinely missing.
     */
    private long count(VectorStore vectorStore, String collection, String catalogVersion) {
        try {
            return vectorStore.count(collection, catalogVersion);
        } catch (Exception e) {
            return 0L;
        }
    }

    private void print(String text) {
        System.out.println(text);
    }

}
