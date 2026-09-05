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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.output.StubServer;

/**
 * 
 * @Description: EmbeddingClientTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
class EmbeddingClientTest {

    private StubServer server;
    private EmbeddingProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        server = new StubServer();
        properties = new EmbeddingProperties();
        properties.getOpenai().setApiKey("test-key");
        properties.getOpenai().setBaseUrl(server.url() + "/v1");
        properties.getOllama().setUrl(server.url());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void openAiParsesTheResponseAndSendsTheKey() {
        server.on("POST", "/v1/embeddings", 200,
                "{\"data\":[{\"embedding\":[0.1,0.2]},{\"embedding\":[0.3,0.4]}]}");

        OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(properties);
        List<float[]> vectors = client.textToVectors(List.of("one", "two"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(0.1f, 0.2f);
        assertThat(server.requestsFor("POST", "/v1/embeddings").get(0).authorization())
                .isEqualTo("Bearer test-key");
    }

    @Test
    void ollamaParsesItsOwnShape() {
        server.on("POST", "/api/embed", 200, "{\"embeddings\":[[1.0,2.0,3.0]]}");

        OllamaEmbeddingClient client = new OllamaEmbeddingClient(properties);
        assertThat(client.textToVector("hello")).containsExactly(1.0f, 2.0f, 3.0f);
    }

    @Test
    @DisplayName("the width is measured, not configured, so a model tag cannot lie about it")
    void dimensionsAreProbed() {
        server.on("POST", "/api/embed", 200, "{\"embeddings\":[[1.0,2.0,3.0,4.0]]}");

        OllamaEmbeddingClient client = new OllamaEmbeddingClient(properties);
        client.afterPropertiesSet();
        assertThat(client.textDimensions()).isEqualTo(4);
    }

    @Test
    void aShortResponseIsAnError() {
        server.on("POST", "/api/embed", 200, "{\"embeddings\":[[1.0]]}");

        OllamaEmbeddingClient client = new OllamaEmbeddingClient(properties);
        assertThatThrownBy(() -> client.textToVectors(List.of("a", "b")))
                .isInstanceOf(WebCrawlerException.class).hasMessageContaining("1 vectors for 2");
    }

    @Test
    @DisplayName("a text-only client is a first class citizen, not a broken one")
    void imageMethodsAreOptional() {
        OllamaEmbeddingClient client = new OllamaEmbeddingClient(properties);
        assertThat(client.supportsImages()).isFalse();
        assertThatThrownBy(() -> client.imageToVector(new byte[] {1}, "image/png"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not embed images");
        assertThatThrownBy(client::imageDimensions)
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> client.queryToImageVector("a cat"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void openAiIsAlsoTextOnly() {
        assertThat(new OpenAiEmbeddingClient(properties).supportsImages()).isFalse();
    }

    @Test
    void aMissingKeyIsReportedClearly() {
        properties.getOpenai().setApiKey(null);
        if (System.getenv("OPENAI_API_KEY") != null) {
            return; // the environment supplies one; nothing to assert
        }
        assertThatThrownBy(() -> new OpenAiEmbeddingClient(properties))
                .isInstanceOf(WebCrawlerException.class).hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void namesCarryTheModel() {
        assertThat(new OllamaEmbeddingClient(properties).getName())
                .isEqualTo("ollama:qwen3-embedding:4b");
        assertThat(new OpenAiEmbeddingClient(properties).getName())
                .isEqualTo("openai:text-embedding-3-small");
    }

    @Test
    void queryToVectorFallsBackToTheDocumentEncoding() {
        server.on("POST", "/api/embed", 200, "{\"embeddings\":[[5.0]]}");
        assertThat(new OllamaEmbeddingClient(properties).queryToVector("x")).containsExactly(5.0f);
    }

}
