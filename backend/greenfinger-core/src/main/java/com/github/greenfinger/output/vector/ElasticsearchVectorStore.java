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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.RestJsonClient;
import lombok.extern.slf4j.Slf4j;

/**
 * Vectors in Elasticsearch, beside the full text index rather than in a second server.
 *
 * <h2>Why this exists when Qdrant does</h2>
 * A deployment that already runs Elasticsearch for the index is one service away from running a
 * vector database as well, and that service has to be installed, watched, backed up and upgraded
 * for one field per document. Elasticsearch has held {@code dense_vector} and answered {@code knn}
 * since 8.x, over the same Lucene HNSW the embedded store uses -- so this is not a lesser engine,
 * it is the engine already there.
 *
 * <h2>Same addressing as everywhere else</h2>
 * A collection is an index, named for the embedding width the way the Qdrant and Weaviate
 * collections are ({@code greenfinger_text_384}), so two models never share a space and never have
 * to be told apart. Which catalog and which version a vector belongs to is a keyword field on the
 * document, filtered at query time. Nothing about the layout is specific to this store, which is
 * what makes moving between the four a setting rather than a re-crawl.
 *
 * <h2>Deleting</h2>
 * Delete by query, and refreshed: the count that follows a delete is read by a person deciding
 * whether it worked, and an unrefreshed index would answer with what was true a second ago.
 *
 * @Description: ElasticsearchVectorStore
 * @Author: Fred Feng
 * @Date: 04/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class ElasticsearchVectorStore implements VectorStore {

    static final String FIELD_CATALOG_VERSION = "catalogVersion";
    static final String FIELD_CATALOG_ID = "catalogId";
    static final String FIELD_VECTOR = "vector";

    private final OutputProperties.Vector.Elasticsearch config;
    private final RestJsonClient client;
    private final String baseUrl;

    public ElasticsearchVectorStore(OutputProperties.Vector.Elasticsearch config) {
        this.config = config;
        this.baseUrl = StringUtils.stripEnd(config.getUris().split(",")[0].trim(), "/");
        this.client = new RestJsonClient(config.getConnectTimeout(), config.getReadTimeout(),
                RestJsonClient.basicAuth(config.getUsername(), config.getPassword()));
    }

    @Override
    public String getName() {
        return "elasticsearch";
    }

    @Override
    public void ensureCollection(String collection, int dimensions) {
        String url = baseUrl + "/" + collection;
        if (client.exists(url)) {
            int existing = client.get(url).path(collection).path("mappings").path("properties")
                    .path(FIELD_VECTOR).path("dims").asInt(-1);
            if (existing > 0 && existing != dimensions) {
                // the width is in the name, so this is a mapping somebody changed by hand rather
                // than something that can happen by switching models
                throw new WebCrawlerException("Index '" + collection + "' holds " + existing
                        + "-dimension vectors but the model produces " + dimensions
                        + ". Point greenfinger at a different collection, or drop this one.");
            }
            return;
        }
        if (!config.isCreateCollectionIfMissing()) {
            throw new WebCrawlerException("No such index: " + collection);
        }
        client.put(url, Map.of("settings",
                Map.of("number_of_shards", config.getNumberOfShards(), "number_of_replicas",
                        config.getNumberOfReplicas()),
                "mappings", mapping(dimensions)));
        log.info("Created Elasticsearch vector index '{}' with {} dimensions", collection,
                dimensions);
    }

    /**
     * Only what is queried or filtered is indexed. The payload is a dozen fields that exist to be
     * handed back with a hit -- indexing them would double the index for nothing.
     */
    private Map<String, Object> mapping(int dimensions) {
        Map<String, Object> vector = new LinkedHashMap<>();
        vector.put("type", "dense_vector");
        vector.put("dims", dimensions);
        vector.put("index", true);
        vector.put("similarity", config.getSimilarity());

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(FIELD_VECTOR, vector);
        properties.put(FIELD_CATALOG_VERSION, Map.of("type", "keyword"));
        properties.put(FIELD_CATALOG_ID, Map.of("type", "keyword"));
        properties.put("payload", Map.of("type", "object", "enabled", false));
        // under "properties", which is where a mapping's fields live. Elasticsearch rejects the
        // whole request rather than ignoring what it does not recognise, so the fields written at
        // the root read to it as four unsupported settings -- and the index is never created.
        return Map.of("properties", properties);
    }

    @Override
    public void upsert(String collection, List<VectorPoint> points) {
        if (points.isEmpty()) {
            return;
        }
        StringBuilder ndjson = new StringBuilder();
        try {
            for (VectorPoint point : points) {
                Map<String, Object> document = new LinkedHashMap<>();
                document.put(FIELD_VECTOR, point.getVector());
                document.put(FIELD_CATALOG_VERSION, point.getPayload().get(FIELD_CATALOG_VERSION));
                document.put(FIELD_CATALOG_ID, point.getPayload().get(FIELD_CATALOG_ID));
                document.put("payload", point.getPayload());
                ndjson.append(client.objectMapper().writeValueAsString(
                        Map.of("index", Map.of("_index", collection, "_id", point.getId()))))
                        .append('\n')
                        .append(client.objectMapper().writeValueAsString(document)).append('\n');
            }
        } catch (Exception e) {
            throw new WebCrawlerException("Could not encode " + points.size() + " vector(s)", e);
        }
        // the id is derived from catalog, version, resource and chunk, so a replay overwrites
        // rather than duplicates
        client.postNdjson(baseUrl + "/_bulk?refresh=false", ndjson.toString());
    }

    @Override
    public long deleteByCatalogVersion(String collection, String catalogVersion) {
        return deleteByTerm(collection, FIELD_CATALOG_VERSION, catalogVersion);
    }

    @Override
    public long deleteByCatalog(String collection, String catalogId) {
        return deleteByTerm(collection, FIELD_CATALOG_ID, catalogId);
    }

    private long deleteByTerm(String collection, String field, String value) {
        if (!client.exists(baseUrl + "/" + collection)) {
            return 0L;
        }
        JsonNode response = client.post(
                baseUrl + "/" + collection + "/_delete_by_query?refresh=true&conflicts=proceed",
                Map.of("query", Map.of("term", Map.of(field, value))));
        return response.path("deleted").asLong(0L);
    }

    @Override
    public long count(String collection, String catalogVersion) {
        return countByTerm(collection, FIELD_CATALOG_VERSION, catalogVersion);
    }

    @Override
    public long countByCatalog(String collection, String catalogId) {
        return countByTerm(collection, FIELD_CATALOG_ID, catalogId);
    }

    private long countByTerm(String collection, String field, String value) {
        if (!client.exists(baseUrl + "/" + collection)) {
            return 0L;
        }
        JsonNode response = client.post(baseUrl + "/" + collection + "/_count",
                Map.of("query", Map.of("term", Map.of(field, value))));
        return response.path("count").asLong(0L);
    }

    @Override
    public List<String> collectionsMatching(String prefix) {
        List<String> names = new ArrayList<>();
        JsonNode response = client.get(baseUrl + "/_cat/indices/" + prefix + "*?format=json");
        for (JsonNode index : response) {
            String name = index.path("index").asText("");
            if (name.startsWith(prefix)) {
                names.add(name);
            }
        }
        return names;
    }

    @Override
    public List<VectorHit> search(String collection, float[] vector, int limit, int offset,
            List<String> catalogVersions) {
        if (!client.exists(baseUrl + "/" + collection)) {
            return List.of();
        }
        Map<String, Object> knn = new LinkedHashMap<>();
        knn.put("field", FIELD_VECTOR);
        knn.put("query_vector", vector);
        knn.put("k", limit + offset);
        // the candidate pool the graph is walked with. Wider than k, because a filter applied to
        // the result of a narrow walk returns fewer than k rows and looks like missing data
        knn.put("num_candidates", Math.max(config.getMinCandidates(), (limit + offset) * 4));
        if (catalogVersions != null && !catalogVersions.isEmpty()) {
            knn.put("filter", Map.of("terms", Map.of(FIELD_CATALOG_VERSION, catalogVersions)));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("knn", knn);
        body.put("size", limit);
        if (offset > 0) {
            body.put("from", offset);
        }
        body.put("_source", List.of("payload"));

        JsonNode response = client.post(baseUrl + "/" + collection + "/_search", body);
        List<VectorHit> hits = new ArrayList<>();
        for (JsonNode hit : response.path("hits").path("hits")) {
            hits.add(new VectorHit(hit.path("_id").asText(), hit.path("_score").asDouble(0d),
                    payloadOf(hit.path("_source").path("payload"))));
        }
        return hits;
    }

    private Map<String, Object> payloadOf(JsonNode node) {
        Map<String, Object> payload = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            payload.put(entry.getKey(),
                    value.isNumber() ? value.numberValue() : value.asText(null));
        });
        return payload;
    }

}
