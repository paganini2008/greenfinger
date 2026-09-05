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

package com.github.greenfinger.cluster.replication;

import com.chaconneai.spreader.GossipCluster;
import com.github.greenfinger.cluster.Channels;
import com.github.greenfinger.cluster.ClusterProperties;
import com.github.greenfinger.cluster.StoreType;
import com.github.greenfinger.core.ManagedBeanLifeCycle;
import com.github.greenfinger.core.component.dedup.ContentDedupFilter;
import com.github.greenfinger.core.component.dedup.ExistingUrlPathFilter;
import com.github.greenfinger.core.engine.CrawlRegistry;
import com.github.greenfinger.core.engine.WebCrawlerExecutionContext;
import com.github.greenfinger.core.output.BlobStore;
import com.github.greenfinger.core.output.OutputChannel;
import com.github.greenfinger.output.index.LuceneIndexes;
import com.github.greenfinger.output.index.LuceneOutputChannel;
import com.github.greenfinger.output.vector.VectorStore;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * The three channels that copy writes between nodes, and the decision of which of them to open.
 *
 * <p>
 * Opening one that is not needed is not free and not harmless: replicating into a shared MySQL
 * would write every row twice, and replicating into a shared MinIO would upload every image twice.
 * So each is opened only when {@link StoreType} says the store behind it is per node.
 *
 * <p>
 * The dedup channel is always open, because the frontier and the two filters are RocksDB whatever
 * else is configured -- there is no shared variant of them, by design: the frontier is this node's
 * work queue and must not be shared, while the filters must be, which is what this channel is for.
 * 
 * @Description: ClusterReplication
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@Slf4j
@Getter
public class ClusterReplication implements ManagedBeanLifeCycle {

    private final ReplicationChannel dedup;
    private final ReplicationChannel records;
    private final ReplicationChannel blobs;

    /**
     * Index documents and vectors, open only when the search engine is the embedded one. A server
     * every node reaches is shared by definition and needs none of this.
     */
    private final ReplicationChannel search;

    private static final com.fasterxml.jackson.databind.ObjectMapper SEARCH_JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /** Null unless the index is the embedded one. */
    private final LuceneIndexes luceneIndexes;

    /** An <em>undecorated</em> vector store, used only to apply what arrives. */
    private final VectorStore plainVectorStore;

    private final StoreType databaseType;
    private final StoreType blobStoreType;
    private final BlobStore plainBlobStore;

    /**
     * @param rows          how an arriving row is written, or null when the database is shared
     * @param plainBlobStore an <em>undecorated</em> store, used only to apply what arrives.
     *                      Applying through the replicating decorator would send every received
     *                      file straight back out, and the cluster would spend itself echoing.
     *                      Null when the blob store is shared.
     */
    public ClusterReplication(GossipCluster cluster, ClusterProperties properties,
            CrawlRegistry crawlRegistry, StoreType databaseType, StoreType blobStoreType,
            ReplicatedRecordStore.RowWriter rows,
            com.github.greenfinger.core.catalog.CatalogStore plainCatalogStore,
            BlobStore plainBlobStore, LuceneIndexes luceneIndexes, VectorStore plainVectorStore) {
        ClusterProperties.Replication config = properties.getReplication();
        this.databaseType = databaseType;
        this.blobStoreType = blobStoreType;
        this.plainBlobStore = plainBlobStore;

        this.dedup = new ReplicationChannel(cluster, Channels.ROCKSDB, "gf-dedup", config,
                entry -> applyDedup(crawlRegistry, entry));
        this.records = databaseType.replicated() && rows != null
                ? new ReplicationChannel(cluster, Channels.RECORD, "gf-records", config,
                        entry -> applyRecord(entry, rows, plainCatalogStore))
                : null;
        this.blobs = blobStoreType.replicated() && plainBlobStore != null
                ? new ReplicationChannel(cluster, Channels.BLOB, "gf-blobs", config,
                        entry -> ReplicatedBlobStore.apply(entry, plainBlobStore))
                : null;
        this.search = luceneIndexes != null || plainVectorStore != null
                ? new ReplicationChannel(cluster, Channels.SEARCH, "gf-search", config,
                        entry -> applySearch(entry, luceneIndexes, plainVectorStore))
                : null;
        this.luceneIndexes = luceneIndexes;
        this.plainVectorStore = plainVectorStore;
    }

    /**
     * Documents and vectors share a channel because they are two halves of the same answer: a page
     * whose text has arrived and whose vectors have not is a page that full text search finds and
     * semantic search does not, and one consumer applying them in the order they were sent keeps
     * the two from drifting apart for longer than they have to.
     */
    private static void applySearch(ReplicationBatch.Entry entry, LuceneIndexes indexes,
            VectorStore plainVectorStore) {
        if (entry.op() == ReplicatedIndexChannel.OP_DOCUMENT) {
            if (indexes != null) {
                ReplicatedIndexChannel.apply(entry, indexes, SEARCH_JSON);
            }
            return;
        }
        if (plainVectorStore != null) {
            ReplicatedVectorStore.apply(entry, plainVectorStore, SEARCH_JSON);
        }
    }

    /**
     * Catalogs and resource rows share a channel because they share an ordering requirement: a
     * resource row is meaningless without the catalog it belongs to, and one consumer applying
     * them in the order they were sent is what keeps that true.
     */
    private static void applyRecord(ReplicationBatch.Entry entry,
            ReplicatedRecordStore.RowWriter rows,
            com.github.greenfinger.core.catalog.CatalogStore catalogs) {
        if (entry.op() == ReplicatedCatalogStore.OP_CATALOG
                || entry.op() == ReplicatedCatalogStore.OP_CATALOG_DELETE) {
            ReplicatedCatalogStore.apply(entry, catalogs);
            return;
        }
        ReplicatedRecordStore.apply(entry, rows);
    }

    /**
     * The filters belong to a run, so a fingerprint that arrives for a catalog this node is not
     * crawling has nowhere to go. Dropping it is safe -- worst case a url is fetched twice, and
     * every id downstream is derived from the url, so the second write lands on the same row.
     */
    private static void applyDedup(CrawlRegistry crawlRegistry, ReplicationBatch.Entry entry) {
        WebCrawlerExecutionContext context = crawlRegistry.getContext(entry.scope());
        if (context == null) {
            return;
        }
        ReplicatedDedup.apply(entry, unwrap(context.getExistingUrlPathFilter()),
                unwrap(context.getContentDedupFilter()));
    }

    /** Always the delegate: applying through the wrapper would announce what just arrived. */
    private static ExistingUrlPathFilter unwrap(ExistingUrlPathFilter filter) {
        return filter instanceof ReplicatedDedup.Urls wrapper ? wrapper.unwrap() : filter;
    }

    private static ContentDedupFilter unwrap(ContentDedupFilter filter) {
        return filter instanceof ReplicatedDedup.Contents wrapper ? wrapper.unwrap() : filter;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (plainBlobStore != null) {
            plainBlobStore.afterPropertiesSet();
        }
        dedup.start();
        if (records != null) {
            records.start();
        }
        if (search != null) {
            if (plainVectorStore != null) {
                plainVectorStore.afterPropertiesSet();
            }
            search.start();
        }
        if (blobs != null) {
            blobs.start();
        }
        log.info("Replication: dedup on, records {}, blobs {}, search {}",
                records != null ? "on (" + databaseType.name() + " is a file per node)"
                        : "off (" + databaseType.name() + " is shared)",
                blobs != null ? "on (local directory per node)"
                        : "off (" + blobStoreType.name() + " is shared)",
                search != null ? "on (embedded index per node)" : "off (a shared search server)");
    }

    @Override
    public void destroy() throws Exception {
        dedup.stop();
        if (records != null) {
            records.stop();
        }
        if (blobs != null) {
            blobs.stop();
        }
        if (search != null) {
            search.stop();
        }
        if (plainBlobStore != null) {
            plainBlobStore.destroy();
        }
        if (plainVectorStore != null) {
            plainVectorStore.destroy();
        }
    }

    /** Wraps a blob store when the bytes are not shared, and hands it back untouched when they are. */
    public BlobStore decorate(BlobStore blobStore) {
        return blobs != null ? new ReplicatedBlobStore(blobStore, blobs) : blobStore;
    }

    /** The same, for the index channel. */
    public OutputChannel decorate(OutputChannel channel) {
        return search != null && channel instanceof LuceneOutputChannel
                ? new ReplicatedIndexChannel(channel, search)
                : channel;
    }

    /** And for the vector store. */
    public VectorStore decorate(VectorStore vectorStore) {
        return search != null && plainVectorStore != null
                ? new ReplicatedVectorStore(vectorStore, search)
                : vectorStore;
    }

}
