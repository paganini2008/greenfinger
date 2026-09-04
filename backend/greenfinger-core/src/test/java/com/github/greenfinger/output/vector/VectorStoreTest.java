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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.StubServer;

/**
 * 
 * @Description: VectorStoreTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class VectorStoreTest {

    private StubServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new StubServer();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private VectorPoint point() {
        return new VectorPoint("11111111-1111-1111-1111-111111111111", new float[] {0.1f, 0.2f},
                Map.of("url", "https://a.com/x", "content", "some text"));
    }

    private OutputProperties.Vector.Qdrant qdrantConfig() {
        OutputProperties.Vector.Qdrant config = new OutputProperties.Vector.Qdrant();
        config.setUrl(server.url());
        return config;
    }

    private OutputProperties.Vector.Weaviate weaviateConfig() {
        OutputProperties.Vector.Weaviate config = new OutputProperties.Vector.Weaviate();
        config.setUrl(server.url());
        return config;
    }

    private OutputProperties.Vector.Elasticsearch esConfig() {
        OutputProperties.Vector.Elasticsearch config =
                new OutputProperties.Vector.Elasticsearch();
        config.setUris(server.url());
        return config;
    }

    @Test
    @DisplayName("elasticsearch: the index is created with dense_vector at the model's width")
    void elasticsearchCreatesTheIndex() throws Exception {
        server.on("GET", "/greenfinger_text_384", 404, "")
                .on("PUT", "/greenfinger_text_384", 200, "{}");

        ElasticsearchVectorStore store = new ElasticsearchVectorStore(esConfig());
        store.ensureCollection("greenfinger_text_384", 384);

        String body = server.requestsFor("PUT", "/greenfinger_text_384").get(0).body();
        assertThat(body).contains("dense_vector").contains("\"dims\":384").contains("cosine");
        // what is filtered is indexed; the payload is handed back with a hit and never searched
        assertThat(body).contains("catalogVersion").contains("keyword");
        // the fields go under "properties". Written at the root, Elasticsearch reads them as
        // settings it does not support and rejects the whole request -- it does not ignore them,
        // so the index is simply never created and every vector for that run is dropped
        assertThat(body).contains("\"mappings\":{\"properties\":{");
        // no replica, because a single node cannot place a copy of its own shard and a cluster
        // that has been yellow since its first crawl teaches its operator to ignore the colour
        assertThat(body).contains("\"number_of_replicas\":0");
        assertThat(store.getName()).isEqualTo("elasticsearch");
    }

    @Test
    @DisplayName("elasticsearch: a width that does not match is refused rather than written into")
    void elasticsearchRefusesAWidthThatDoesNotMatch() throws Exception {
        server.on("GET", "/greenfinger_text_384", 200,
                "{\"greenfinger_text_384\":{\"mappings\":{\"properties\":{\"vector\":{\"dims\":768}}}}}");

        assertThatThrownBy(() -> new ElasticsearchVectorStore(esConfig())
                .ensureCollection("greenfinger_text_384", 384))
                        .isInstanceOf(com.github.greenfinger.core.WebCrawlerException.class)
                        .hasMessageContaining("768");
    }

    @Test
    @DisplayName("elasticsearch: points go in one bulk request, keyed so a replay overwrites")
    void elasticsearchUpsertsInBulk() throws Exception {
        server.on("POST", "/_bulk", 200, "{\"errors\":false}");

        new ElasticsearchVectorStore(esConfig()).upsert("greenfinger_text_384",
                java.util.List.of(new VectorPoint("id-1", new float[] {0.1f, 0.2f},
                        Map.of("catalogVersion", "cat-1:0", "catalogId", "cat-1", "url", "u"))));

        String body = server.requestsFor("POST", "/_bulk").get(0).body();
        assertThat(body).contains("\"_id\":\"id-1\"").contains("greenfinger_text_384");
        // the two filtered fields are lifted out of the payload, and the payload is kept whole
        assertThat(body).contains("\"catalogVersion\":\"cat-1:0\"").contains("\"payload\"");
    }

    @Test
    @DisplayName("elasticsearch: a search is knn, filtered by version, over a wider candidate pool")
    void elasticsearchSearchesByKnn() throws Exception {
        server.on("GET", "/greenfinger_text_384", 200, "{}").on("POST",
                "/greenfinger_text_384/_search", 200,
                "{\"hits\":{\"hits\":[{\"_id\":\"id-1\",\"_score\":0.9,"
                        + "\"_source\":{\"payload\":{\"url\":\"https://a.com/x\"}}}]}}");

        java.util.List<VectorHit> hits = new ElasticsearchVectorStore(esConfig())
                .search("greenfinger_text_384", new float[] {0.1f, 0.2f}, 5,
                        java.util.List.of("cat-1:0"));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).text("url")).isEqualTo("https://a.com/x");
        String body = server.requestsFor("POST", "/greenfinger_text_384/_search").get(0).body();
        assertThat(body).contains("\"knn\"").contains("cat-1:0");
        // wider than k: the filter is applied after the walk, so a walk of exactly k comes back
        // short and reads as missing data
        assertThat(body).contains("num_candidates");
    }

    @Test
    @DisplayName("elasticsearch: deleting reports what went, and refreshes so a count agrees")
    void elasticsearchDeletesByQuery() throws Exception {
        server.on("GET", "/greenfinger_text_384", 200, "{}").on("POST",
                "/greenfinger_text_384/_delete_by_query", 200, "{\"deleted\":7}");

        long removed = new ElasticsearchVectorStore(esConfig())
                .deleteByCatalogVersion("greenfinger_text_384", "cat-1:0");

        assertThat(removed).isEqualTo(7L);
        // refreshed, because the count a person reads straight afterwards would otherwise be
        // what was true a second ago
        assertThat(server.requestsFor("POST", "/greenfinger_text_384/_delete_by_query").get(0)
                .query()).contains("refresh=true");
    }

    @Test
    @DisplayName("elasticsearch: nothing there is nothing to delete, not an error")
    void elasticsearchToleratesAMissingIndex() throws Exception {
        server.on("GET", "/greenfinger_text_384", 404, "");

        ElasticsearchVectorStore store = new ElasticsearchVectorStore(esConfig());
        assertThat(store.deleteByCatalog("greenfinger_text_384", "cat-1")).isZero();
        assertThat(store.count("greenfinger_text_384", "cat-1:0")).isZero();
        assertThat(store.search("greenfinger_text_384", new float[] {0.1f}, 5, null)).isEmpty();
    }

    @Test
    @DisplayName("the collection is created with the model's dimension, which cannot change later")
    void qdrantCreatesTheCollection() throws Exception {
        server.on("GET", "/collections/greenfinger_text", 404, "")
                .on("PUT", "/collections/greenfinger_text", 200, "{}");

        QdrantVectorStore store = new QdrantVectorStore(qdrantConfig());
        store.ensureCollection("greenfinger_text", 1536);

        String body = server.requestsFor("PUT", "/collections/greenfinger_text").get(0).body();
        assertThat(body).contains("\"size\":1536").contains("Cosine");
        assertThat(store.getName()).isEqualTo("qdrant");
    }

    @Test
    void qdrantLeavesAnExistingCollectionAlone() throws Exception {
        server.on("GET", "/collections/greenfinger_text", 200, "{}");
        new QdrantVectorStore(qdrantConfig()).ensureCollection("greenfinger_text", 1536);
        // the payload index is still ensured; what must not happen is the collection being created
        assertThat(server.requestsFor("PUT", "/collections/greenfinger_text").stream()
                .map(r -> r.path()).filter("/collections/greenfinger_text"::equals)).isEmpty();
    }

    @Test
    void qdrantRefusesToCreateWhenToldNotTo() throws Exception {
        server.on("GET", "/collections/greenfinger_text", 404, "");
        OutputProperties.Vector.Qdrant config = qdrantConfig();
        config.setCreateCollectionIfMissing(false);

        assertThatThrownBy(() -> new QdrantVectorStore(config).ensureCollection("greenfinger_text", 1536))
                .isInstanceOf(com.github.greenfinger.core.WebCrawlerException.class);
    }

    @Test
    void qdrantUpsertsPoints() throws Exception {
        server.on("PUT", "/collections/greenfinger_text/points", 200, "{}");
        new QdrantVectorStore(qdrantConfig()).upsert("greenfinger_text", List.of(point()));

        String body = server.requestsFor("PUT", "/collections/greenfinger_text/points").get(0).body();
        assertThat(body).contains("11111111-1111-1111-1111-111111111111").contains("0.1")
                .contains("https://a.com/x");
    }

    @Test
    void anEmptyBatchIsNotSent() throws Exception {
        new QdrantVectorStore(qdrantConfig()).upsert("greenfinger_text", List.of());
        new WeaviateVectorStore(weaviateConfig()).upsert("greenfinger_text", List.of());
        assertThat(server.getRequests()).isEmpty();
    }

    @Test
    @DisplayName("the endpoint may be quoted with or without its /v1 suffix")
    void weaviateToleratesTheV1Suffix() throws Exception {
        server.on("GET", "/v1/schema/Greenfinger", 200, "{}");

        OutputProperties.Vector.Weaviate config = weaviateConfig();
        config.setUrl(server.url() + "/v1");
        new WeaviateVectorStore(config).ensureCollection("greenfinger_text", 1536);

        // one call to see whether the class is there, one to read its properties back
        assertThat(server.requestsFor("GET", "/v1/schema/Greenfinger")).isNotEmpty();
    }

    @Test
    @DisplayName("Weaviate is told not to embed, since the vectors are computed here")
    void weaviateCreatesTheCollectionWithoutAVectorizer() throws Exception {
        server.on("GET", "/v1/schema/Greenfinger", 404, "")
                .on("POST", "/v1/schema", 200, "{}");

        WeaviateVectorStore store = new WeaviateVectorStore(weaviateConfig());
        store.ensureCollection("greenfinger_text", 1536);

        String body = server.requestsFor("POST", "/v1/schema").get(0).body();
        assertThat(body).contains("\"vectorizer\":\"none\"").contains("cosine")
                .contains("chunkIndex");
        assertThat(store.getName()).isEqualTo("weaviate");
    }

    @Test
    void weaviateUpsertsObjects() throws Exception {
        server.on("POST", "/v1/batch/objects", 200, "[{\"result\":{}}]");
        new WeaviateVectorStore(weaviateConfig()).upsert("greenfinger_text", List.of(point()));

        String body = server.requestsFor("POST", "/v1/batch/objects").get(0).body();
        assertThat(body).contains("Greenfinger").contains("11111111-1111-1111-1111-111111111111");
    }

    @Test
    @DisplayName("a rejected batch is an error, not silently dropped data")
    void weaviateReportsRejections() throws Exception {
        server.on("POST", "/v1/batch/objects", 200,
                "[{\"result\":{\"errors\":{\"error\":[{\"message\":\"bad vector\"}]}}}]");

        assertThatThrownBy(() -> new WeaviateVectorStore(weaviateConfig()).upsert("greenfinger_text", List.of(point())))
                .isInstanceOf(WebCrawlerException.class).hasMessageContaining("bad vector");
    }


    @Test
    @DisplayName("a Qdrant search filters on the one composite field and returns payloads")
    void qdrantSearches() throws Exception {
        server.on("GET", "/collections/greenfinger_text", 200, "{}").on("POST",
                "/collections/greenfinger_text/points/search", 200,
                "{\"result\":[{\"id\":\"p1\",\"score\":0.87,\"payload\":{\"url\":\"https://a\","
                        + "\"textLength\":1000,\"linkTextLength\":50}}]}");

        List<VectorHit> hits = new QdrantVectorStore(qdrantConfig()).search("greenfinger_text",
                new float[] {0.1f, 0.2f}, 5, List.of("c:0", "d:1"));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).score()).isEqualTo(0.87);
        assertThat(hits.get(0).text("url")).isEqualTo("https://a");
        assertThat(hits.get(0).linkDensity()).isEqualTo(0.05);

        String body = server.requestsFor("POST", "/collections/greenfinger_text/points/search")
                .get(0).body();
        assertThat(body).contains("catalogVersion").contains("c:0").contains("d:1");
    }

    @Test
    void qdrantSearchOnAMissingCollectionIsEmpty() throws Exception {
        server.on("GET", "/collections/greenfinger_text", 404, "");
        assertThat(new QdrantVectorStore(qdrantConfig()).search("greenfinger_text",
                new float[] {0.1f}, 5, List.of())).isEmpty();
    }

    @Test
    void qdrantDeletesAndCountsByVersion() throws Exception {
        server.on("GET", "/collections/greenfinger_text", 200, "{}")
                .on("POST", "/collections/greenfinger_text/points/count", 200,
                        "{\"result\":{\"count\":7}}")
                .on("POST", "/collections/greenfinger_text/points/delete", 200, "{}");

        QdrantVectorStore store = new QdrantVectorStore(qdrantConfig());
        assertThat(store.count("greenfinger_text", "c:0")).isEqualTo(7L);
        assertThat(store.deleteByCatalogVersion("greenfinger_text", "c:0")).isEqualTo(7L);
    }

    @Test
    @DisplayName("Weaviate reports a distance, which becomes a similarity")
    void weaviateSearches() throws Exception {
        server.on("GET", "/v1/schema/Greenfinger", 200, "{\"properties\":[]}").on("POST",
                "/v1/graphql", 200,
                "{\"data\":{\"Get\":{\"Greenfinger_text\":[{\"url\":\"https://a\","
                        + "\"textLength\":1000,\"linkTextLength\":900,"
                        + "\"_additional\":{\"id\":\"w1\",\"distance\":0.25}}]}}}");

        List<VectorHit> hits = new WeaviateVectorStore(weaviateConfig()).search(
                "greenfinger_text", new float[] {0.1f, 0.2f}, 5, List.of("c:0"));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).score()).isEqualTo(0.75);
        assertThat(hits.get(0).linkDensity()).isEqualTo(0.9);
    }

    @Test
    void weaviateSearchOnAMissingClassIsEmpty() throws Exception {
        server.on("GET", "/v1/schema/Greenfinger", 404, "");
        assertThat(new WeaviateVectorStore(weaviateConfig()).search("greenfinger_text",
                new float[] {0.1f}, 5, List.of())).isEmpty();
    }

    @Test
    @DisplayName("a class from an earlier version gains the properties it is missing")
    void weaviateAddsMissingProperties() throws Exception {
        server.on("GET", "/v1/schema/Greenfinger", 200,
                "{\"properties\":[{\"name\":\"url\"},{\"name\":\"title\"}]}")
                .on("POST", "/v1/schema/Greenfinger", 200, "{}");

        new WeaviateVectorStore(weaviateConfig()).ensureCollection("greenfinger_text", 384);

        // linkTextLength was not there; without it a query naming it would fail outright
        assertThat(server.requestsFor("POST", "/v1/schema/Greenfinger").stream()
                .map(r -> r.body()).filter(b -> b.contains("linkTextLength"))).isNotEmpty();
    }

    @Test
    void weaviateCountsThroughGraphql() throws Exception {
        server.on("GET", "/v1/schema/Greenfinger", 200, "{\"properties\":[]}").on("POST",
                "/v1/graphql", 200,
                "{\"data\":{\"Aggregate\":{\"Greenfinger_text\":[{\"meta\":{\"count\":11}}]}}}");

        assertThat(new WeaviateVectorStore(weaviateConfig()).count("greenfinger_text", "c:0"))
                .isEqualTo(11L);
    }

}
