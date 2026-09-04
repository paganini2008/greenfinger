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

package com.github.greenfinger.output.index;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.output.OutputChannel;
import com.github.greenfinger.core.output.OutputPayload;
import com.github.greenfinger.core.record.ResourceRecord;
import com.github.greenfinger.core.utils.UrlUtils;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.RestJsonClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Full text search. One index per catalog, named {@code <prefix>-<catalogId>}, holding every
 * version of that catalog; versions are kept apart by the {@code catalogVersion} keyword rather
 * than by separate indices.
 *
 * <p>
 * That one field, of the form {@code <catalogId>:<version>}, does three jobs: a search across
 * catalogs whose current versions differ is a single any-of match, promoting a finished version is
 * a change of the value being matched, and deleting a version is one term query. The vector store
 * carries the identical field, and so does the embedded Lucene index, so all three are queried the
 * same way.
 *
 * <p>
 * The index is named from the catalog's id and never its name, because a name is editable and an
 * index named after one would be orphaned by a rename with nothing left to say what it had held.
 *
 * <p>
 * Talks the REST api directly rather than through the official client, because the client refuses
 * a server whose major version it does not match, and the calls used here are identical across
 * Elasticsearch 7, 8 and 9.
 * 
 * @Description: ElasticsearchOutputChannel
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class ElasticsearchOutputChannel implements OutputChannel {

    private final OutputProperties.Index config;
    private final RestJsonClient client;
    private final List<Map<String, Object>> buffer = new ArrayList<>();
    private final AtomicLong written = new AtomicLong(0);

    @Getter
    private String indexName;
    private String baseUrl;
    private CatalogDetails catalogDetails;

    public ElasticsearchOutputChannel(OutputProperties.Index config) {
        this.config = config;
        this.client = new RestJsonClient(config.getConnectTimeout(), config.getReadTimeout(),
                RestJsonClient.basicAuth(config.getUsername(), config.getPassword()));
    }

    @Override
    public String getName() {
        return "index";
    }

    @Override
    public OutputType getType() {
        return OutputType.INDEX;
    }

    @Override
    public void open(CatalogDetails catalogDetails) {
        this.catalogDetails = catalogDetails;
        this.baseUrl = StringUtils.stripEnd(config.getUris().split(",")[0].trim(), "/");
        this.indexName = com.github.greenfinger.core.output.IndexAdmin.indexOf(config.getPrefix(),
                catalogDetails.getId());
        if (!client.exists(baseUrl + "/" + indexName)) {
            client.put(baseUrl + "/" + indexName, mapping());
            log.info("Created index '{}'", indexName);
        }
        log.info("Indexing into {}/{} as {}", baseUrl, indexName,
                catalogDetails.getCatalogVersion());
    }

    private Map<String, Object> mapping() {
        Map<String, Object> text = Map.of("type", "text", "analyzer", config.getAnalyzer(),
                "search_analyzer", config.getAnalyzer());
        Map<String, Object> keyword = Map.of("type", "keyword");

        Map<String, Object> imageProperties = new LinkedHashMap<>();
        imageProperties.put("imageId", keyword);
        imageProperties.put("imageFilePath", keyword);
        imageProperties.put("contentType", keyword);
        imageProperties.put("sourceUrl", keyword);
        imageProperties.put("width", Map.of("type", "integer"));
        imageProperties.put("height", Map.of("type", "integer"));
        imageProperties.put("alt", text);
        imageProperties.put("title", text);
        // the wording around the tag: what makes an image with no alt attribute findable
        imageProperties.put("context", text);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("title", text);
        properties.put("content", text);
        properties.put("url", keyword);
        properties.put("host", keyword);
        properties.put("cat", keyword);
        properties.put("catalog", keyword);
        properties.put("catalogId", keyword);
        properties.put("catalogVersion", keyword);
        // mirrors _id as an ordinary field, so a cursor can sort on it: sorting on _id itself
        // needs fielddata and Elasticsearch refuses it by default
        properties.put("id", keyword);
        properties.put("referer", keyword);
        properties.put("contentHash", keyword);
        properties.put("htmlFilePath", keyword);
        properties.put("htmlContentFilePath", keyword);
        properties.put("version", Map.of("type", "integer"));
        properties.put("depth", Map.of("type", "integer"));
        // the two ranking signals: a listing is mostly links and little prose, a detail page the
        // reverse, and search boosts on that without anything having to classify pages
        properties.put("linkCount", Map.of("type", "integer"));
        properties.put("textLength", Map.of("type", "integer"));
        properties.put("linkTextLength", Map.of("type", "integer"));
        properties.put("createTime", Map.of("type", "date", "format", "epoch_millis"));
        properties.put("images", Map.of("type", "nested", "properties", imageProperties));

        return Map.of("settings",
                Map.of("number_of_shards", config.getNumberOfShards(), "number_of_replicas",
                        config.getNumberOfReplicas()),
                "mappings", Map.of("properties", properties));
    }

    @Override
    public void write(OutputPayload payload) throws Exception {
        ResourceRecord record = payload.getRecord();
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("_id", record.resource().getId());
        document.put("id", record.resource().getId());
        document.put("title", record.resource().getTitle());
        document.put("content", payload.getText());
        document.put("url", record.resource().getUrl());
        document.put("host", UrlUtils.getHost(record.resource().getUrl()));
        document.put("cat", record.resource().getCat());
        document.put("catalog", catalogDetails.getName());
        document.put("catalogId", record.resource().getCatalogId());
        document.put("catalogVersion",
                record.resource().getCatalogId() + ":" + record.resource().getVersion());
        document.put("referer", record.resource().getReferer());
        document.put("contentHash", record.resource().getContentHash());
        document.put("htmlFilePath", record.resource().getHtmlFilePath());
        document.put("htmlContentFilePath", record.resource().getHtmlContentFilePath());
        document.put("version", record.resource().getVersion());
        document.put("depth", record.resource().getDepth());
        document.put("linkCount",
                record.resource().getLinkCount() != null ? record.resource().getLinkCount() : 0);
        document.put("textLength",
                record.resource().getTextLength() != null ? record.resource().getTextLength() : 0);
        document.put("linkTextLength", record.resource().getLinkTextLength() != null
                ? record.resource().getLinkTextLength()
                : 0);
        document.put("createTime", record.resource().getCreatedAt() != null
                ? record.resource().getCreatedAt().getTime()
                : System.currentTimeMillis());
        document.put("images", imagesOf(payload));

        List<Map<String, Object>> batch = null;
        synchronized (buffer) {
            buffer.add(document);
            if (buffer.size() >= config.getBatchSize()) {
                batch = new ArrayList<>(buffer);
                buffer.clear();
            }
        }
        if (batch != null) {
            bulkIndex(batch);
        }
    }

    /**
     * Nested, so a hit on the page brings its pictures back with it. Empty when the catalog is
     * running in text-only mode, in which case the images are still crawled and stored -- turning
     * them on later is a replay, not a re-crawl.
     */
    private List<Map<String, Object>> imagesOf(OutputPayload payload) {
        if (!payload.getCatalogDetails().getContentMode().includesImages()) {
            return List.of();
        }
        List<Map<String, Object>> images = new ArrayList<>();
        for (ResourceRecord.ImageRecord image : payload.getRecord().images()) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("imageId", image.image().getId());
            one.put("imageFilePath", image.image().getImageFilePath());
            one.put("contentType", image.image().getContentType());
            one.put("sourceUrl", image.reference().getSourceUrl());
            one.put("width", image.image().getWidth());
            one.put("height", image.image().getHeight());
            one.put("alt", image.reference().getAltText());
            one.put("title", image.reference().getTitleText());
            one.put("context", image.reference().getContextText());
            images.add(one);
        }
        return images;
    }

    private void bulkIndex(List<Map<String, Object>> batch) throws Exception {
        if (batch.isEmpty()) {
            return;
        }
        StringBuilder ndjson = new StringBuilder();
        for (Map<String, Object> document : batch) {
            Map<String, Object> source = new LinkedHashMap<>(document);
            Object id = source.remove("_id");
            ndjson.append(client.objectMapper()
                    .writeValueAsString(Map.of("index", Map.of("_id", id)))).append('\n');
            ndjson.append(client.objectMapper().writeValueAsString(source)).append('\n');
        }
        JsonNode response = client.postNdjson(baseUrl + "/" + indexName + "/_bulk",
                ndjson.toString());
        if (response.path("errors").asBoolean(false)) {
            // one rejected document must not cost the batch; report and carry on
            log.warn("Some documents were rejected: {}",
                    StringUtils.abbreviate(response.path("items").toString(), 500));
        }
        written.addAndGet(batch.size());
    }

    @Override
    public void flush() throws Exception {
        List<Map<String, Object>> batch;
        synchronized (buffer) {
            batch = new ArrayList<>(buffer);
            buffer.clear();
        }
        bulkIndex(batch);
    }

    @Override
    public void close() throws Exception {
        flush();
        // make everything just written visible to search straight away
        client.post(baseUrl + "/" + indexName + "/_refresh");
        log.info("Indexed {} document(s) into {}", written.get(), indexName);
    }

    public long getWrittenCount() {
        return written.get();
    }

}
