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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawledPage;
import com.github.greenfinger.core.model.ContentMode;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.output.ContentReader;
import com.github.greenfinger.output.OutputFixtures;
import com.github.greenfinger.output.OutputProperties;

/**
 * 
 * @Description: VectorOutputChannelTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
class VectorOutputChannelTest {

    /** Keeps what it was handed, per collection. */
    private static class RecordingVectorStore implements VectorStore {

        private final Map<String, List<VectorPoint>> points = new ConcurrentHashMap<>();
        private final Map<String, Integer> dimensions = new ConcurrentHashMap<>();

        @Override
        public String getName() {
            return "recording";
        }

        @Override
        public void ensureCollection(String collection, int size) {
            dimensions.put(collection, size);
        }

        @Override
        public void upsert(String collection, List<VectorPoint> batch) {
            points.computeIfAbsent(collection, k -> new ArrayList<>()).addAll(batch);
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
            List<VectorPoint> kept = points.getOrDefault(collection, List.of()).stream()
                    .filter(p -> !catalogVersion.equals(p.getPayload().get("catalogVersion")))
                    .toList();
            long removed = points.getOrDefault(collection, List.of()).size() - kept.size();
            points.put(collection, new ArrayList<>(kept));
            return removed;
        }

        @Override

        public java.util.List<String> collectionsMatching(String prefix) {

            return java.util.List.of();

        }


        @Override
        public long count(String collection, String catalogVersion) {
            return points.getOrDefault(collection, List.of()).stream()
                    .filter(p -> catalogVersion.equals(p.getPayload().get("catalogVersion")))
                    .count();
        }

        @Override
        public List<VectorHit> search(String collection, float[] vector, int limit, int offset,
                List<String> catalogVersions) {
            return points.getOrDefault(collection, List.of()).stream()
                    .filter(p -> catalogVersions == null || catalogVersions.isEmpty()
                            || catalogVersions.contains(p.getPayload().get("catalogVersion")))
                    .skip(offset).limit(limit)
                    // a fixed similarity: what a test asserts on is the re-ranking, not the maths
                    .map(p -> new VectorHit(p.getId(), 0.9d, p.getPayload())).toList();
        }

        List<VectorPoint> of(String collection) {
            return points.getOrDefault(collection, List.of());
        }
    }

    /** Reads back whatever the file layer would have written. */
    private static class FakeContentReader implements ContentReader {

        @Override
        public Optional<String> readText(String path) {
            return Optional.of("text at " + path);
        }

        @Override
        public Optional<byte[]> readBytes(String path) {
            return Optional.of(new byte[] {1, 2, 3});
        }
    }

    private OutputProperties.Vector config;
    private RecordingVectorStore vectorStore;
    private FakeContentReader contentReader;

    @BeforeEach
    void setUp() {
        config = new OutputProperties.Vector();
        vectorStore = new RecordingVectorStore();
        contentReader = new FakeContentReader();
    }

    private VectorOutputChannel channel(EmbeddingClient client) {
        return new VectorOutputChannel(config, client, vectorStore, contentReader);
    }

    @Test
    @DisplayName("the width is part of the collection name, so changing model cannot corrupt one")
    void collectionNamesCarryTheirWidth() throws Exception {
        CatalogDetails details = OutputFixtures.catalogDetails(Set.of(OutputType.VECTOR));
        try (VectorOutputChannel channel = channel(new StubEmbeddingClient(384, 768))) {
            channel.open(details);
            assertThat(channel.getTextCollection()).isEqualTo("greenfinger_text_384");
            assertThat(channel.getImageCollection()).isEqualTo("greenfinger_image_768");
        }
    }

    @Test
    void writesOneTextPointPerChunk() throws Exception {
        CatalogDetails details = OutputFixtures.catalogDetails(Set.of(OutputType.VECTOR));
        CrawledPage page = OutputFixtures.page("https://www.example.com/a", "A", "body");

        try (VectorOutputChannel channel = channel(new StubEmbeddingClient(4))) {
            channel.open(details);
            channel.write(OutputFixtures.payload(details, page));
            channel.flush();
        }
        assertThat(vectorStore.of("greenfinger_text_4")).hasSize(1);
        assertThat(vectorStore.of("greenfinger_text_4").get(0).getVector()).hasSize(4);
    }

    @Test
    void textPayloadCarriesTheCatalogVersionAndTheSourcePage() throws Exception {
        CatalogDetails details = OutputFixtures.catalogDetails(Set.of(OutputType.VECTOR));
        CrawledPage page = OutputFixtures.page("https://www.example.com/a", "A", "body");

        try (VectorOutputChannel channel = channel(new StubEmbeddingClient(4))) {
            channel.open(details);
            channel.write(OutputFixtures.payload(details, page));
            channel.flush();
        }
        Map<String, Object> payload = vectorStore.of("greenfinger_text_4").get(0).getPayload();
        assertThat(payload.get("catalogVersion")).isEqualTo(OutputFixtures.CATALOG_ID + ":0");
        assertThat(payload.get("url")).isEqualTo("https://www.example.com/a");
        assertThat(payload).containsKey("chunkText");
    }

    @Test
    @DisplayName("an image vector is written per page-image reference, so a hit knows its page")
    void imagePointsAreSelfSufficient() throws Exception {
        CatalogDetails details = OutputFixtures.catalogDetails(Set.of(OutputType.VECTOR));
        CrawledPage page =
                OutputFixtures.pageWithImage("https://www.example.com/b", "B", "with picture");

        try (VectorOutputChannel channel = channel(new StubEmbeddingClient(4, 8))) {
            channel.open(details);
            channel.write(OutputFixtures.payload(details, page));
            channel.flush();
        }
        List<VectorPoint> images = vectorStore.of("greenfinger_image_8");
        assertThat(images).hasSize(1);
        Map<String, Object> payload = images.get(0).getPayload();
        // the referring page travels with the picture, because search never asks the database
        assertThat(payload.get("url")).isEqualTo("https://www.example.com/b");
        assertThat(payload.get("title")).isEqualTo("B");
        assertThat(payload.get("alt")).isEqualTo("a picture");
    }

    @Test
    @DisplayName("the point id is the reference row's id: both name one page-image pair")
    void imagePointIdIsTheReferenceId() throws Exception {
        CatalogDetails details = OutputFixtures.catalogDetails(Set.of(OutputType.VECTOR));
        CrawledPage page =
                OutputFixtures.pageWithImage("https://www.example.com/b", "B", "text");
        var record = OutputFixtures.record(page);

        try (VectorOutputChannel channel = channel(new StubEmbeddingClient(4, 8))) {
            channel.open(details);
            channel.write(OutputFixtures.payload(details, page));
            channel.flush();
        }
        assertThat(vectorStore.of("greenfinger_image_8").get(0).getId())
                .isEqualTo(record.images().get(0).reference().getId());
    }

    @Test
    @DisplayName("a text-only embedding client is fine; the images simply get no vectors")
    void skipsImagesWhenTheClientCannotEmbedThem() throws Exception {
        CatalogDetails details = OutputFixtures.catalogDetails(Set.of(OutputType.VECTOR));
        CrawledPage page =
                OutputFixtures.pageWithImage("https://www.example.com/b", "B", "text");

        try (VectorOutputChannel channel = channel(new StubEmbeddingClient(4))) {
            channel.open(details);
            channel.write(OutputFixtures.payload(details, page));
            channel.flush();
            assertThat(channel.getImageCollection()).isNull();
        }
        assertThat(vectorStore.of("greenfinger_text_4")).isNotEmpty();
        assertThat(vectorStore.of("greenfinger_image_8")).isEmpty();
    }

    @Test
    void textOnlyModeAlsoSkipsImages() throws Exception {
        CatalogDetails details =
                OutputFixtures.catalogDetails(Set.of(OutputType.VECTOR), ContentMode.TEXT);
        CrawledPage page =
                OutputFixtures.pageWithImage("https://www.example.com/b", "B", "text");

        try (VectorOutputChannel channel = channel(new StubEmbeddingClient(4, 8))) {
            channel.open(details);
            channel.write(OutputFixtures.payload(details, page));
            channel.flush();
        }
        assertThat(vectorStore.of("greenfinger_image_8")).isEmpty();
    }

    @Test
    void longTextIsSplitIntoSeveralPoints() throws Exception {
        config.setChunkSize(50);
        config.setChunkOverlap(10);
        CatalogDetails details = OutputFixtures.catalogDetails(Set.of(OutputType.VECTOR));
        CrawledPage page = OutputFixtures.page("https://www.example.com/long", "Long",
                "word ".repeat(200));

        try (VectorOutputChannel channel = channel(new StubEmbeddingClient(4))) {
            channel.open(details);
            channel.write(OutputFixtures.payload(details, page));
            channel.flush();
        }
        assertThat(vectorStore.of("greenfinger_text_4").size()).isGreaterThan(1);
    }

    @Test
    void reportsWhatItWrote() throws Exception {
        CatalogDetails details = OutputFixtures.catalogDetails(Set.of(OutputType.VECTOR));
        CrawledPage page =
                OutputFixtures.pageWithImage("https://www.example.com/b", "B", "text");

        try (VectorOutputChannel channel = channel(new StubEmbeddingClient(4, 8))) {
            channel.open(details);
            channel.write(OutputFixtures.payload(details, page));
            channel.flush();
            assertThat(channel.getTextWrittenCount()).isEqualTo(1L);
            assertThat(channel.getImageWrittenCount()).isEqualTo(1L);
        }
    }

    @Test
    void isNotRequiredSoAFailureCannotStopACrawl() throws Exception {
        try (VectorOutputChannel channel = channel(new StubEmbeddingClient(4))) {
            assertThat(channel.getType()).isEqualTo(OutputType.VECTOR);
            assertThat(channel.isRequired()).isFalse();
        }
    }

}
