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

/**
 * Deterministic vectors without a model, so the channel can be tested without loading half a
 * gigabyte of weights. Image support is switchable, because whether a client has it is exactly the
 * thing the pipeline has to cope with.
 * 
 * @Description: StubEmbeddingClient
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public class StubEmbeddingClient implements EmbeddingClient {

    private final int textDimensions;
    private final Integer imageDimensions;

    public StubEmbeddingClient(int textDimensions) {
        this(textDimensions, null);
    }

    public StubEmbeddingClient(int textDimensions, Integer imageDimensions) {
        this.textDimensions = textDimensions;
        this.imageDimensions = imageDimensions;
    }

    @Override
    public String getName() {
        return "stub";
    }

    @Override
    public int textDimensions() {
        return textDimensions;
    }

    @Override
    public float[] textToVector(String text) {
        return fill(textDimensions, text != null ? text.hashCode() : 0);
    }

    @Override
    public boolean supportsImages() {
        return imageDimensions != null;
    }

    @Override
    public int imageDimensions() {
        if (imageDimensions == null) {
            return EmbeddingClient.super.imageDimensions();
        }
        return imageDimensions;
    }

    @Override
    public float[] imageToVector(byte[] image, String contentType) {
        if (imageDimensions == null) {
            return EmbeddingClient.super.imageToVector(image, contentType);
        }
        return fill(imageDimensions, image != null ? image.length : 0);
    }

    @Override
    public java.util.List<float[]> imagesToVectors(java.util.List<byte[]> images,
            java.util.List<String> contentTypes) {
        if (imageDimensions == null) {
            return EmbeddingClient.super.imagesToVectors(images, contentTypes);
        }
        return images.stream().map(bytes -> imageToVector(bytes, null)).toList();
    }

    @Override
    public float[] queryToImageVector(String text) {
        if (imageDimensions == null) {
            return EmbeddingClient.super.queryToImageVector(text);
        }
        return fill(imageDimensions, text != null ? text.hashCode() : 0);
    }

    private float[] fill(int size, int seed) {
        float[] vector = new float[size];
        for (int i = 0; i < size; i++) {
            vector[i] = ((seed + i) % 100) / 100f;
        }
        return vector;
    }

}
