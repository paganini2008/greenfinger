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
 * Qdrant over its REST api.
 * 
 * @Description: QdrantVectorStore
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class QdrantVectorStore implements VectorStore {

    /** Every payload carries it, and every filter matches on it. */
    static final String FIELD_CATALOG_VERSION = "catalogVersion";

    private final OutputProperties.Vector.Qdrant config;
    private final RestJsonClient client;
    private final String baseUrl;

    public QdrantVectorStore(OutputProperties.Vector.Qdrant config) {
        this.config = config;
        this.baseUrl = StringUtils.stripEnd(config.getUrl(), "/");
        // `api-key`, not `Authorization`: Qdrant reads a header of its own and answers 401 to a
        // bearer token however well formed
        this.client = StringUtils.isNotBlank(config.getApiKey())
                ? new RestJsonClient(10000, 60000, config.getApiKey(), "api-key")
                : new RestJsonClient(10000, 60000);
    }

    @Override
    public String getName() {
        return "qdrant";
    }

    @Override
    public void ensureCollection(String collection, int dimensions) {
        String url = baseUrl + "/collections/" + collection;
        if (client.exists(url)) {
            int existing = client.get(url).path("result").path("config").path("params")
                    .path("vectors").path("size").asInt(-1);
            if (existing > 0 && existing != dimensions) {
                throw new WebCrawlerException("Collection '" + collection + "' holds " + existing
                        + "-dimension vectors but the model produces " + dimensions
                        + ". Point greenfinger at a different collection, or re-create this one.");
            }
            ensurePayloadIndex(collection);
            return;
        }
        if (!config.isCreateCollectionIfMissing()) {
            throw new WebCrawlerException("No such collection: " + collection);
        }
        client.put(url, Map.of("vectors",
                Map.of("size", dimensions, "distance", config.getDistance())));
        ensurePayloadIndex(collection);
        log.info("Created Qdrant collection '{}' with {} dimensions", collection, dimensions);
    }

    /**
     * Without this index, filtering and deleting by version degrade into a full scan of the
     * collection.
     */
    private void ensurePayloadIndex(String collection) {
        try {
            client.put(baseUrl + "/collections/" + collection + "/index?wait=true",
                    Map.of("field_name", FIELD_CATALOG_VERSION, "field_schema", "keyword"));
        } catch (Exception e) {
            log.debug("Payload index on '{}' not created: {}", collection, e.getMessage());
        }
    }

    @Override
    public void upsert(String collection, List<VectorPoint> points) {
        if (points.isEmpty()) {
            return;
        }
        List<Map<String, Object>> body = new ArrayList<>(points.size());
        for (VectorPoint point : points) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("id", point.getId());
            one.put("vector", point.getVector());
            one.put("payload", point.getPayload());
            body.add(one);
        }
        client.put(baseUrl + "/collections/" + collection + "/points?wait=true",
                Map.of("points", body));
    }

    @Override
    public long deleteByCatalogVersion(String collection, String catalogVersion) {
        if (!client.exists(baseUrl + "/collections/" + collection)) {
            return 0L;
        }
        long before = count(collection, catalogVersion);
        client.post(baseUrl + "/collections/" + collection + "/points/delete?wait=true",
                Map.of("filter", filter(catalogVersion)));
        return before;
    }

    @Override
    public long deleteByCatalog(String collection, String catalogId) {
        if (!client.exists(baseUrl + "/collections/" + collection)) {
            return 0L;
        }
        long before = countByCatalog(collection, catalogId);
        client.post(baseUrl + "/collections/" + collection + "/points/delete?wait=true",
                Map.of("filter", catalogFilter(catalogId)));
        return before;
    }

    @Override
    public long countByCatalog(String collection, String catalogId) {
        if (!client.exists(baseUrl + "/collections/" + collection)) {
            return 0L;
        }
        JsonNode response = client.post(baseUrl + "/collections/" + collection + "/points/count",
                Map.of("filter", catalogFilter(catalogId), "exact", true));
        return response.path("result").path("count").asLong(0L);
    }

    @Override
    public long count(String collection, String catalogVersion) {
        if (!client.exists(baseUrl + "/collections/" + collection)) {
            return 0L;
        }
        JsonNode response =
                client.post(baseUrl + "/collections/" + collection + "/points/count",
                        Map.of("filter", filter(catalogVersion), "exact", true));
        return response.path("result").path("count").asLong(0L);
    }

    /**
     * Qdrant lists its collections, so the width suffix does not have to be guessed.
     */
    @Override
    public List<String> collectionsMatching(String prefix) {
        JsonNode response = client.get(baseUrl + "/collections");
        List<String> names = new ArrayList<>();
        for (JsonNode collection : response.path("result").path("collections")) {
            String name = collection.path("name").asText("");
            if (name.startsWith(prefix)) {
                names.add(name);
            }
        }
        return names;
    }

    @Override
    public List<VectorHit> search(String collection, float[] vector, int limit, int offset,
            List<String> catalogVersions) {
        if (!client.exists(baseUrl + "/collections/" + collection)) {
            return List.of();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", vector);
        body.put("limit", limit);
        if (offset > 0) {
            // left out when it is zero so the request stays the shape it has always been
            body.put("offset", offset);
        }
        body.put("with_payload", true);
        if (catalogVersions != null && !catalogVersions.isEmpty()) {
            // any-of over the one composite field, the same shape the index filters with
            body.put("filter", Map.of("must", List.of(Map.of("key", FIELD_CATALOG_VERSION, "match",
                    Map.of("any", catalogVersions)))));
        }
        JsonNode response =
                client.post(baseUrl + "/collections/" + collection + "/points/search", body);

        List<VectorHit> hits = new ArrayList<>();
        for (JsonNode point : response.path("result")) {
            hits.add(new VectorHit(point.path("id").asText(), point.path("score").asDouble(0d),
                    payloadOf(point.path("payload"))));
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

    private Map<String, Object> filter(String catalogVersion) {
        return Map.of("must", List.of(Map.of("key", FIELD_CATALOG_VERSION, "match",
                Map.of("value", catalogVersion))));
    }

    private Map<String, Object> catalogFilter(String catalogId) {
        return Map.of("must",
                List.of(Map.of("key", "catalogId", "match", Map.of("value", catalogId))));
    }

}
