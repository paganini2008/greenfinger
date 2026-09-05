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
 * Embeddings from a local Ollama. Text only: Ollama's embedding endpoint takes no images.
 * 
 * @Description: OllamaEmbeddingClient
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class OllamaEmbeddingClient implements EmbeddingClient {

    private final EmbeddingProperties properties;
    private final EmbeddingProperties.Ollama config;
    private final RestJsonClient client;

    private volatile int dimensions = -1;

    public OllamaEmbeddingClient(EmbeddingProperties properties) {
        this.properties = properties;
        this.config = properties.getOllama();
        this.client = new RestJsonClient(properties.getConnectTimeout(),
                properties.getReadTimeout());
    }

    @Override
    public String getName() {
        return "ollama:" + config.getModel();
    }

    /**
     * Measured rather than configured: a collection is created with a fixed width, and a model tag
     * that quietly produces a different one would otherwise be discovered halfway through a crawl.
     */
    @Override
    public void afterPropertiesSet() {
        dimensions = textToVector("greenfinger").length;
        log.info("Ollama model '{}' produces {} dimensions", config.getModel(), dimensions);
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
        String url = StringUtils.stripEnd(config.getUrl(), "/") + "/api/embed";
        JsonNode response = client.post(url, Map.of("model", config.getModel(), "input", texts));
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (JsonNode embedding : response.path("embeddings")) {
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = (float) embedding.get(i).asDouble();
            }
            vectors.add(vector);
        }
        if (vectors.size() != texts.size()) {
            throw new WebCrawlerException("Ollama returned " + vectors.size() + " vectors for "
                    + texts.size() + " inputs");
        }
        return vectors;
    }

}
