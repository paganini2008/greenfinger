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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsImpl;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.model.Resource;
import com.github.greenfinger.core.output.OutputPayload;
import com.github.greenfinger.core.record.ResourceRecord;
import com.github.greenfinger.core.output.IndexAdmin;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.index.LuceneAnalyzers;
import com.github.greenfinger.output.index.LuceneIndexAdmin;
import com.github.greenfinger.output.index.LuceneIndexes;
import com.github.greenfinger.output.index.LuceneOutputChannel;
import com.github.greenfinger.output.vector.LuceneVectorStore;
import com.github.greenfinger.output.vector.VectorHit;
import com.github.greenfinger.output.vector.VectorPoint;

/**
 * What a node sends the others when its search engine is the embedded one.
 *
 * <p>
 * The whole point is that a url is dispatched to exactly one node, so without this most of the
 * corpus is missing from every node's answer. Both halves are tested the same way: write on one,
 * apply on the other, and ask the other for it.
 * 
 * @Description: ReplicatedSearchTest
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
class ReplicatedSearchTest {

    private static final String CATALOG_ID = "0192f0c8-1234-7000-8000-0000000000aa";
    private static final String VERSION = CATALOG_ID + ":0";
    private static final String COLLECTION = "greenfinger_text_4";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<ReplicationBatch.Entry> sent = new ArrayList<>();
    private final ReplicationSink sink = sent::add;

    @TempDir
    Path here;

    @TempDir
    Path there;

    private OutputProperties.Index indexConfig;

    @BeforeEach
    void setUp() {
        indexConfig = new OutputProperties.Index();
        indexConfig.getLucene().setCommitEvery(1);
    }

    @AfterEach
    void tearDown() {
        LuceneIndexes.closeShared(here.toString());
        LuceneIndexes.closeShared(there.toString());
    }

    /**
     * The smallest catalog and page that will produce a document. Built here rather than borrowed
     * from greenfinger-core's fixtures, which are that module's test classes and not published.
     */
    private CatalogDetails catalogDetails() {
        Catalog catalog = new Catalog();
        catalog.setId(CATALOG_ID);
        catalog.setName("example");
        catalog.setUrl("https://www.example.com");
        catalog.setPathPattern("**.example.com");
        catalog.setIndexVersion(0);
        catalog.setSearchVersion(-1);
        return new CatalogDetailsImpl(catalog, new WebCrawlerProperties());
    }

    private OutputPayload payload(String text) {
        Resource resource = new Resource();
        resource.setId("res-1");
        resource.setCatalogId(CATALOG_ID);
        resource.setVersion(0);
        resource.setUrl("https://www.example.com/a");
        resource.setTitle("Page A");
        resource.setCat("test");
        resource.setTextLength(text.length());
        resource.setLinkTextLength(0);
        resource.setCreatedAt(new java.util.Date());
        OutputPayload payload = new OutputPayload(catalogDetails(),
                new ResourceRecord(resource, List.of()), null);
        payload.setText(text);
        return payload;
    }

    private LuceneIndexes indexes(Path root) {
        return LuceneIndexes.shared(root.toString(), LuceneAnalyzers.of("standard"));
    }

    @Test
    @DisplayName("a document indexed on one node reaches the others, by id so a repeat is one hit")
    void documentsTravel() throws Exception {
        String indexName = IndexAdmin.indexOf(indexConfig.getPrefix(), CATALOG_ID);
        Map<String, Object> fields = Map.of("id", "res-1", "title", "Page A", "content",
                "Alpha content", "catalogId", CATALOG_ID, "catalogVersion", VERSION,
                "version", 0, "textLength", 1000, "linkTextLength", 100);

        sink.replicate(ReplicationBatch.Entry.of(ReplicatedIndexChannel.OP_DOCUMENT, CATALOG_ID,
                indexName, objectMapper.writeValueAsBytes(fields)));

        LuceneIndexes remote = indexes(there);
        ReplicatedIndexChannel.apply(sent.get(0), remote, objectMapper);
        // twice, because delivery is at least once
        ReplicatedIndexChannel.apply(sent.get(0), remote, objectMapper);
        remote.commit(indexName);

        indexConfig.getLucene().setDirectory(there.toString());
        assertThat(new LuceneIndexAdmin(indexConfig, remote).countByCatalogVersion(VERSION))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("an unknown op is dropped rather than applied as something else")
    void unknownIndexOpsAreDropped() throws Exception {
        LuceneIndexes remote = indexes(there);
        ReplicatedIndexChannel.apply(
                ReplicationBatch.Entry.of((byte) 99, CATALOG_ID, "whatever", new byte[0]), remote,
                objectMapper);
        assertThat(remote.names()).isEmpty();
    }

    @Test
    @DisplayName("a vector embedded on one node reaches the others, floats intact")
    void vectorsTravel() throws Exception {
        OutputProperties.Vector.Lucene config = new OutputProperties.Vector.Lucene();
        config.setDirectory(here.toString());
        LuceneVectorStore local = new LuceneVectorStore(config);
        local.afterPropertiesSet();
        local.ensureCollection(COLLECTION, 4);

        ReplicatedVectorStore replicated = new ReplicatedVectorStore(local, sink);
        replicated.upsert(COLLECTION, List.of(new VectorPoint("chunk-1",
                new float[] {0.25f, 0.5f, 0.75f, 1f},
                Map.of("catalogVersion", VERSION, "title", "Page A"))));

        OutputProperties.Vector.Lucene remoteConfig = new OutputProperties.Vector.Lucene();
        remoteConfig.setDirectory(there.toString());
        LuceneVectorStore remote = new LuceneVectorStore(remoteConfig);
        remote.afterPropertiesSet();
        ReplicatedVectorStore.apply(sent.get(0), remote, objectMapper);

        List<VectorHit> hits =
                remote.search(COLLECTION, new float[] {0.25f, 0.5f, 0.75f, 1f}, 5, List.of(VERSION));
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).id()).isEqualTo("chunk-1");
        assertThat(hits.get(0).text("title")).isEqualTo("Page A");
        assertThat(replicated.getName()).isEqualTo("replicated:lucene");
    }

    @Test
    @DisplayName("floats survive the wire exactly, which json would not have managed")
    void encodesAndDecodesAPoint() throws Exception {
        VectorPoint point = new VectorPoint("chunk-1", new float[] {0.1234567f, -2.5f, 0f},
                Map.of("catalogVersion", VERSION));

        VectorPoint back = ReplicatedVectorStore.decode("chunk-1",
                ReplicatedVectorStore.encode(point, objectMapper), objectMapper);

        assertThat(back.getId()).isEqualTo("chunk-1");
        assertThat(back.getVector()).containsExactly(0.1234567f, -2.5f, 0f);
        assertThat(back.getPayload()).containsEntry("catalogVersion", VERSION);
    }

    @Test
    @DisplayName("a delete travels too, so a version removed here goes there as well")
    void deletesTravel() throws Exception {
        OutputProperties.Vector.Lucene config = new OutputProperties.Vector.Lucene();
        config.setDirectory(here.toString());
        LuceneVectorStore local = new LuceneVectorStore(config);
        local.afterPropertiesSet();
        local.ensureCollection(COLLECTION, 4);
        local.upsert(COLLECTION, List.of(new VectorPoint("chunk-1", new float[] {1f, 0f, 0f, 0f},
                Map.of("catalogVersion", VERSION))));

        ReplicatedVectorStore replicated = new ReplicatedVectorStore(local, sink);
        assertThat(replicated.deleteByCatalogVersion(COLLECTION, VERSION)).isEqualTo(1L);
        assertThat(sent).anyMatch(e -> e.op() == ReplicatedVectorStore.OP_DELETE_VERSION);

        OutputProperties.Vector.Lucene remoteConfig = new OutputProperties.Vector.Lucene();
        remoteConfig.setDirectory(there.toString());
        LuceneVectorStore remote = new LuceneVectorStore(remoteConfig);
        remote.afterPropertiesSet();
        remote.ensureCollection(COLLECTION, 4);
        remote.upsert(COLLECTION, List.of(new VectorPoint("chunk-1", new float[] {1f, 0f, 0f, 0f},
                Map.of("catalogVersion", VERSION))));

        ReplicatedVectorStore.apply(
                sent.stream().filter(e -> e.op() == ReplicatedVectorStore.OP_DELETE_VERSION)
                        .findFirst().orElseThrow(),
                remote, objectMapper);
        assertThat(remote.count(COLLECTION, VERSION)).isZero();
    }

    @Test
    @DisplayName("reads and lifecycle go straight through to this node's own copy")
    void readsArePassedThrough() throws Exception {
        OutputProperties.Vector.Lucene config = new OutputProperties.Vector.Lucene();
        config.setDirectory(here.toString());
        LuceneVectorStore local = new LuceneVectorStore(config);
        ReplicatedVectorStore replicated = new ReplicatedVectorStore(local, sink);

        replicated.afterPropertiesSet();
        replicated.ensureCollection(COLLECTION, 4);
        assertThat(replicated.collectionsMatching("greenfinger_")).containsExactly(COLLECTION);
        assertThat(replicated.count(COLLECTION, VERSION)).isZero();
        assertThat(replicated.search(COLLECTION, new float[] {1f, 0f, 0f, 0f}, 5, 0, null))
                .isEmpty();
        replicated.destroy();
        // nothing was announced: a read is this node's business alone
        assertThat(sent).isEmpty();
    }

    @Test
    @DisplayName("an unreadable vector is logged and dropped, not thrown at the channel")
    void aBrokenVectorDoesNotStopTheChannel() throws Exception {
        OutputProperties.Vector.Lucene config = new OutputProperties.Vector.Lucene();
        config.setDirectory(there.toString());
        LuceneVectorStore remote = new LuceneVectorStore(config);
        remote.afterPropertiesSet();

        ReplicatedVectorStore.apply(ReplicationBatch.Entry.of(ReplicatedVectorStore.OP_UPSERT,
                COLLECTION, "chunk-1", new byte[] {1, 2, 3}), remote, objectMapper);
        ReplicatedVectorStore.apply(
                ReplicationBatch.Entry.of((byte) 99, COLLECTION, "chunk-1", new byte[0]), remote,
                objectMapper);

        assertThat(remote.count(COLLECTION, VERSION)).isZero();
    }

    @Test
    void onlyTheEmbeddedIndexIsCopiedAround() {
        assertThat(ReplicatedIndexChannel.shouldReplicate("lucene")).isTrue();
        assertThat(ReplicatedIndexChannel.shouldReplicate("elasticsearch")).isFalse();
    }


    @Test
    @DisplayName("the channel writes here first, then tells the others: a node's own index is"
            + " right even when the cluster is not")
    void writesLocallyAndAnnounces() throws Exception {
        indexConfig.getLucene().setDirectory(here.toString());
        LuceneIndexes local = indexes(here);
        LuceneOutputChannel plain = new LuceneOutputChannel(indexConfig, local);
        ReplicatedIndexChannel channel = new ReplicatedIndexChannel(plain, sink);

        channel.open(catalogDetails());
        channel.write(payload("Alpha content, plenty of prose"));
        channel.flush();
        channel.close();

        assertThat(channel.getName()).isEqualTo("index");
        assertThat(channel.getType()).isEqualTo(OutputType.INDEX);
        // the index and the vectors are rebuilt from the database if they go wrong, so neither
        // is worth failing a crawl over
        assertThat(channel.isRequired()).isFalse();

        LuceneIndexAdmin admin = new LuceneIndexAdmin(indexConfig, local);
        assertThat(admin.countByCatalogVersion(VERSION)).isEqualTo(1L);

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).op()).isEqualTo(ReplicatedIndexChannel.OP_DOCUMENT);

        // and the other node ends up with the same document
        LuceneIndexes remote = indexes(there);
        ReplicatedIndexChannel.apply(sent.get(0), remote, objectMapper);
        remote.commit(sent.get(0).key());
        OutputProperties.Index remoteConfig = new OutputProperties.Index();
        remoteConfig.getLucene().setDirectory(there.toString());
        assertThat(new LuceneIndexAdmin(remoteConfig, remote).countByCatalogVersion(VERSION))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("a document that cannot be read is dropped, not thrown at the channel")
    void aBrokenDocumentDoesNotStopTheChannel() {
        LuceneIndexes remote = indexes(there);
        ReplicatedIndexChannel.apply(
                ReplicationBatch.Entry.of(ReplicatedIndexChannel.OP_DOCUMENT, CATALOG_ID,
                        "greenfinger-x", "not json".getBytes()),
                remote, objectMapper);
        // nothing was written and nothing was thrown: one unreadable frame must not take the
        // channel down with it
        assertThat(remote.names()).doesNotContain("greenfinger-x");
    }

}
