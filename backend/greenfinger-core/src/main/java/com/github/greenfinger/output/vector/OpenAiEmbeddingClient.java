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
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.output.RestJsonClient;
import lombok.extern.slf4j.Slf4j;

/**
 * Embeddings from OpenAI, or any gateway speaking its api. Text only: OpenAI publishes no image
 * embedding endpoint.
 * 
 * @Description: OpenAiEmbeddingClient
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private final EmbeddingProperties properties;
    private final EmbeddingProperties.OpenAi config;
    private final RestJsonClient client;

    private volatile int dimensions = -1;

    public OpenAiEmbeddingClient(EmbeddingProperties properties) {
        this.properties = properties;
        this.config = properties.getOpenai();
        this.client = new RestJsonClient(properties.getConnectTimeout(),
                properties.getReadTimeout(), "Bearer " + apiKey());
    }

    /**
     * The environment variable is the normal source, so a key never has to be written into a
     * configuration file that might be committed.
     */
    private String apiKey() {
        if (StringUtils.isNotBlank(config.getApiKey())) {
            return config.getApiKey();
        }
        String fromEnvironment = System.getenv("OPENAI_API_KEY");
        if (StringUtils.isBlank(fromEnvironment)) {
            throw new WebCrawlerException(
                    "No OpenAI key. Set greenfinger.embedding.openai.api-key, or OPENAI_API_KEY.");
        }
        return fromEnvironment;
    }

    @Override
    public String getName() {
        return "openai:" + config.getModel();
    }

    @Override
    public void afterPropertiesSet() {
        dimensions = textToVector("greenfinger").length;
        log.info("OpenAI model '{}' produces {} dimensions", config.getModel(), dimensions);
    }

    @Override
    public int textDimensions() {
        if (dimensions < 0) {
            afterPropertiesSet();
        }
        return dimensions;
    }

    @Override
    public float[] textToVector(String text) {
        return textToVectors(List.of(text)).get(0);
    }

    @Override
    public List<float[]> textToVectors(List<String> texts) {
        String url = StringUtils.stripEnd(config.getBaseUrl(), "/") + "/embeddings";
        JsonNode response = client.post(url, Map.of("model", config.getModel(), "input", texts));
        JsonNode data = response.path("data");
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (JsonNode item : data) {
            JsonNode embedding = item.path("embedding");
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = (float) embedding.get(i).asDouble();
            }
            vectors.add(vector);
        }
        if (vectors.size() != texts.size()) {
            throw new WebCrawlerException("OpenAI returned " + vectors.size() + " vectors for "
                    + texts.size() + " inputs");
        }
        return vectors;
    }

}
