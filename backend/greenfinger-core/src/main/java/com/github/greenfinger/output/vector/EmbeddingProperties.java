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

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * How text and images become vectors.
 *
 * <p>
 * The default provider runs models locally, so a new user opens no account and buys no quota. That
 * is not the same as downloading nothing -- model weights cannot go in the jar -- but nothing is
 * fetched until a crawl actually asks for vector output, which the default configuration does not.
 * 
 * @Description: EmbeddingProperties
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@ConfigurationProperties("greenfinger.embedding")
@Getter
@Setter
@ToString
public class EmbeddingProperties {

    /** local, ollama or openai. */
    private String provider = "local";

    /**
     * Where model weights are cached. Written once, on first use, and read from thereafter.
     */
    private String modelDir = System.getProperty("user.home") + "/.greenfinger/models";

    /** Refuse to download anything; fail instead if a model is not already cached. */
    private boolean offline = false;

    /**
     * Load the models when the application starts rather than when the first page needs them.
     *
     * <p>
     * Only has an effect when the local provider is selected and a vector output is configured, so
     * the quick start neither downloads nor loads anything. When both are true, paying the load
     * cost up front beats discovering a missing model minutes into a crawl.
     */
    private boolean preload = true;

    private int batchSize = 32;
    private int connectTimeout = 10000;
    private int readTimeout = 120000;

    private Local local = new Local();
    private Ollama ollama = new Ollama();
    private OpenAi openai = new OpenAi();

    /**
     * The zero-account default.
     *
     * <p>
     * Two towers, because a text embedding and an image embedding do not live in the same space:
     * text similarity comes from e5, image similarity from SigLIP 2. Searching for images by
     * words therefore has to encode the query with SigLIP's own text tower, which is what
     * {@code queryToImageVector} does -- passing an e5 vector to an image collection would produce
     * nothing but noise.
     * 
     * @Description: Local
     * @Author: Fred Feng
     * @Date: 30/08/2026
     * @Version 2.0.0
     */
    @Getter
    @Setter
    @ToString
    public static class Local {

        /** Multilingual, small, MIT licensed. */
        private String textModel = "intfloat/multilingual-e5-small";

        /** Image and text in one space, Apache-2.0 licensed. */
        private String imageModel = "google/siglip2-base-patch16-224";

        /**
         * e5 was trained with these prefixes and loses noticeable accuracy without them.
         */
        private String queryPrefix = "query: ";
        private String documentPrefix = "passage: ";

        private int maxTextTokens = 512;
    }

    /**
     * 
     * @Description: Ollama
     * @Author: Fred Feng
     * @Date: 30/08/2026
     * @Version 2.0.0
     */
    @Getter
    @Setter
    @ToString
    public static class Ollama {

        private String url = "http://localhost:11434";
        private String model = "qwen3-embedding:4b";
    }

    /**
     * 
     * @Description: OpenAi
     * @Author: Fred Feng
     * @Date: 30/08/2026
     * @Version 2.0.0
     */
    @Getter
    @Setter
    @ToString(exclude = "apiKey")
    public static class OpenAi {

        /** Any OpenAI-compatible gateway works here. */
        private String baseUrl = "https://api.openai.com/v1";

        /**
         * Falls back to the {@code OPENAI_API_KEY} environment variable when left unset, so a key
         * never has to be written into a configuration file that might be committed.
         */
        private String apiKey;

        private String model = "text-embedding-3-small";
    }

}
