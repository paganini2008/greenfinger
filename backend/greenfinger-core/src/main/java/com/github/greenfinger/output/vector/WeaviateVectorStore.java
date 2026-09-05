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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.RestJsonClient;
import lombok.extern.slf4j.Slf4j;

/**
 * Weaviate over its REST api, with vectors supplied rather than generated -- the class is created
 * with {@code vectorizer: none} because the embedding happens here.
 * 
 * @Description: WeaviateVectorStore
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class WeaviateVectorStore implements VectorStore {

    private final OutputProperties.Vector.Weaviate config;
    private final RestJsonClient client;
    private final String baseUrl;

    public WeaviateVectorStore(OutputProperties.Vector.Weaviate config) {
        this.config = config;
        String url = StringUtils.stripEnd(config.getUrl(), "/");
        // the endpoint is accepted with or without the /v1 suffix
        this.baseUrl = url.endsWith("/v1") ? url : url + "/v1";
        this.client = StringUtils.isNotBlank(config.getApiKey())
                ? new RestJsonClient(10000, 60000, "Bearer " + config.getApiKey())
                : new RestJsonClient(10000, 60000);
    }

    @Override
    public String getName() {
        return "weaviate";
    }

    /**
     * Weaviate class names must start with a capital, so the collection name is adjusted rather
     * than rejected.
     */
    static String className(String collection) {
        String cleaned = collection.replaceAll("[^A-Za-z0-9_]", "_");
        return cleaned.substring(0, 1).toUpperCase(Locale.ROOT) + cleaned.substring(1);
    }

    /**
     * The properties every payload may carry. Weaviate rejects a query that mentions a field the
     * class does not declare -- and rejects the whole query, not just that field -- so the schema
     * and the payload have to be kept in step from one place.
     */
    private static final List<String> TEXT_PROPERTIES =
            List.of("catalogVersion", "catalogId", "catalog", "resourceId", "imageId", "url",
                    "title", "cat", "chunkText", "alt", "context", "imageFilePath",
                    "htmlFilePath", "contentType");

    private static final List<String> NUMBER_PROPERTIES =
            List.of("version", "chunkIndex", "width", "height", "linkCount", "textLength",
                    "linkTextLength");

    @Override
    public void ensureCollection(String collection, int dimensions) {
        String name = className(collection);
        if (client.exists(baseUrl + "/schema/" + name)) {
            addMissingProperties(name);
            return;
        }
        if (!config.isCreateCollectionIfMissing()) {
            throw new WebCrawlerException("No such Weaviate class: " + name);
        }
        List<Map<String, Object>> properties = new ArrayList<>();
        TEXT_PROPERTIES.forEach(
                field -> properties.add(Map.of("name", field, "dataType", List.of("text"))));
        NUMBER_PROPERTIES.forEach(
                field -> properties.add(Map.of("name", field, "dataType", List.of("int"))));

        client.post(baseUrl + "/schema",
                Map.of("class", name, "vectorizer", "none", "vectorIndexConfig",
                        Map.of("distance", config.getDistance()), "properties", properties));
        log.info("Created Weaviate class '{}' for {} dimensions", name, dimensions);
    }

    /**
     * Brings a class created by an earlier version up to date. Without this a query naming a field
     * added since would fail outright rather than simply returning nothing for it.
     */
    private void addMissingProperties(String name) {
        JsonNode existing = client.get(baseUrl + "/schema/" + name);
        Set<String> present = new LinkedHashSet<>();
        existing.path("properties")
                .forEach(property -> present.add(property.path("name").asText()));

        for (String field : TEXT_PROPERTIES) {
            addProperty(name, present, field, "text");
        }
        for (String field : NUMBER_PROPERTIES) {
            addProperty(name, present, field, "int");
        }
    }

    private void addProperty(String name, Set<String> present, String field, String dataType) {
        // Weaviate lower-cases the first letter of a property name
        if (present.contains(field) || present.contains(lowerFirst(field))) {
            return;
        }
        try {
            client.post(baseUrl + "/schema/" + name + "/properties",
                    Map.of("name", field, "dataType", List.of(dataType)));
            log.info("Added property '{}' to Weaviate class '{}'", field, name);
        } catch (Exception e) {
            log.warn("Could not add property '{}' to '{}': {}", field, name, e.getMessage());
        }
    }

    private static String lowerFirst(String value) {
        return value.substring(0, 1).toLowerCase(Locale.ROOT) + value.substring(1);
    }

    @Override
    public void upsert(String collection, List<VectorPoint> points) {
        if (points.isEmpty()) {
            return;
        }
        String name = className(collection);
        List<Map<String, Object>> objects = new ArrayList<>(points.size());
        for (VectorPoint point : points) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("class", name);
            one.put("id", point.getId());
            one.put("vector", point.getVector());
            one.put("properties", point.getPayload());
            objects.add(one);
        }
        JsonNode response = client.post(baseUrl + "/batch/objects", Map.of("objects", objects));
        for (JsonNode item : response) {
            JsonNode errors = item.path("result").path("errors");
            if (!errors.isMissingNode() && !errors.isNull()) {
                // rejected objects are lost data, so this has to be loud. The composite channel
                // catches it, counts it and lets the crawl carry on, and a replay can fill the gap
                throw new WebCrawlerException("Weaviate rejected an object: "
                        + StringUtils.abbreviate(errors.toString(), 300));
            }
        }
    }

    @Override
    public long deleteByCatalogVersion(String collection, String catalogVersion) {
        String name = className(collection);
        if (!client.exists(baseUrl + "/schema/" + name)) {
            return 0L;
        }
        JsonNode response = client.delete(baseUrl + "/batch/objects",
                Map.of("match", Map.of("class", name, "where", where(catalogVersion))));
        return response.path("results").path("successful").asLong(-1L);
    }

    @Override
    public long deleteByCatalog(String collection, String catalogId) {
        String name = className(collection);
        if (!client.exists(baseUrl + "/schema/" + name)) {
            return 0L;
        }
        JsonNode response = client.delete(baseUrl + "/batch/objects",
                Map.of("match", Map.of("class", name, "where", whereCatalog(catalogId))));
        return response.path("results").path("successful").asLong(-1L);
    }

    @Override
    public long countByCatalog(String collection, String catalogId) {
        String name = className(collection);
        if (!client.exists(baseUrl + "/schema/" + name)) {
            return 0L;
        }
        String query = String.format(
                "{ Aggregate { %s(where: {path:[\"catalogId\"], operator: Equal, "
                        + "valueText: \"%s\"}) { meta { count } } } }",
                name, catalogId);
        JsonNode response = client.post(baseUrl + "/graphql", Map.of("query", query));
        return response.path("data").path("Aggregate").path(name).path(0).path("meta")
                .path("count").asLong(0L);
    }

    @Override
    public long count(String collection, String catalogVersion) {
        String name = className(collection);
        if (!client.exists(baseUrl + "/schema/" + name)) {
            return 0L;
        }
        String query = String.format(
                "{ Aggregate { %s(where: {path:[\"catalogVersion\"], operator: Equal, "
                        + "valueText: \"%s\"}) { meta { count } } } }",
                name, catalogVersion);
        JsonNode response = client.post(baseUrl + "/graphql", Map.of("query", query));
        return response.path("data").path("Aggregate").path(name).path(0).path("meta")
                .path("count").asLong(0L);
    }

    /**
     * Weaviate has no plain vector-search endpoint on its REST api, so this goes through GraphQL.
     */
    @Override
    public List<VectorHit> search(String collection, float[] vector, int limit, int offset,
            List<String> catalogVersions) {
        String name = className(collection);
        if (!client.exists(baseUrl + "/schema/" + name)) {
            return List.of();
        }
        StringBuilder near = new StringBuilder("nearVector: {vector: [");
        for (int i = 0; i < vector.length; i++) {
            near.append(i > 0 ? "," : "").append(vector[i]);
        }
        near.append("]}");

        StringBuilder filter = new StringBuilder();
        if (catalogVersions != null && !catalogVersions.isEmpty()) {
            filter.append(", where: {operator: Or, operands: [");
            for (int i = 0; i < catalogVersions.size(); i++) {
                filter.append(i > 0 ? "," : "")
                        .append("{path:[\"catalogVersion\"], operator: Equal, valueText: \"")
                        .append(catalogVersions.get(i)).append("\"}");
            }
            filter.append("]}");
        }

        String fields = String.join(" ", TEXT_PROPERTIES) + " "
                + String.join(" ", NUMBER_PROPERTIES);
        String paging = offset > 0 ? String.format(", offset: %d", offset) : "";
        String query = String.format("{ Get { %s(%s, limit: %d%s%s) { %s "
                + "_additional { id distance } } } }", name, near, limit, paging, filter, fields);
        JsonNode response = client.post(baseUrl + "/graphql", Map.of("query", query));

        List<VectorHit> hits = new ArrayList<>();
        for (JsonNode object : response.path("data").path("Get").path(name)) {
            Map<String, Object> payload = new LinkedHashMap<>();
            object.fields().forEachRemaining(entry -> {
                if (!"_additional".equals(entry.getKey())) {
                    JsonNode value = entry.getValue();
                    payload.put(entry.getKey(),
                            value.isNumber() ? value.numberValue() : value.asText(null));
                }
            });
            JsonNode additional = object.path("_additional");
            // Weaviate reports distance; nearer is smaller, so it is turned into a similarity
            hits.add(new VectorHit(additional.path("id").asText(),
                    1d - additional.path("distance").asDouble(0d), payload));
        }
        return hits;
    }

    private Map<String, Object> where(String catalogVersion) {
        return Map.of("path", List.of("catalogVersion"), "operator", "Equal", "valueText",
                catalogVersion);
    }

    private Map<String, Object> whereCatalog(String catalogId) {
        return Map.of("path", List.of("catalogId"), "operator", "Equal", "valueText", catalogId);
    }


    /**
     * A class in Weaviate is a class: the width is part of the name here too, and the schema
     * endpoint is where the names are.
     */
    @Override
    public List<String> collectionsMatching(String prefix) {
        JsonNode response = client.get(baseUrl + "/schema");
        List<String> names = new ArrayList<>();
        for (JsonNode clazz : response.path("classes")) {
            String name = clazz.path("class").asText("");
            // Weaviate capitalises class names, and the configured prefix is lower case
            if (name.toLowerCase(Locale.ROOT)
                    .startsWith(prefix.toLowerCase(Locale.ROOT))) {
                names.add(name);
            }
        }
        return names;
    }

}
