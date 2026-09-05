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

package com.github.greenfinger.output;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.DisposableBean;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.output.BlobStore;
import com.github.greenfinger.core.output.FileLayout;
import com.github.greenfinger.core.output.OutputChannel;
import com.github.greenfinger.output.blob.LocalBlobStore;
import com.github.greenfinger.output.blob.MinioBlobStore;
import com.github.greenfinger.core.output.IndexAdmin;
import com.github.greenfinger.core.output.Searcher;
import com.github.greenfinger.output.index.ElasticsearchIndexAdmin;
import com.github.greenfinger.output.index.ElasticsearchOutputChannel;
import com.github.greenfinger.output.index.ElasticsearchSearcher;
import com.github.greenfinger.output.index.LuceneAnalyzers;
import com.github.greenfinger.output.index.LuceneIndexAdmin;
import com.github.greenfinger.output.index.LuceneIndexes;
import com.github.greenfinger.output.index.LuceneOutputChannel;
import com.github.greenfinger.output.index.LuceneSearcher;
import com.github.greenfinger.output.vector.LuceneVectorStore;
import com.github.greenfinger.output.blob.FileOutputChannel;
import com.github.greenfinger.output.vector.EmbeddingClient;
import com.github.greenfinger.output.vector.EmbeddingProperties;
import com.github.greenfinger.output.vector.OllamaEmbeddingClient;
import com.github.greenfinger.output.vector.OpenAiEmbeddingClient;
import com.github.greenfinger.output.vector.ElasticsearchVectorStore;
import com.github.greenfinger.output.vector.QdrantVectorStore;
import com.github.greenfinger.output.vector.VectorOutputChannel;
import com.github.greenfinger.output.vector.VectorStore;
import com.github.greenfinger.output.vector.WeaviateVectorStore;
import com.github.greenfinger.core.utils.BeanLifeCycleUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Assembles the outputs a catalog asked for.
 *
 * <p>
 * They stack rather than exclude one another, and the file layer is always present: the database
 * keeps metadata only, so without the files there would be nothing for the index or the vectors to
 * be rebuilt from. Where the files go -- local disk or MinIO -- is the one either/or here.
 * 
 * @Description: OutputFactory
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class OutputFactory implements DisposableBean {

    private final OutputProperties outputProperties;
    private final EmbeddingProperties embeddingProperties;

    /**
     * The one embedding client this process uses, built on first demand and kept.
     *
     * <p>
     * The local provider builds three onnx sessions off disk and that costs seconds, so it used to
     * be paid by every crawl, every replay and every semantic search -- and paid again by the
     * startup warm-up, which loaded the models only to close them, leaving the next crawl to load
     * them all over again. Held now, which removes those seconds from the start of a run and, in a
     * cluster, from the window in which a node that has been told to join a crawl is not yet able
     * to accept urls for it.
     */
    private volatile EmbeddingClient shared;

    /**
     * The open Lucene indices of this process, when Lucene is the index provider.
     *
     * <p>
     * One object, for the same reason the embedding client is one: Lucene locks a directory for as
     * long as a writer is open, so the crawl writing an index, the search reading it and the
     * delete removing a version from it are not three independent users of a file -- they are
     * three callers that have to be handed the same writer.
     */
    private volatile LuceneIndexes luceneIndexes;

    /**
     * The file store, which is also what the later layers read their input back from.
     */
    public BlobStore getBlobStore() {
        OutputProperties.File config = outputProperties.getFile();
        return switch (config.getTarget().toLowerCase(Locale.ROOT)) {
            case "minio" -> new MinioBlobStore(config.getMinio());
            case "local" -> new LocalBlobStore(Paths.get(config.getDirectory()));
            default -> throw new UnsupportedOperationException(
                    "Unknown file target: " + config.getTarget());
        };
    }

    public FileLayout getFileLayout(CatalogDetails catalogDetails) {
        return FileLayout.of(catalogDetails, outputProperties.getFile().getShardDepth());
    }

    /** For a layout of a version other than the catalog's current one. */
    public int shardDepth() {
        return outputProperties.getFile().getShardDepth();
    }

    /**
     * @param embeddingClient supplied by the caller so its models are loaded once per run rather
     *        than once per channel
     */
    public CompositeOutputChannel getOutputChannel(CatalogDetails catalogDetails,
            BlobStore blobStore, EmbeddingClient embeddingClient) {
        List<OutputChannel> channels = new ArrayList<>();
        channels.add(new FileOutputChannel(blobStore, getFileLayout(catalogDetails)));

        if (catalogDetails.hasOutput(OutputType.INDEX)) {
            channels.add(indexChannel());
        }
        if (catalogDetails.hasOutput(OutputType.VECTOR)) {
            channels.add(new VectorOutputChannel(outputProperties.getVector(), embeddingClient,
                    getVectorStore(), blobStore));
        }
        return new CompositeOutputChannel(channels, blobStore);
    }

    /**
     * The index half of the output channel. Protected because the cluster edition wraps it: an
     * embedded index is one copy per node and its writes have to reach the others.
     */
    protected OutputChannel indexChannel() {
        OutputProperties.Index config = outputProperties.getIndex();
        return switch (config.getProvider().toLowerCase(Locale.ROOT)) {
            case "lucene" -> new LuceneOutputChannel(config, sharedLuceneIndexes());
            case "elasticsearch", "es" -> new ElasticsearchOutputChannel(config);
            default -> throw new UnsupportedOperationException(
                    "Unknown index provider: " + config.getProvider() + ". Use lucene or"
                            + " elasticsearch.");
        };
    }

    /**
     * Searching whichever index the crawl wrote.
     */
    public Searcher getSearcher() {
        OutputProperties.Index config = outputProperties.getIndex();
        return switch (config.getProvider().toLowerCase(Locale.ROOT)) {
            case "lucene" -> new LuceneSearcher(config, sharedLuceneIndexes());
            case "elasticsearch", "es" -> new ElasticsearchSearcher(config);
            default -> throw new UnsupportedOperationException(
                    "Unknown index provider: " + config.getProvider() + ". Use lucene or"
                            + " elasticsearch.");
        };
    }

    /**
     * Counting, deleting and listing indices. Closing what this returns is a no-op for both
     * providers -- neither owns anything the caller has to give back.
     */
    public IndexAdmin getIndexAdmin() {
        OutputProperties.Index config = outputProperties.getIndex();
        return switch (config.getProvider().toLowerCase(Locale.ROOT)) {
            case "lucene" -> new LuceneIndexAdmin(config, sharedLuceneIndexes());
            case "elasticsearch", "es" -> new ElasticsearchIndexAdmin(config);
            default -> throw new UnsupportedOperationException(
                    "Unknown index provider: " + config.getProvider() + ". Use lucene or"
                            + " elasticsearch.");
        };
    }

    /**
     * The process's Lucene indices, opened on first demand.
     */
    public LuceneIndexes sharedLuceneIndexes() {
        LuceneIndexes indexes = luceneIndexes;
        if (indexes == null) {
            synchronized (this) {
                indexes = luceneIndexes;
                if (indexes == null) {
                    OutputProperties.Index.Lucene config = outputProperties.getIndex().getLucene();
                    indexes = LuceneIndexes.shared(config.getDirectory(),
                            LuceneAnalyzers.of(config.getAnalyzer()));
                    log.info("Lucene index at {}, analyzer {}", indexes.getRoot(),
                            config.getAnalyzer());
                    luceneIndexes = indexes;
                }
            }
        }
        return indexes;
    }

    /**
     * The shared client, initialised once. What every caller that is about to embed something
     * should use.
     */
    public EmbeddingClient sharedEmbeddingClient() throws Exception {
        EmbeddingClient client = shared;
        if (client == null) {
            synchronized (this) {
                client = shared;
                if (client == null) {
                    client = getEmbeddingClient();
                    long start = System.currentTimeMillis();
                    BeanLifeCycleUtils.afterPropertiesSet(client);
                    log.info("Embedding client '{}' ready in {} ms, {} dimensions",
                            client.getName(), System.currentTimeMillis() - start,
                            client.textDimensions());
                    shared = client;
                }
            }
        }
        return client;
    }

    @Override
    public void destroy() {
        EmbeddingClient client = shared;
        shared = null;
        BeanLifeCycleUtils.destroyQuietly(client);

        luceneIndexes = null;
        // committing on the way out: a crawl interrupted between commits has documents in the
        // writer's buffer, and they belong in the index rather than in the next run's report of
        // how much was lost. This application's two roots and no others -- a second application
        // in the same jvm has its own, and closing those would leave it writing to shut indices
        LuceneIndexes.closeShared(outputProperties.getIndex().getLucene().getDirectory());
        LuceneIndexes.closeShared(outputProperties.getVector().getLucene().getDirectory());
    }

    /**
     * A new, uninitialised client. Only for callers that genuinely want their own -- deciding
     * which provider is configured, or a test. Everything on a hot path wants
     * {@link #sharedEmbeddingClient()}.
     */
    public EmbeddingClient getEmbeddingClient() {
        return switch (embeddingProperties.getProvider().toLowerCase(Locale.ROOT)) {
            case "local" -> new com.github.greenfinger.output.vector.LocalEmbeddingClient(
                    embeddingProperties);
            case "ollama" -> new OllamaEmbeddingClient(embeddingProperties);
            case "openai" -> new OpenAiEmbeddingClient(embeddingProperties);
            default -> throw new UnsupportedOperationException(
                    "Unknown embedding provider: " + embeddingProperties.getProvider());
        };
    }

    public com.github.greenfinger.output.vector.VectorSearcher getVectorSearcher(
            EmbeddingClient embeddingClient) {
        return new com.github.greenfinger.output.vector.VectorSearcher(
                outputProperties.getVector(), embeddingClient, getVectorStore());
    }

    public VectorStore getVectorStore() {
        OutputProperties.Vector config = outputProperties.getVector();
        return switch (config.getStore().toLowerCase(Locale.ROOT)) {
            case "lucene" -> new LuceneVectorStore(config.getLucene());
            case "elasticsearch", "es" ->
                new ElasticsearchVectorStore(config.getElasticsearch());
            case "qdrant" -> new QdrantVectorStore(config.getQdrant());
            case "weaviate" -> new WeaviateVectorStore(config.getWeaviate());
            default -> throw new UnsupportedOperationException(
                    "Unknown vector store: " + config.getStore()
                            + ". Use lucene, elasticsearch, qdrant or weaviate.");
        };
    }

}
