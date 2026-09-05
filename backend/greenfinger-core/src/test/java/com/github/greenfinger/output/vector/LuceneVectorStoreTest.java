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

package com.github.greenfinger.output.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.index.LuceneIndexes;

/**
 * The embedded vector store, end to end.
 * 
 * @Description: LuceneVectorStoreTest
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
class LuceneVectorStoreTest {

    private static final String COLLECTION = "greenfinger_text_4";
    private static final String VERSION = "cat-1:0";

    @TempDir
    Path directory;

    private OutputProperties.Vector.Lucene config;
    private LuceneVectorStore store;

    @BeforeEach
    void setUp() throws Exception {
        config = new OutputProperties.Vector.Lucene();
        config.setDirectory(directory.toString());
        store = new LuceneVectorStore(config);
        store.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        LuceneIndexes.closeShared(directory.toString());
    }

    private VectorPoint point(String id, float[] vector, String catalogVersion) {
        return new VectorPoint(id, vector,
                Map.of("catalogVersion", catalogVersion, "url", "https://a.com/" + id,
                        "title", "Page " + id, "textLength", 1000, "linkTextLength", 100));
    }

    @Test
    @DisplayName("what was written comes back nearest first, with its payload")
    void writesAndSearches() throws Exception {
        store.ensureCollection(COLLECTION, 4);
        store.upsert(COLLECTION, List.of(point("a", new float[] {1f, 0f, 0f, 0f}, VERSION),
                point("b", new float[] {0f, 1f, 0f, 0f}, VERSION)));

        List<VectorHit> hits =
                store.search(COLLECTION, new float[] {1f, 0f, 0f, 0f}, 2, List.of(VERSION));

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).id()).isEqualTo("a");
        assertThat(hits.get(0).text("title")).isEqualTo("Page a");
        // the payload carries the two numbers so a re-rank can prefer detail pages
        assertThat(hits.get(0).linkDensity()).isEqualTo(0.1d);
    }

    @Test
    @DisplayName("writing the same chunk twice is one point, which is what makes a replay safe")
    void upsertReplaces() throws Exception {
        store.ensureCollection(COLLECTION, 4);
        store.upsert(COLLECTION, List.of(point("a", new float[] {1f, 0f, 0f, 0f}, VERSION)));
        store.upsert(COLLECTION, List.of(point("a", new float[] {0f, 1f, 0f, 0f}, VERSION)));

        assertThat(store.count(COLLECTION, VERSION)).isEqualTo(1L);
    }

    @Test
    @DisplayName("the filter is applied while the graph is walked, not after")
    void searchesOnlyTheVersionsAsked() throws Exception {
        store.ensureCollection(COLLECTION, 4);
        store.upsert(COLLECTION, List.of(point("a", new float[] {1f, 0f, 0f, 0f}, VERSION),
                point("b", new float[] {1f, 0f, 0f, 0f}, "cat-1:1")));

        assertThat(store.search(COLLECTION, new float[] {1f, 0f, 0f, 0f}, 10, List.of(VERSION)))
                .extracting(VectorHit::id).containsExactly("a");
        // no versions named is no filter at all
        assertThat(store.search(COLLECTION, new float[] {1f, 0f, 0f, 0f}, 10, null)).hasSize(2);
    }

    @Test
    void countsAndDeletesOneVersion() throws Exception {
        store.ensureCollection(COLLECTION, 4);
        store.upsert(COLLECTION, List.of(point("a", new float[] {1f, 0f, 0f, 0f}, VERSION),
                point("b", new float[] {0f, 1f, 0f, 0f}, "cat-1:1")));

        assertThat(store.count(COLLECTION, VERSION)).isEqualTo(1L);
        assertThat(store.deleteByCatalogVersion(COLLECTION, VERSION)).isEqualTo(1L);
        assertThat(store.count(COLLECTION, VERSION)).isZero();
        assertThat(store.count(COLLECTION, "cat-1:1")).isEqualTo(1L);
    }

    @Test
    @DisplayName("a collection that has never been written is empty, not an error")
    void anAbsentCollectionIsEmpty() throws Exception {
        assertThat(store.search("nothing_here", new float[] {1f, 0f, 0f, 0f}, 5, null)).isEmpty();
        assertThat(store.count("nothing_here", VERSION)).isZero();
        assertThat(store.deleteByCatalogVersion("nothing_here", VERSION)).isZero();
    }

    @Test
    @DisplayName("changing the model mid-life is refused where it is noticed, not mid-crawl")
    void refusesAWidthThatDoesNotMatch() throws Exception {
        store.ensureCollection(COLLECTION, 4);
        store.upsert(COLLECTION, List.of(point("a", new float[] {1f, 0f, 0f, 0f}, VERSION)));

        assertThatThrownBy(() -> store.ensureCollection(COLLECTION, 8))
                .isInstanceOf(WebCrawlerException.class).hasMessageContaining("4 dimensional")
                .hasMessageContaining("8");
    }

    @Test
    void listsItsCollections() throws Exception {
        store.ensureCollection(COLLECTION, 4);
        store.ensureCollection("greenfinger_image_4", 4);
        store.ensureCollection("something_else", 4);

        assertThat(store.collectionsMatching("greenfinger_"))
                .containsExactly("greenfinger_image_4", COLLECTION);
    }

    @Test
    @DisplayName("an offset walks further down the ranking")
    void searchesFromAnOffset() throws Exception {
        store.ensureCollection(COLLECTION, 4);
        store.upsert(COLLECTION, List.of(point("a", new float[] {1f, 0f, 0f, 0f}, VERSION),
                point("b", new float[] {0.9f, 0.1f, 0f, 0f}, VERSION)));

        List<VectorHit> second =
                store.search(COLLECTION, new float[] {1f, 0f, 0f, 0f}, 1, 1, List.of(VERSION));
        assertThat(second).hasSize(1);
        assertThat(second.get(0).id()).isEqualTo("b");
    }

    @Test
    void similarityIsNamedRatherThanNumbered() {
        assertThat(LuceneVectorStore.similarityOf("cosine"))
                .isEqualTo(VectorSimilarityFunction.COSINE);
        assertThat(LuceneVectorStore.similarityOf("dot"))
                .isEqualTo(VectorSimilarityFunction.DOT_PRODUCT);
        assertThat(LuceneVectorStore.similarityOf("euclidean"))
                .isEqualTo(VectorSimilarityFunction.EUCLIDEAN);
        assertThat(LuceneVectorStore.similarityOf(null))
                .isEqualTo(VectorSimilarityFunction.COSINE);
        assertThatThrownBy(() -> LuceneVectorStore.similarityOf("hamming"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hamming");
    }

    @Test
    @DisplayName("closing one caller's store does not shut the directory another is writing to")
    void destroyDoesNotCloseTheSharedDirectory() throws Exception {
        store.ensureCollection(COLLECTION, 4);
        store.upsert(COLLECTION, List.of(point("a", new float[] {1f, 0f, 0f, 0f}, VERSION)));

        // what a search does around its own work, while a crawl is still writing
        LuceneVectorStore borrower = new LuceneVectorStore(config);
        borrower.afterPropertiesSet();
        borrower.destroy();

        assertThat(store.count(COLLECTION, VERSION)).isEqualTo(1L);
        assertThat(store.getName()).isEqualTo("lucene");
    }

    @Test
    @DisplayName("it opens itself, because not every caller opens it")
    void opensOnFirstUse() throws Exception {
        // a vector store used to be an http client, so the output channel reasonably assumed
        // constructing one was enough and calls ensureCollection before initialising it. Failing
        // there skipped the vector output for a whole crawl and only said so afterwards.
        LuceneVectorStore fresh = new LuceneVectorStore(config);
        fresh.ensureCollection(COLLECTION, 4);
        fresh.upsert(COLLECTION, List.of(point("a", new float[] {1f, 0f, 0f, 0f}, VERSION)));

        assertThat(fresh.count(COLLECTION, VERSION)).isEqualTo(1L);
    }

}
