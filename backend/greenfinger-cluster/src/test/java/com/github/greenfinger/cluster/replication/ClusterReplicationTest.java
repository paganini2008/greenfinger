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

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.github.greenfinger.cluster.ClusterProperties;
import com.github.greenfinger.cluster.StoreType;
import com.github.greenfinger.cluster.support.TestCluster;
import com.github.greenfinger.cluster.support.TestRun;
import com.github.greenfinger.core.engine.CrawlRegistry;
import com.github.greenfinger.core.output.BlobStore;
import com.github.greenfinger.core.output.OutputChannel;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.index.LuceneAnalyzers;
import com.github.greenfinger.output.index.LuceneIndexes;
import com.github.greenfinger.output.index.LuceneOutputChannel;
import com.github.greenfinger.output.vector.LuceneVectorStore;
import com.github.greenfinger.output.vector.VectorStore;
import com.github.greenfinger.output.blob.LocalBlobStore;

/**
 * Which channels are opened, and what happens to what arrives on them.
 *
 * <p>
 * Opening a channel that is not needed is not harmless: replicating into a shared MySQL writes
 * every row twice and replicating into a shared MinIO uploads every image twice. Which is why the
 * decision is made once, from the store types, and why it is worth a test of its own.
 * 
 * @Description: ClusterReplicationTest
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
class ClusterReplicationTest {

    private TestCluster cluster;
    private CrawlRegistry registry;

    @BeforeEach
    void setUp() {
        cluster = TestCluster.start(1);
        registry = new CrawlRegistry();
    }

    @AfterEach
    void tearDown() {
        cluster.close();
    }

    @Test
    @DisplayName("a file backed database and a local directory: both channels open")
    void everythingPerNodeIsReplicated(@TempDir Path root) throws Exception {
        BlobStore blobStore = store(root);
        ClusterReplication replication = new ClusterReplication(cluster.node(0).cluster(),
                new ClusterProperties(), registry, StoreType.H2, StoreType.LOCAL_FILE,
                rows(), null, blobStore, null, null);
        replication.afterPropertiesSet();
        try {
            assertThat(replication.getDedup()).isNotNull();
            assertThat(replication.getRecords()).isNotNull();
            assertThat(replication.getBlobs()).isNotNull();
            // and the blob store comes back wrapped
            assertThat(replication.decorate(blobStore)).isInstanceOf(ReplicatedBlobStore.class);
        } finally {
            replication.destroy();
        }
    }

    @Test
    @DisplayName("a shared database and MinIO: neither channel opens, and the store is untouched")
    void nothingSharedIsReplicated(@TempDir Path root) throws Exception {
        BlobStore blobStore = store(root);
        ClusterReplication replication = new ClusterReplication(cluster.node(0).cluster(),
                new ClusterProperties(), registry, StoreType.POSTGRESQL, StoreType.MINIO, null,
                null, null, null, null);
        replication.afterPropertiesSet();
        try {
            assertThat(replication.getRecords()).isNull();
            assertThat(replication.getBlobs()).isNull();
            // the dedup filters are RocksDB whatever else is configured, so that one always opens
            assertThat(replication.getDedup()).isNotNull();
            assertThat(replication.decorate(blobStore)).isSameAs(blobStore);
        } finally {
            replication.destroy();
        }
    }

    @Test
    @DisplayName("a fingerprint for a crawl this node is not running is dropped, not applied"
            + " somewhere else")
    void dedupNeedsTheRunItBelongsTo() throws Exception {
        ClusterReplication replication = new ClusterReplication(cluster.node(0).cluster(),
                new ClusterProperties(), registry, StoreType.MYSQL, StoreType.MINIO, null, null, null, null, null);
        replication.afterPropertiesSet();
        try {
            // nothing registered: this must not throw, and must not reach another catalog
            replication.getDedup().replicate(
                    ReplicationBatch.Entry.of(ReplicatedDedup.OP_URL, "unknown", "https://x/a"));

            TestRun run = new TestRun("cat-1", "books");
            registry.register("cat-1", run);
            assertThat(run.plainUrls().knows("https://x/a")).isFalse();
        } finally {
            replication.destroy();
        }
    }

    @Test
    void storeTypesAreReportedForTheStartupLine() throws Exception {
        ClusterReplication replication = new ClusterReplication(cluster.node(0).cluster(),
                new ClusterProperties(), registry, StoreType.SQLITE, StoreType.MINIO, rows(),
                null, null, null, null);
        replication.afterPropertiesSet();
        try {
            assertThat(replication.getDatabaseType()).isEqualTo(StoreType.SQLITE);
            assertThat(replication.getBlobStoreType()).isEqualTo(StoreType.MINIO);
        } finally {
            replication.destroy();
        }
    }

    private static BlobStore store(Path root) throws Exception {
        LocalBlobStore blobStore = new LocalBlobStore(root);
        blobStore.afterPropertiesSet();
        return blobStore;
    }

    private static ReplicatedRecordStore.RowWriter rows() {
        return new ReplicatedRecordStore.RowWriter() {

            @Override
            public void resource(com.github.greenfinger.core.model.Resource resource) {
                // the routing is what ClusterReplication is asked about, not the writing
            }

            @Override
            public void image(com.github.greenfinger.core.model.Image image) {
                // as above
            }

            @Override
            public void reference(com.github.greenfinger.core.model.ResourceImage reference) {
                // as above
            }

            @Override
            public void deleteCatalog(String catalogId) {}

            @Override
            public void deleteVersion(String catalogId, int version) {
                // as above
            }
        };
    }


    @Test
    @DisplayName("the embedded search engines are per node, so their channel opens too")
    void theEmbeddedIndexIsReplicated(@TempDir Path root) throws Exception {
        LuceneIndexes indexes = LuceneIndexes.shared(root.resolve("lucene").toString(),
                LuceneAnalyzers.of("standard"));
        OutputProperties.Vector.Lucene vectorConfig = new OutputProperties.Vector.Lucene();
        vectorConfig.setDirectory(root.resolve("lucene-vector").toString());
        LuceneVectorStore vectors = new LuceneVectorStore(vectorConfig);

        ClusterReplication replication = new ClusterReplication(cluster.node(0).cluster(),
                new ClusterProperties(), registry, StoreType.POSTGRESQL, StoreType.MINIO, null,
                null, null, indexes, vectors);
        replication.afterPropertiesSet();
        try {
            assertThat(replication.getSearch()).isNotNull();

            OutputProperties.Index indexConfig = new OutputProperties.Index();
            LuceneOutputChannel plain = new LuceneOutputChannel(indexConfig, indexes);
            assertThat(replication.decorate((OutputChannel) plain))
                    .isInstanceOf(ReplicatedIndexChannel.class);
            assertThat(replication.decorate((VectorStore) vectors))
                    .isInstanceOf(ReplicatedVectorStore.class);

            // an arriving document and an arriving vector go to the right half of the channel
            replication.getSearch().replicate(ReplicationBatch.Entry.of(
                    ReplicatedIndexChannel.OP_DOCUMENT, "cat-1", "greenfinger-cat-1",
                    "{\"id\":\"res-1\",\"catalogVersion\":\"cat-1:0\"}".getBytes()));
        } finally {
            replication.destroy();
            LuceneIndexes.closeShared(root.resolve("lucene").toString());
            LuceneIndexes.closeShared(root.resolve("lucene-vector").toString());
        }
    }

    @Test
    @DisplayName("a shared search server needs none of it, and the channel stays shut")
    void aSharedSearchServerIsNotReplicated(@TempDir Path root) throws Exception {
        ClusterReplication replication = new ClusterReplication(cluster.node(0).cluster(),
                new ClusterProperties(), registry, StoreType.POSTGRESQL, StoreType.MINIO, null,
                null, null, null, null);
        replication.afterPropertiesSet();
        try {
            assertThat(replication.getSearch()).isNull();

            OutputProperties.Index indexConfig = new OutputProperties.Index();
            LuceneIndexes indexes = LuceneIndexes.shared(root.toString(),
                    LuceneAnalyzers.of("standard"));
            OutputChannel plain = new LuceneOutputChannel(indexConfig, indexes);
            assertThat(replication.decorate(plain)).isSameAs(plain);
        } finally {
            replication.destroy();
            LuceneIndexes.closeShared(root.toString());
        }
    }

}
