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

package com.github.greenfinger.matrix;

import java.util.Locale;
import com.github.greenfinger.output.vector.EmbeddingClient;

/**
 * Vectors without a model: a bag of words projected onto a small fixed number of dimensions.
 *
 * <p>
 * Deliberately not a real embedder, and not trying to be. What the matrix is testing is the path
 * a vector takes through this application -- written to the Lucene vector store beside a catalog
 * and a version, searched, counted, and removed again by every shape of delete. None of that
 * depends on the numbers being meaningful, and a real model would make the test depend on a
 * download, several seconds per run, and a machine with the memory for it.
 *
 * <p>
 * It is still a real signal rather than random noise: the same text gives the same vector, and two
 * texts that share words come out nearer each other than two that share none, so a nearest
 * neighbour search returns something a test can actually assert about.
 * 
 * @Description: StubEmbeddingClient
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
public class StubEmbeddingClient implements EmbeddingClient {

    /** Small on purpose: the store writes and reads whatever width it is told. */
    private static final int DIMENSIONS = 32;

    @Override
    public String getName() {
        return "stub";
    }

    @Override
    public int textDimensions() {
        return DIMENSIONS;
    }

    @Override
    public float[] textToVector(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text == null) {
            return vector;
        }
        for (String word : text.toLowerCase(Locale.ROOT).split("\\W+")) {
            if (!word.isEmpty()) {
                vector[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1f;
            }
        }
        double length = 0d;
        for (float value : vector) {
            length += value * value;
        }
        length = Math.sqrt(length);
        if (length > 0d) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= (float) length;
            }
        } else {
            // a vector of zeroes has no direction, and a cosine against it is undefined
            vector[0] = 1f;
        }
        return vector;
    }

}
