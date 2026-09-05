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

import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.greenfinger.core.output.IndexAdmin;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.RestJsonClient;
import lombok.extern.slf4j.Slf4j;

/**
 * Index housekeeping: counting, deleting a version, and reclaiming the space afterwards.
 *
 * <p>
 * One index per catalog, named {@code <prefix>-<catalogId>}, holding every version of it. So
 * removing a version is a delete by query -- a marked deletion, whose space comes back when
 * segments merge, which is why {@code forcemergeAfterDelete} exists and is off by default -- while
 * removing a whole catalog is dropping the index, which is immediate and complete.
 * 
 * @Description: ElasticsearchIndexAdmin
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class ElasticsearchIndexAdmin implements IndexAdmin {

    private final OutputProperties.Index config;
    private final RestJsonClient client;
    private final String baseUrl;

    public ElasticsearchIndexAdmin(OutputProperties.Index config) {
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
    public String getLocation() {
        return baseUrl;
    }

    @Override
    public String getIndexPrefix() {
        return config.getPrefix();
    }

    @Override
    public String indexOf(String catalogId) {
        return IndexAdmin.indexOf(config.getPrefix(), catalogId);
    }

    @Override
    public boolean indexExists(String catalogId) {
        return client.exists(baseUrl + "/" + indexOf(catalogId));
    }

    @Override
    public long countByCatalogVersion(String catalogVersion) {
        String index = indexOf(IndexAdmin.catalogIdOf(catalogVersion));
        if (!client.exists(baseUrl + "/" + index)) {
            return 0L;
        }
        JsonNode response = client.post(baseUrl + "/" + index + "/_count", query(catalogVersion));
        return response.path("count").asLong(0L);
    }

    @Override
    public long countByCatalog(String catalogId) {
        String index = indexOf(catalogId);
        if (!client.exists(baseUrl + "/" + index)) {
            return 0L;
        }
        return client.post(baseUrl + "/" + index + "/_count", Map.of()).path("count").asLong(0L);
    }

    @Override
    public long deleteByCatalogVersion(String catalogVersion) {
        String index = indexOf(IndexAdmin.catalogIdOf(catalogVersion));
        if (!client.exists(baseUrl + "/" + index)) {
            return 0L;
        }
        JsonNode response = client.post(
                baseUrl + "/" + index + "/_delete_by_query?refresh=true&conflicts=proceed",
                query(catalogVersion));
        long deleted = response.path("deleted").asLong(0L);
        if (config.isForcemergeAfterDelete()) {
            forcemerge(index);
        }
        return deleted;
    }

    @Override
    public long deleteAllVersions(String catalogId) {
        String index = indexOf(catalogId);
        if (!client.exists(baseUrl + "/" + index)) {
            return 0L;
        }
        JsonNode response = client.post(
                baseUrl + "/" + index + "/_delete_by_query?refresh=true&conflicts=proceed",
                Map.of("query", Map.of("term", Map.of("catalogId", catalogId))));
        long deleted = response.path("deleted").asLong(0L);
        if (config.isForcemergeAfterDelete()) {
            forcemerge(index);
        }
        return deleted;
    }

    /**
     * Dropping the index rather than marking a corpus of documents for deletion and waiting for a
     * merge to reclaim them.
     */
    @Override
    public long deleteByCatalog(String catalogId) {
        String index = indexOf(catalogId);
        if (!client.exists(baseUrl + "/" + index)) {
            return 0L;
        }
        long counted = countByCatalog(catalogId);
        client.delete(baseUrl + "/" + index);
        return counted;
    }

    private void forcemerge(String index) {
        try {
            client.post(baseUrl + "/" + index + "/_forcemerge?only_expunge_deletes=true");
        } catch (Exception e) {
            log.warn("Force merge failed: {}", e.getMessage());
        }
    }

    private Map<String, Object> query(String catalogVersion) {
        return Map.of("query", Map.of("term", Map.of("catalogVersion", catalogVersion)));
    }

    /**
     * Every index the prefix owns. Asked for by pattern rather than listing the server's own,
     * because a shared cluster holds other people's indices and none of them is ours to report.
     */
    @Override
    public List<String> listIndices() {
        JsonNode response =
                client.get(baseUrl + "/_cat/indices/" + config.getPrefix()
                        + "-*?format=json&h=index");
        return java.util.stream.StreamSupport.stream(response.spliterator(), false)
                .map(node -> node.path("index").asText()).filter(StringUtils::isNotBlank).sorted()
                .toList();
    }

    @Override
    public void refresh() {
        // every index at once: a refresh is asked for after something changed, and what changed
        // is not always one catalog
        try {
            client.post(baseUrl + "/" + config.getPrefix() + "-*/_refresh");
        } catch (Exception e) {
            log.debug("Refresh failed: {}", e.getMessage());
        }
    }

}
