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
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawledPage;
import com.github.greenfinger.core.model.ContentMode;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.output.OutputFixtures;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.StubServer;

/**
 * 
 * @Description: ElasticsearchOutputChannelTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
class ElasticsearchOutputChannelTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StubServer server;
    private OutputProperties.Index config;

    @BeforeEach
    void setUp() throws Exception {
        server = new StubServer();
        server.on("GET", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa", 404, "");
        server.on("PUT", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa", 200, "{\"acknowledged\":true}");
        server.on("POST", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa/_bulk", 200, "{\"errors\":false,\"items\":[]}");
        server.on("POST", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa/_refresh", 200, "{}");

        config = new OutputProperties.Index();
        config.setUris(server.url());
        config.setBatchSize(2);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void createsTheIndexWhenItIsMissing() throws Exception {
        try (ElasticsearchOutputChannel channel = new ElasticsearchOutputChannel(config)) {
            channel.open(OutputFixtures.catalogDetails());
            // from the catalog's id, so a rename cannot orphan the index
            assertThat(channel.getIndexName())
                    .isEqualTo("greenfinger-" + OutputFixtures.CATALOG_ID);
        }
        assertThat(server.requestsFor("PUT", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa")).hasSize(1);
    }

    @Test
    void mappingHasNestedImagesAndTheCatalogVersionKeyword() throws Exception {
        try (ElasticsearchOutputChannel channel = new ElasticsearchOutputChannel(config)) {
            channel.open(OutputFixtures.catalogDetails());
        }
        String body = server.requestsFor("PUT", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa").get(0).body();
        JsonNode properties = objectMapper.readTree(body).path("mappings").path("properties");
        assertThat(properties.path("catalogVersion").path("type").asText()).isEqualTo("keyword");
        assertThat(properties.path("images").path("type").asText()).isEqualTo("nested");
        assertThat(properties.path("images").path("properties").path("context").path("type")
                .asText()).isEqualTo("text");
    }

    @Test
    void documentIdIsTheResourceId() throws Exception {
        CatalogDetails details = OutputFixtures.catalogDetails();
        CrawledPage page = OutputFixtures.page("https://www.example.com/a", "A", "text");
        var record = OutputFixtures.record(page);

        try (ElasticsearchOutputChannel channel = new ElasticsearchOutputChannel(config)) {
            channel.open(details);
            channel.write(OutputFixtures.payload(details, page));
            channel.flush();
        }
        String ndjson = server.requestsFor("POST", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa/_bulk").get(0).body();
        assertThat(ndjson).contains(record.resource().getId());
    }

    @Test
    void documentCarriesTheCatalogVersion() throws Exception {
        CatalogDetails details = OutputFixtures.catalogDetails();
        CrawledPage page = OutputFixtures.page("https://www.example.com/a", "A", "text");

        try (ElasticsearchOutputChannel channel = new ElasticsearchOutputChannel(config)) {
            channel.open(details);
            channel.write(OutputFixtures.payload(details, page));
            channel.flush();
        }
        String ndjson = server.requestsFor("POST", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa/_bulk").get(0).body();
        assertThat(ndjson).contains("\"catalogVersion\":\"" + OutputFixtures.CATALOG_ID + ":0\"");
    }

    @Test
    void batchesUpToTheConfiguredSize() throws Exception {
        CatalogDetails details = OutputFixtures.catalogDetails();
        try (ElasticsearchOutputChannel channel = new ElasticsearchOutputChannel(config)) {
            channel.open(details);
            for (String path : List.of("/a", "/b", "/c")) {
                channel.write(OutputFixtures.payload(details,
                        OutputFixtures.page("https://www.example.com" + path, path, "t")));
            }
            channel.flush();
        }
        // two of three go in the first bulk, the third on flush
        assertThat(server.requestsFor("POST", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa/_bulk")).hasSize(2);
    }

    @Test
    void imagesAreNestedInTheDocument() throws Exception {
        CatalogDetails details = OutputFixtures.catalogDetails();
        CrawledPage page = OutputFixtures.pageWithImage("https://www.example.com/b", "B", "text");

        try (ElasticsearchOutputChannel channel = new ElasticsearchOutputChannel(config)) {
            channel.open(details);
            channel.write(OutputFixtures.payload(details, page));
            channel.flush();
        }
        String ndjson = server.requestsFor("POST", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa/_bulk").get(0).body();
        assertThat(ndjson).contains("a picture").contains("words around the picture");
    }

    @Test
    void textOnlyModeLeavesTheImagesOut() throws Exception {
        CatalogDetails details =
                OutputFixtures.catalogDetails(Set.of(OutputType.INDEX), ContentMode.TEXT);
        CrawledPage page = OutputFixtures.pageWithImage("https://www.example.com/b", "B", "text");

        try (ElasticsearchOutputChannel channel = new ElasticsearchOutputChannel(config)) {
            channel.open(details);
            channel.write(OutputFixtures.payload(details, page));
            channel.flush();
        }
        String ndjson = server.requestsFor("POST", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa/_bulk").get(0).body();
        assertThat(ndjson).contains("\"images\":[]").doesNotContain("a picture");
    }

    @Test
    void refreshesOnClose() throws Exception {
        try (ElasticsearchOutputChannel channel = new ElasticsearchOutputChannel(config)) {
            channel.open(OutputFixtures.catalogDetails());
        }
        assertThat(server.requestsFor("POST", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa/_refresh")).hasSize(1);
    }

    @Test
    void reportsTheTypeSoTheCompositeCanOrderIt() {
        ElasticsearchOutputChannel channel = new ElasticsearchOutputChannel(config);
        assertThat(channel.getType()).isEqualTo(OutputType.INDEX);
        // not required: one Elasticsearch hiccup must not destroy a whole crawl
        assertThat(channel.isRequired()).isFalse();
    }

    @Test
    void countsWhatItWrote() throws Exception {
        CatalogDetails details = OutputFixtures.catalogDetails();
        try (ElasticsearchOutputChannel channel = new ElasticsearchOutputChannel(config)) {
            channel.open(details);
            channel.write(OutputFixtures.payload(details,
                    OutputFixtures.page("https://www.example.com/a", "A", "t")));
            channel.flush();
            assertThat(channel.getWrittenCount()).isEqualTo(1L);
        }
    }

    @Test
    void mappingUsesTheConfiguredAnalyzer() throws Exception {
        config.setAnalyzer("ik_max_word");
        try (ElasticsearchOutputChannel channel = new ElasticsearchOutputChannel(config)) {
            channel.open(OutputFixtures.catalogDetails());
        }
        String body = server.requestsFor("PUT", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa").get(0).body();
        Map<?, ?> parsed = objectMapper.readValue(body, Map.class);
        assertThat(parsed.toString()).contains("ik_max_word");
    }

}
