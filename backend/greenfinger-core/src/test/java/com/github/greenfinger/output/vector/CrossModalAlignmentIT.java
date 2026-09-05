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
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.github.greenfinger.core.utils.BeanLifeCycleUtils;

/**
 * Whether searching for pictures by words works at all.
 *
 * <p>
 * Everything else about the image path can be right -- the model loads, vectors are written, a
 * search returns results with plausible scores -- while the two towers disagree about what the
 * space means, and then every result is noise wearing a similarity score. Nothing short of asking
 * the model catches that, which is why this test exists and why it loads real weights.
 *
 * <p>
 * The pictures are chosen so the answer cannot be a matter of taste: a red square and a blue one,
 * described in words. If a query for red does not prefer the red one, cross modal retrieval is
 * broken, and no amount of tuning further up will fix it.
 *
 * <p>
 * Off unless asked for -- it loads about 400 MB of SigLIP weights:
 *
 * <pre>
 * mvn test -Dgreenfinger.models=true
 * </pre>
 *
 * @Description: CrossModalAlignmentIT
 * @Author: Fred Feng
 * @Date: 01/09/2026
 * @Version 2.0.0
 */
@EnabledIfSystemProperty(named = "greenfinger.models", matches = "true")
class CrossModalAlignmentIT {

    private static LocalEmbeddingClient client;

    @BeforeAll
    static void loadTheModel() throws Exception {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setPreload(false);
        client = new LocalEmbeddingClient(properties);
        BeanLifeCycleUtils.afterPropertiesSet(client);
    }

    @AfterAll
    static void unload() {
        BeanLifeCycleUtils.destroyQuietly(client);
    }

    private static byte[] solid(Color colour) throws Exception {
        BufferedImage image = new BufferedImage(224, 224, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(colour);
        graphics.fillRect(0, 0, 224, 224);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static double similarity(float[] a, float[] b) {
        // both sides are unit length, so the dot product is the cosine
        double sum = 0d;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    @Test
    @DisplayName("a query for red prefers the red picture: the two towers share a space")
    void wordsAndPicturesMeet() throws Exception {
        float[] red = client.imageToVector(solid(Color.RED), "image/png");
        float[] blue = client.imageToVector(solid(Color.BLUE), "image/png");

        float[] askingForRed = client.queryToImageVector("a solid red square");
        float[] askingForBlue = client.queryToImageVector("a solid blue square");

        assertThat(similarity(askingForRed, red)).as("red query against the red picture")
                .isGreaterThan(similarity(askingForRed, blue));
        assertThat(similarity(askingForBlue, blue)).as("blue query against the blue picture")
                .isGreaterThan(similarity(askingForBlue, red));
    }

    @Test
    @DisplayName("the same picture twice is the same vector, so retrieval is repeatable")
    void encodingIsDeterministic() throws Exception {
        byte[] picture = solid(Color.GREEN);

        assertThat(similarity(client.imageToVector(picture, "image/png"),
                client.imageToVector(picture, "image/png"))).isCloseTo(1.0,
                        org.assertj.core.data.Offset.offset(1e-4));
    }

    @Test
    @DisplayName("two different pictures are not the same vector, which a collapsed tower would be")
    void differentPicturesDiffer() throws Exception {
        double similarity = similarity(client.imageToVector(solid(Color.RED), "image/png"),
                client.imageToVector(solid(Color.BLUE), "image/png"));

        assertThat(similarity).isLessThan(0.99d);
    }

}
