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

import java.util.List;
import com.github.greenfinger.core.ManagedBeanLifeCycle;

/**
 * Turns content into vectors. The extension point for anyone who wants their own model.
 *
 * <p>
 * Only the two text methods have to be implemented. Everything to do with images is optional and
 * throws by default, because plenty of perfectly good embedding services do text and nothing else
 * -- Ollama's embedding endpoint and OpenAI's both refuse images -- and requiring an image
 * implementation would make those impossible to plug in. A client that cannot embed images is
 * detected once at startup and its image work is skipped; the crawl is unaffected and the images
 * are still fetched, stored and indexed.
 * 
 * @Description: EmbeddingClient
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public interface EmbeddingClient extends ManagedBeanLifeCycle {

    String getName();

    /** Length of the text vectors. Probed at startup rather than configured. */
    int textDimensions();

    float[] textToVector(String text);

    /**
     * A batch. Batching matters: one call per chunk would dominate a crawl's runtime.
     */
    default List<float[]> textToVectors(List<String> texts) {
        return texts.stream().map(this::textToVector).toList();
    }

    /**
     * The query side. Some models -- e5 among them -- were trained with a different prefix for
     * queries than for documents and lose accuracy when the two are encoded identically.
     */
    default float[] queryToVector(String text) {
        return textToVector(text);
    }

    /** Whether this client can embed images at all. */
    default boolean supportsImages() {
        return false;
    }

    default int imageDimensions() {
        throw new UnsupportedOperationException(getName() + " does not embed images");
    }

    default float[] imageToVector(byte[] image, String contentType) {
        throw new UnsupportedOperationException(getName() + " does not embed images");
    }

    default List<float[]> imagesToVectors(List<byte[]> images, List<String> contentTypes) {
        throw new UnsupportedOperationException(getName() + " does not embed images");
    }

    /**
     * Encodes a search phrase into the <em>image</em> space, for finding pictures by words. This is
     * not the same as {@link #queryToVector}: the text vectors and the image vectors live in
     * different spaces, and comparing one against the other yields noise rather than an error.
     */
    default float[] queryToImageVector(String text) {
        throw new UnsupportedOperationException(getName() + " does not embed images");
    }

    @Override
    default void afterPropertiesSet() throws Exception {
        // most clients are stateless; the local one loads its models here
    }

}
