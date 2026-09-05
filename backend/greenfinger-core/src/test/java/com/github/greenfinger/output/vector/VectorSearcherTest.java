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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.output.OutputProperties;

/**
 * 
 * @Description: VectorSearcherTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
class VectorSearcherTest {

    /** Returns whatever it was primed with, so the re-ranking is what gets asserted on. */
    private static class CannedVectorStore implements VectorStore {

        private List<VectorHit> canned = List.of();
        private String searched;
        private int requestedLimit;
        private int requestedOffset;

        @Override
        public String getName() {
            return "canned";
        }

        @Override
        public void ensureCollection(String collection, int dimensions) {
        }

        @Override
        public void upsert(String collection, List<VectorPoint> points) {
        }

        @Override
        public long deleteByCatalog(String collection, String catalogId) {
            return 0L;
        }

        @Override
        public long countByCatalog(String collection, String catalogId) {
            return 0L;
        }

        @Override
        public long deleteByCatalogVersion(String collection, String catalogVersion) {
            return 0;
        }

        @Override

        public java.util.List<String> collectionsMatching(String prefix) {

            return java.util.List.of();

        }


        @Override
        public long count(String collection, String catalogVersion) {
            return 0;
        }

        @Override
        public List<VectorHit> search(String collection, float[] vector, int limit, int offset,
                List<String> catalogVersions) {
            searched = collection;
            requestedLimit = limit;
            requestedOffset = offset;
            return canned;
        }
    }

    private static VectorHit hit(String id, String resourceId, double score, int textLength,
            int linkTextLength, String title) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resourceId", resourceId);
        payload.put("title", title);
        payload.put("textLength", textLength);
        payload.put("linkTextLength", linkTextLength);
        return new VectorHit(id, score, payload);
    }

    private OutputProperties.Vector config;
    private CannedVectorStore store;

    @BeforeEach
    void setUp() {
        config = new OutputProperties.Vector();
        store = new CannedVectorStore();
    }

    private VectorSearcher searcher(EmbeddingClient client) {
        return new VectorSearcher(config, client, store);
    }

    @Test
    @DisplayName("the collection name carries the model's width, on the query side too")
    void namesTheCollectionByWidth() {
        VectorSearcher searcher = searcher(new StubEmbeddingClient(384, 768));
        assertThat(searcher.textCollection()).isEqualTo("greenfinger_text_384");
        assertThat(searcher.imageCollection()).isEqualTo("greenfinger_image_768");
    }

    @Test
    @DisplayName("an article outranks a listing that the store scored higher")
    void prefersDetailPages() throws Exception {
        // the listing is nearly all anchor text; the article almost none
        store.canned = List.of(hit("1", "r1", 0.90, 1000, 900, "Category listing"),
                hit("2", "r2", 0.80, 1000, 50, "An actual article"));

        List<VectorHit> hits =
                searcher(new StubEmbeddingClient(4)).searchText("x", List.of("c:0"), 10, true);

        assertThat(hits).extracting(h -> h.text("title")).containsExactly("An actual article",
                "Category listing");
    }

    @Test
    void theReRankingCanBeTurnedOff() throws Exception {
        store.canned = List.of(hit("1", "r1", 0.90, 1000, 900, "Category listing"),
                hit("2", "r2", 0.80, 1000, 50, "An actual article"));

        List<VectorHit> hits =
                searcher(new StubEmbeddingClient(4)).searchText("x", List.of("c:0"), 10, false);

        assertThat(hits).extracting(h -> h.text("title")).containsExactly("Category listing",
                "An actual article");
    }

    @Test
    @DisplayName("twenty chunks of one article are one result to a reader")
    void keepsOneHitPerPage() throws Exception {
        List<VectorHit> canned = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            canned.add(hit("c" + i, "same-resource", 0.9 - i * 0.01, 1000, 50, "One article"));
        }
        canned.add(hit("other", "another-resource", 0.5, 1000, 50, "Another article"));
        store.canned = canned;

        List<VectorHit> hits =
                searcher(new StubEmbeddingClient(4)).searchText("x", List.of("c:0"), 10, true);

        assertThat(hits).hasSize(2);
        assertThat(hits).extracting(h -> h.text("resourceId"))
                .containsExactly("same-resource", "another-resource");
    }

    @Test
    void honoursTheLimit() throws Exception {
        List<VectorHit> canned = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            canned.add(hit("h" + i, "r" + i, 0.9, 1000, 50, "Article " + i));
        }
        store.canned = canned;

        assertThat(searcher(new StubEmbeddingClient(4)).searchText("x", List.of("c:0"), 3, true))
                .hasSize(3);
    }

    @Test
    @DisplayName("searching images needs a model that has them; a text-only one says so")
    void refusesImageSearchWithoutAnImageModel() {
        VectorSearcher searcher = searcher(new StubEmbeddingClient(4));

        assertThatThrownBy(() -> searcher.searchImages("a cat", List.of("c:0"), 5))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("provider=local");
    }

    @Test
    @DisplayName("image search goes to the image collection, encoded by the image model")
    void searchesTheImageCollection() throws Exception {
        store.canned = List.of(hit("i1", null, 0.7, 0, 0, null));

        List<VectorHit> hits =
                searcher(new StubEmbeddingClient(4, 8)).searchImages("a cat", List.of("c:0"), 5);

        assertThat(hits).hasSize(1);
        assertThat(store.searched).isEqualTo("greenfinger_image_8");
    }

    @Test
    void aPageWithNoTextIsTreatedAsNeutral() {
        assertThat(hit("x", "r", 0.5, 0, 0, "t").linkDensity()).isEqualTo(0.5);
    }

    @Test
    void densityIsCappedAtOne() {
        assertThat(hit("x", "r", 0.5, 100, 500, "t").linkDensity()).isEqualTo(1.0);
    }


    @Test
    @DisplayName("an image page is an offset, pushed down to the store")
    void pagesImagesByOffset() throws Exception {
        searcher(new StubEmbeddingClient(4, 8)).searchImages("a cat", List.of("c:0"), 5, 10);

        assertThat(store.requestedOffset).isEqualTo(10);
        assertThat(store.requestedLimit).isEqualTo(5);
    }

    @Test
    @DisplayName("a text page is skipped locally, because the ranking happens after the store")
    void pagesTextAfterRanking() throws Exception {
        store.canned = List.of(hit("1", "r1", 0.9d, 100, 10, "first"),
                hit("2", "r2", 0.8d, 100, 10, "second"),
                hit("3", "r3", 0.7d, 100, 10, "third"),
                hit("4", "r4", 0.6d, 100, 10, "fourth"));

        List<VectorHit> second =
                searcher(new StubEmbeddingClient(4)).searchText("x", List.of("c:0"), 2, 2, false);

        // the store was asked for the whole span, not for an offset of its own
        assertThat(store.requestedOffset).isZero();
        assertThat(second).extracting(VectorHit::id).containsExactly("3", "4");
    }

}
