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
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.core.output.IndexAdmin;
import com.github.greenfinger.core.output.SearchRequest;
import com.github.greenfinger.core.output.SearchResponse;
import com.github.greenfinger.core.output.SearchResult;
import com.github.greenfinger.core.output.Searcher;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.RestJsonClient;

/**
 * Searches the Elasticsearch output path.
 *
 * <p>
 * The title is weighted above the body, and matching passages come back marked, because a list of
 * bare titles rarely tells you which result you wanted.
 * 
 * @Description: ElasticsearchSearcher
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class ElasticsearchSearcher implements Searcher {

    /**
     * Elasticsearch's own {@code index.max_result_window}. Raising it on the server trades memory
     * for deep paging; the cursor costs nothing, so that is the way past it.
     */
    static final int MAX_RESULT_WINDOW = 10_000;

    private final OutputProperties.Index config;
    private final RestJsonClient client;
    private final String baseUrl;

    public ElasticsearchSearcher(OutputProperties.Index config) {
        this.config = config;
        this.baseUrl = StringUtils.stripEnd(config.getUris().split(",")[0].trim(), "/");
        this.client = new RestJsonClient(config.getConnectTimeout(), config.getReadTimeout(),
                RestJsonClient.basicAuth(config.getUsername(), config.getPassword()));
    }

    @Override
    public String getName() {
        return "elasticsearch";
    }

    /**
     * The indices a request touches.
     *
     * <p>
     * One index per catalog, so a search that names its catalogs names that many indices, and one
     * that names none takes the prefix wildcard. Elasticsearch scores a multi-index search from
     * the combined term statistics, so narrowing to the indices actually in play makes the request
     * cheaper without making the ranking differ from what a search of everything would have
     * produced for the same documents.
     *
     * <p>
     * The {@code catalogVersion} filter still does the version half of the work: an index holds
     * every version of its catalog, and only the published one should be visible.
     */
    private String indexPattern(SearchRequest request) {
        if (request.getCatalogVersions() == null || request.getCatalogVersions().isEmpty()) {
            return config.getPrefix() + "-*";
        }
        return request.getCatalogVersions().stream()
                .map(catalogVersion -> IndexAdmin.indexOf(config.getPrefix(),
                        IndexAdmin.catalogIdOf(catalogVersion)))
                .distinct().collect(java.util.stream.Collectors.joining(","));
    }

    /**
     * Pushes detail pages above listings.
     *
     * <p>
     * A listing matches a search term as readily as the page it links to, and is almost never what
     * someone wanted. The two are told apart without any classification: a listing is mostly links
     * and little prose, a detail page the reverse. So prose lifts a document and links push it
     * down. Both go through a logarithm, which keeps the adjustment to a factor of roughly two or
     * three -- enough to reorder documents of similar relevance, not enough to float an irrelevant
     * page above a relevant one.
     */
    /**
     * The sort values Elasticsearch attached to a hit, which are what the next page resumes from.
     */
    private List<Object> sortValuesOf(JsonNode hit) {
        JsonNode sort = hit.path("sort");
        if (sort.isMissingNode() || !sort.isArray()) {
            return null;
        }
        List<Object> values = new ArrayList<>();
        for (JsonNode value : sort) {
            values.add(value.isNumber() ? value.numberValue() : value.asText());
        }
        return values;
    }

    private Map<String, Object> preferDetail(Map<String, Object> matched) {
        // link density, the metric boilerplate detection has used since Boilerpipe: anchor text
        // over total text. Near one for a listing, near zero for an article, and unaffected by how
        // long the page happens to be, which a raw link count is not.
        String script = "double t = doc['textLength'].size() == 0 ? 0 : doc['textLength'].value; "
                + "double a = doc['linkTextLength'].size() == 0 ? 0 : doc['linkTextLength'].value; "
                + "if (t <= 0) { return 0.5; } "
                + "double density = a / t; if (density > 1) { density = 1; } "
                + "return 1.5 - density;";

        Map<String, Object> functionScore = new LinkedHashMap<>();
        functionScore.put("query", matched);
        functionScore.put("script_score", Map.of("script", Map.of("source", script)));
        functionScore.put("boost_mode", "multiply");
        return Map.of("function_score", functionScore);
    }

    @Override
    public SearchResponse search(SearchRequest request) {
        long start = System.currentTimeMillis();

        List<Map<String, Object>> filters = new ArrayList<>();
        if (StringUtils.isNotBlank(request.getCat())) {
            filters.add(Map.of("term", Map.of("cat", request.getCat())));
        }
        if (request.getCatalogVersions() != null && !request.getCatalogVersions().isEmpty()) {
            // one terms clause covers however many catalogs are in play, each at its own current
            // version, without a nest of and/or
            filters.add(Map.of("terms",
                    Map.of("catalogVersion", request.getCatalogVersions())));
        }

        Map<String, Object> match = StringUtils.isNotBlank(request.getKeyword())
                ? Map.of("multi_match",
                        Map.of("query", request.getKeyword(), "fields",
                                List.of("title^2", "content")))
                : Map.of("match_all", Map.of());

        Map<String, Object> matched =
                Map.of("bool", Map.of("must", List.of(match), "filter", filters));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", request.getPageSize());
        body.put("query", request.isPreferDetailPages() ? preferDetail(matched) : matched);
        // an explicit sort with a unique tiebreaker is what makes a cursor possible at all: without
        // one, two documents of equal score could swap places between pages and a hit be shown
        // twice or skipped
        body.put("sort", List.of(Map.of("_score", "desc"), Map.of("id", "asc")));

        if (request.getCursor() != null && !request.getCursor().isEmpty()) {
            body.put("search_after", request.getCursor());
        } else {
            int from = Math.max(0, (request.getPage() - 1) * request.getPageSize());
            if (from + request.getPageSize() > MAX_RESULT_WINDOW) {
                throw new WebCrawlerException("Page " + request.getPage()
                        + " is past Elasticsearch's " + MAX_RESULT_WINDOW
                        + " result ceiling. Page forward with the cursor from the previous"
                        + " response instead of jumping to a page number.");
            }
            body.put("from", from);
        }
        body.put("highlight",
                Map.of("pre_tags", List.of("<em>"), "post_tags", List.of("</em>"), "fields",
                        Map.of("content", Map.of("fragment_size", 160, "number_of_fragments", 2),
                                "title", Map.of())));
        body.put("_source",
                List.of("title", "url", "cat", "catalog", "version", "catalogVersion",
                        "htmlFilePath", "createTime"));

        JsonNode response =
                client.post(baseUrl + "/" + indexPattern(request) + "/_search?ignore_unavailable=true",
                        body);
        JsonNode hits = response.path("hits");

        List<Object> nextCursor = null;
        List<SearchResult> results = new ArrayList<>();
        for (JsonNode hit : hits.path("hits")) {
            nextCursor = sortValuesOf(hit);
            JsonNode source = hit.path("_source");
            List<String> highlights = new ArrayList<>();
            hit.path("highlight").path("content").forEach(node -> highlights.add(node.asText()));
            hit.path("highlight").path("title").forEach(node -> highlights.add(node.asText()));

            results.add(SearchResult.builder().id(hit.path("_id").asText())
                    .title(source.path("title").asText(""))
                    .url(source.path("url").asText(""))
                    .cat(source.path("cat").asText(null))
                    .catalog(source.path("catalog").asText(null))
                    .version(source.path("version").isMissingNode() ? null
                            : source.path("version").asInt())
                    .createTime(source.path("createTime").isMissingNode() ? null
                            : new Date(source.path("createTime").asLong()))
                    .score(hit.path("_score").asDouble(0d)).highlights(highlights).build());
        }

        return SearchResponse.builder().results(results)
                .nextCursor(results.size() < request.getPageSize() ? null : nextCursor)
                .total(hits.path("total").path("value").asLong(results.size()))
                .page(request.getPage()).pageSize(request.getPageSize())
                .elapsedMillis(System.currentTimeMillis() - start).build();
    }

}
