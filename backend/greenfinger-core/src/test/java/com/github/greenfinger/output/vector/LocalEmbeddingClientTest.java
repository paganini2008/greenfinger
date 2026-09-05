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
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.WebCrawlerException;

/**
 * The arithmetic around the models, which is testable without loading half a gigabyte of weights.
 * The models themselves are exercised by an actual crawl.
 * 
 * @Description: LocalEmbeddingClientTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
class LocalEmbeddingClientTest {

    @Test
    @DisplayName("unit length, so cosine similarity is a dot product")
    void normalisesToUnitLength() {
        float[] vector = LocalEmbeddingClient.normalise(new float[] {3f, 4f});

        assertThat(vector[0]).isEqualTo(0.6f);
        assertThat(vector[1]).isEqualTo(0.8f);
        assertThat(Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1])).isCloseTo(1.0,
                org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("an all-zero vector is left alone rather than divided by nothing")
    void survivesAZeroVector() {
        assertThat(LocalEmbeddingClient.normalise(new float[] {0f, 0f})).containsExactly(0f, 0f);
    }

    @Test
    @DisplayName("pixels are scaled into the range SigLIP was trained on")
    void scalesPixelsToTheTrainedRange() {
        // mean 0.5, deviation 0.5: black becomes -1, white +1, mid grey 0
        assertThat(LocalEmbeddingClient.scale(0)).isEqualTo(-1f);
        assertThat(LocalEmbeddingClient.scale(255)).isEqualTo(1f);
        assertThat(LocalEmbeddingClient.scale(128)).isCloseTo(0f,
                org.assertj.core.data.Offset.offset(0.01f));
    }

    @Test
    @DisplayName("channel-first and resized, whatever the picture came in as")
    void producesChannelFirstPixels() {
        BufferedImage source = new BufferedImage(10, 40, BufferedImage.TYPE_INT_RGB);
        source.setRGB(0, 0, 0xFF0000);

        float[] pixels = LocalEmbeddingClient.pixelsOf(source);

        // three planes of 224 x 224, not interleaved triples
        assertThat(pixels).hasSize(3 * 224 * 224);
        assertThat(pixels[0]).isEqualTo(1f);                    // red channel, top left
        assertThat(pixels[224 * 224]).isEqualTo(-1f);           // green channel, same pixel
        assertThat(pixels[2 * 224 * 224]).isEqualTo(-1f);       // blue channel, same pixel
    }

    @Test
    @DisplayName("SigLIP's text tower is fixed length, so short inputs are padded")
    void padsToAFixedLength() {
        assertThat(LocalEmbeddingClient.pad(new long[] {1L, 2L}, 5)).containsExactly(1L, 2L, 0L,
                0L, 0L);
        // and a long one is cut rather than overflowing
        assertThat(LocalEmbeddingClient.pad(new long[] {1L, 2L, 3L}, 2)).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("this provider does images, which is the whole reason it exists")
    void declaresImageSupport() {
        assertThat(new LocalEmbeddingClient(new EmbeddingProperties()).supportsImages()).isTrue();
        assertThat(new LocalEmbeddingClient(new EmbeddingProperties()).getName())
                .isEqualTo("local");
    }

    @Test
    @DisplayName("offline with nothing cached fails with an explanation, not a stack trace")
    void offlineWithoutACacheSaysWhy(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setModelDir(root.toString());
        properties.setOffline(true);

        assertThatThrownBy(() -> new LocalEmbeddingClient(properties).textToVector("x"))
                .isInstanceOf(WebCrawlerException.class)
                .hasMessageContaining("local text model");
    }

}
