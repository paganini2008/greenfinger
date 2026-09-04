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

package com.github.greenfinger.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.WebCrawlerException;

/**
 * 
 * @Description: RestJsonClientTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class RestJsonClientTest {

    private StubServer server;
    private RestJsonClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new StubServer();
        client = new RestJsonClient(2000, 5000);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void getsAndParsesJson() {
        server.on("GET", "/thing", 200, "{\"name\":\"value\",\"count\":3}");

        var node = client.get(server.url() + "/thing");
        assertThat(node.path("name").asText()).isEqualTo("value");
        assertThat(node.path("count").asInt()).isEqualTo(3);
    }

    @Test
    void putsPostsAndDeletes() {
        server.on("PUT", "/thing", 200, "{}").on("POST", "/thing", 200, "{}")
                .on("DELETE", "/thing", 200, "{}");

        client.put(server.url() + "/thing", Map.of("a", 1));
        client.post(server.url() + "/thing", Map.of("b", 2));
        client.post(server.url() + "/thing");
        client.delete(server.url() + "/thing");

        assertThat(server.requestsFor("PUT", "/thing").get(0).body()).contains("\"a\":1");
        assertThat(server.requestsFor("POST", "/thing").get(0).body()).contains("\"b\":2");
        // a body-less post really sends nothing; some endpoints reject one that carries a body
        assertThat(server.requestsFor("POST", "/thing").get(1).body()).isEmpty();
        assertThat(server.requestsFor("DELETE", "/thing")).hasSize(1);
    }

    @Test
    @DisplayName("the bulk api takes newline delimited json, not a json document")
    void postsNdjson() {
        server.on("POST", "/_bulk", 200, "{\"errors\":false}");

        client.postNdjson(server.url() + "/_bulk", "{\"index\":{}}\n{\"a\":1}\n");
        assertThat(server.requestsFor("POST", "/_bulk").get(0).body())
                .isEqualTo("{\"index\":{}}\n{\"a\":1}\n");
    }

    @Test
    @DisplayName("a missing resource is absence, not failure; other errors still throw")
    void existsDistinguishes404FromFailure() {
        server.on("GET", "/present", 200, "{}").on("GET", "/broken", 500, "boom");

        assertThat(client.exists(server.url() + "/present")).isTrue();
        assertThat(client.exists(server.url() + "/absent")).isFalse();
        assertThatThrownBy(() -> client.exists(server.url() + "/broken"))
                .isInstanceOf(WebCrawlerException.class);
    }

    @Test
    void reportsTheStatusAndBodyOfAFailure() {
        server.on("GET", "/bad", 400, "{\"error\":\"malformed\"}");

        assertThatThrownBy(() -> client.get(server.url() + "/bad"))
                .isInstanceOf(WebCrawlerException.class).hasMessageContaining("400")
                .hasMessageContaining("malformed");
    }

    @Test
    void sendsCredentialsWhenGiven() {
        server.on("GET", "/secure", 200, "{}");
        new RestJsonClient(2000, 5000, RestJsonClient.basicAuth("user", "pass"))
                .get(server.url() + "/secure");

        assertThat(server.requestsFor("GET", "/secure").get(0).authorization())
                .startsWith("Basic ");
    }

    @Test
    void basicAuthNeedsAUsername() {
        assertThat(RestJsonClient.basicAuth(null, "pass")).isNull();
        assertThat(RestJsonClient.basicAuth("", "pass")).isNull();
        assertThat(RestJsonClient.basicAuth("user", null)).startsWith("Basic ");
    }

    @Test
    void anEmptyResponseBodyIsAnEmptyObject() {
        server.on("GET", "/empty", 200, "");
        assertThat(client.get(server.url() + "/empty").isEmpty()).isTrue();
    }

    @Test
    void anUnreachableHostIsReported() {
        assertThatThrownBy(() -> client.get("http://127.0.0.1:1/nope"))
                .isInstanceOf(WebCrawlerException.class).hasMessageContaining("Request failed");
    }

}
