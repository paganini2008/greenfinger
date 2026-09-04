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

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.output.SearchRequest;
import com.github.greenfinger.core.output.SearchResponse;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.StubServer;

/**
 * 
 * @Description: ElasticsearchSearcherTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class ElasticsearchSearcherTest {

    private static final String HITS = """
            {"hits":{"total":{"value":42},"hits":[
              {"_id":"abc","_score":1.5,
               "_source":{"title":"A page","url":"https://a.com/x","cat":"news",
                          "catalog":"site","version":0,"createTime":1700000000000},
               "highlight":{"content":["a <em>match</em> here"],"title":["A <em>page</em>"]}}
            ]}}
            """;

    private StubServer server;
    private OutputProperties.Index config;

    @BeforeEach
    void setUp() throws Exception {
        server = new StubServer();
        config = new OutputProperties.Index();
        config.setUris(server.url());
        config.setPrefix("greenfinger");
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    @DisplayName("hits come back with their matching passages marked")
    void parsesHitsAndHighlights() {
        server.on("POST", "/greenfinger-*", 200, HITS);

        SearchResponse response = new ElasticsearchSearcher(config)
                .search(SearchRequest.builder().keyword("match").build());

        assertThat(response.getTotal()).isEqualTo(42);
        assertThat(response.getResults()).hasSize(1);
        var result = response.getResults().get(0);
        assertThat(result.getTitle()).isEqualTo("A page");
        assertThat(result.getUrl()).isEqualTo("https://a.com/x");
        assertThat(result.getCat()).isEqualTo("news");
        assertThat(result.getScore()).isEqualTo(1.5d);
        assertThat(result.getCreateTime()).isNotNull();
        assertThat(result.getHighlights()).contains("a <em>match</em> here",
                "A <em>page</em>");
    }

    @Test
    @DisplayName("the title is weighted above the body, and passages are asked for")
    void buildsAQueryThatWeightsTheTitle() {
        server.on("POST", "/greenfinger-*", 200, HITS);
        new ElasticsearchSearcher(config)
                .search(SearchRequest.builder().keyword("beef").build());

        String body = server.requestsFor("POST", "/greenfinger-*").get(0).body();
        assertThat(body).contains("title^2").contains("multi_match").contains("highlight");
    }

    @Test
    @DisplayName("one index holds every catalog and every version")
    void alwaysSearchesTheOneIndex() {
        server.on("POST", "/greenfinger-*", 200, HITS);
        new ElasticsearchSearcher(config).search(SearchRequest.builder().keyword("x").build());

        assertThat(server.requestsFor("POST", "/greenfinger-*").get(0).path())
                .isEqualTo("/greenfinger-*/_search");
    }

    @Test
    @DisplayName("several catalogs at their own versions are one terms clause")
    void filtersByCatalogVersion() {
        server.on("POST", "/greenfinger-a,greenfinger-b", 200, HITS);
        new ElasticsearchSearcher(config).search(SearchRequest.builder().keyword("x")
                .catalogVersions(java.util.List.of("a:6", "b:5")).build());

        String body = server.requestsFor("POST", "/greenfinger-a,greenfinger-b").get(0).body();
        assertThat(body).contains("\"terms\"").contains("\"a:6\"").contains("\"b:5\"");
    }

    @Test
    void filtersByCategory() {
        server.on("POST", "/greenfinger-cat", 200, HITS);
        new ElasticsearchSearcher(config).search(SearchRequest.builder().keyword("x").cat("news")
                .catalogVersions(java.util.List.of("cat:1")).build());

        String body = server.requestsFor("POST", "/greenfinger-cat").get(0).body();
        assertThat(body).contains("\"cat\":\"news\"");
    }

    @Test
    @DisplayName("no keyword lists everything rather than matching nothing")
    void anEmptyKeywordMatchesAll() {
        server.on("POST", "/greenfinger-*", 200, HITS);
        new ElasticsearchSearcher(config).search(SearchRequest.builder().build());

        assertThat(server.requestsFor("POST", "/greenfinger-*").get(0).body())
                .contains("match_all");
    }

    @Test
    void paginates() {
        server.on("POST", "/greenfinger-*", 200, HITS);
        SearchResponse response = new ElasticsearchSearcher(config)
                .search(SearchRequest.builder().keyword("x").page(3).pageSize(10).build());

        assertThat(server.requestsFor("POST", "/greenfinger-*").get(0).body())
                .contains("\"from\":20").contains("\"size\":10");
        assertThat(response.getPage()).isEqualTo(3);
        assertThat(response.getTotalPages()).isEqualTo(5);
    }

    @Test
    void namesItself() {
        assertThat(new ElasticsearchSearcher(config).getName()).isEqualTo("elasticsearch");
    }


    @org.junit.jupiter.api.DisplayName("detail pages are pushed above listings by default")
    @Test
    void prefersDetailPages() {
        server.on("POST", "/greenfinger-*", 200, HITS);
        new ElasticsearchSearcher(config).search(SearchRequest.builder().keyword("x").build());

        String body = server.requestsFor("POST", "/greenfinger-*").get(0).body();
        assertThat(body).contains("function_score").contains("textLength")
                .contains("linkTextLength");
    }

    @Test
    void theDetailBoostCanBeTurnedOff() {
        server.on("POST", "/greenfinger-*", 200, HITS);
        new ElasticsearchSearcher(config)
                .search(SearchRequest.builder().keyword("x").preferDetailPages(false).build());

        String body = server.requestsFor("POST", "/greenfinger-*").get(0).body();
        assertThat(body).doesNotContain("function_score");
    }

}
