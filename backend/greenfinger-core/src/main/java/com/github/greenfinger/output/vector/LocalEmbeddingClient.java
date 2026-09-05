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

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.inference.Predictor;
import com.github.greenfinger.core.WebCrawlerException;
import lombok.extern.slf4j.Slf4j;

/**
 * Embeddings computed here, with no account and no service.
 *
 * <p>
 * Two models, because text similarity and image similarity are not the same space:
 *
 * <ul>
 * <li><b>multilingual-e5-small</b> for text. Trained with a "query: " / "passage: " prefix and
 * noticeably worse without them, so both are applied.</li>
 * <li><b>SigLIP 2</b> for images, and for the text side of image search. Its two towers share one
 * space, which is what lets a picture be found by describing it; encoding that description with e5
 * instead would return noise rather than an error, so the two paths are kept apart.</li>
 * </ul>
 * 
 * @Description: LocalEmbeddingClient
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class LocalEmbeddingClient implements EmbeddingClient {

    private static final String E5_REPOSITORY = "Xenova/multilingual-e5-small";
    private static final String SIGLIP_REPOSITORY = "onnx-community/siglip2-base-patch16-224-ONNX";

    /**
     * Every weight file the provider opens, named once. {@code models pull} fetches this list, and
     * the loaders below resolve the same constants, so the two cannot drift apart.
     */
    private static final ModelFile E5_ONNX =
            new ModelFile(E5_REPOSITORY, "onnx/model_quantized.onnx", ModelFile.TEXT);
    private static final ModelFile E5_TOKENIZER =
            new ModelFile(E5_REPOSITORY, "tokenizer.json", ModelFile.TEXT);
    private static final ModelFile SIGLIP_VISION =
            new ModelFile(SIGLIP_REPOSITORY, "onnx/vision_model_quantized.onnx", ModelFile.IMAGE);
    private static final ModelFile SIGLIP_TEXT =
            new ModelFile(SIGLIP_REPOSITORY, "onnx/text_model_quantized.onnx", ModelFile.IMAGE);
    private static final ModelFile SIGLIP_TOKENIZER =
            new ModelFile(SIGLIP_REPOSITORY, "tokenizer.json", ModelFile.IMAGE);

    private static final List<ModelFile> REQUIRED_FILES =
            List.of(E5_ONNX, E5_TOKENIZER, SIGLIP_VISION, SIGLIP_TEXT, SIGLIP_TOKENIZER);

    /**
     * What the local provider needs on disk, for a caller that wants to fetch or inspect the
     * weights without loading them -- loading is seconds and hundreds of megabytes of memory, and
     * a pull should be neither.
     */
    public static List<ModelFile> requiredFiles() {
        return REQUIRED_FILES;
    }

    /** SigLIP's input resolution, and the mean and deviation it was trained with. */
    private static final int IMAGE_SIZE = 224;
    private static final float IMAGE_MEAN = 0.5f;
    private static final float IMAGE_STD = 0.5f;

    /** SigLIP's text tower is fixed-length; shorter inputs are padded. */
    private static final int SIGLIP_TEXT_TOKENS = 64;

    private final EmbeddingProperties properties;
    private final EmbeddingProperties.Local config;
    private final ModelStore modelStore;

    private NDManager manager;

    private HuggingFaceTokenizer textTokenizer;
    private ZooModel<NDList, NDList> textModel;
    private Predictor<NDList, NDList> textPredictor;
    private int textDimensions = -1;

    private HuggingFaceTokenizer siglipTokenizer;
    private ZooModel<NDList, NDList> visionModel;
    private Predictor<NDList, NDList> visionPredictor;
    private ZooModel<NDList, NDList> siglipTextModel;
    private Predictor<NDList, NDList> siglipTextPredictor;
    private int imageDimensions = -1;

    public LocalEmbeddingClient(EmbeddingProperties properties) {
        this.properties = properties;
        this.config = properties.getLocal();
        this.modelStore = new ModelStore(properties.getModelDir(), properties.isOffline());
    }

    @Override
    public String getName() {
        return "local";
    }

    /**
     * Loads the text model. The image models are left alone until something asks for an image, so a
     * text-only crawl never pays for the larger download.
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        manager = NDManager.newBaseManager();
        loadTextModel();
        log.info("Local text model '{}' ready, {} dimensions", config.getTextModel(),
                textDimensions);
    }

    private synchronized void loadTextModel() throws Exception {
        if (textPredictor != null) {
            return;
        }
        Path onnx = modelStore.resolve(E5_ONNX);
        Path tokenizer = modelStore.resolve(E5_TOKENIZER);

        textTokenizer = HuggingFaceTokenizer.newInstance(tokenizer);
        textModel = Criteria.builder().setTypes(NDList.class, NDList.class)
                .optModelPath(onnx).optEngine("OnnxRuntime").build().loadModel();
        textPredictor = textModel.newPredictor();
        textDimensions = textToVector("greenfinger").length;
    }

    private synchronized void loadImageModels() throws Exception {
        if (visionPredictor != null) {
            return;
        }
        Path vision = modelStore.resolve(SIGLIP_VISION);
        Path text = modelStore.resolve(SIGLIP_TEXT);
        Path tokenizer = modelStore.resolve(SIGLIP_TOKENIZER);

        siglipTokenizer = HuggingFaceTokenizer.newInstance(tokenizer);
        visionModel = Criteria.builder().setTypes(NDList.class, NDList.class).optModelPath(vision)
                .optEngine("OnnxRuntime").build().loadModel();
        visionPredictor = visionModel.newPredictor();
        siglipTextModel = Criteria.builder().setTypes(NDList.class, NDList.class)
                .optModelPath(text).optEngine("OnnxRuntime").build().loadModel();
        siglipTextPredictor = siglipTextModel.newPredictor();

        imageDimensions = imageToVector(blankPng(), "image/png").length;
        log.info("Local image model '{}' ready, {} dimensions", config.getImageModel(),
                imageDimensions);
    }

    @Override
    public int textDimensions() {
        ensureText();
        return textDimensions;
    }

    @Override
    public float[] textToVector(String text) {
        return encodeText(config.getDocumentPrefix() + safe(text));
    }

    @Override
    public float[] queryToVector(String text) {
        // e5 was trained with different prefixes for the two sides and loses accuracy without them
        return encodeText(config.getQueryPrefix() + safe(text));
    }

    private float[] encodeText(String text) {
        ensureText();
        try (NDManager scope = manager.newSubManager()) {
            Encoding encoding = textTokenizer.encode(text);
            long[] ids = truncate(encoding.getIds());
            long[] mask = truncate(encoding.getAttentionMask());

            NDArray inputIds = scope.create(ids, new Shape(1, ids.length));
            inputIds.setName("input_ids");
            NDArray attention = scope.create(mask, new Shape(1, mask.length));
            attention.setName("attention_mask");
            NDArray tokenTypes = scope.zeros(new Shape(1, ids.length), DataType.INT64);
            tokenTypes.setName("token_type_ids");

            NDList output = textPredictor.predict(new NDList(inputIds, attention, tokenTypes));
            return normalise(pooledOf(output, mask));
        } catch (Exception e) {
            throw new WebCrawlerException("Local text embedding failed", e);
        }
    }

    @Override
    public boolean supportsImages() {
        return true;
    }

    @Override
    public int imageDimensions() {
        ensureImages();
        return imageDimensions;
    }

    @Override
    public float[] imageToVector(byte[] image, String contentType) {
        ensureImages();
        try (NDManager scope = manager.newSubManager()) {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image));
            if (decoded == null) {
                throw new WebCrawlerException("Not a readable image");
            }
            NDArray pixels = scope.create(pixelsOf(decoded),
                    new Shape(1, 3, IMAGE_SIZE, IMAGE_SIZE));
            pixels.setName("pixel_values");

            NDList output = visionPredictor.predict(new NDList(pixels));
            return normalise(pooledOf(output, null));
        } catch (WebCrawlerException e) {
            throw e;
        } catch (Exception e) {
            throw new WebCrawlerException("Local image embedding failed", e);
        }
    }

    @Override
    public List<float[]> imagesToVectors(List<byte[]> images, List<String> contentTypes) {
        List<float[]> vectors = new ArrayList<>(images.size());
        for (int i = 0; i < images.size(); i++) {
            vectors.add(imageToVector(images.get(i),
                    contentTypes != null && i < contentTypes.size() ? contentTypes.get(i) : null));
        }
        return vectors;
    }

    /**
     * The description encoded by SigLIP's own text tower, which shares a space with the image
     * tower. This is the whole reason searching for pictures by words works.
     */
    @Override
    public float[] queryToImageVector(String text) {
        ensureImages();
        try (NDManager scope = manager.newSubManager()) {
            Encoding encoding = siglipTokenizer.encode(safe(text));
            long[] ids = pad(encoding.getIds(), SIGLIP_TEXT_TOKENS);

            NDArray inputIds = scope.create(ids, new Shape(1, ids.length));
            inputIds.setName("input_ids");

            NDList output = siglipTextPredictor.predict(new NDList(inputIds));
            return normalise(pooledOf(output, null));
        } catch (Exception e) {
            throw new WebCrawlerException("Local query embedding failed", e);
        }
    }

    private void ensureText() {
        try {
            if (manager == null) {
                manager = NDManager.newBaseManager();
            }
            loadTextModel();
        } catch (Exception e) {
            throw new WebCrawlerException("Could not load the local text model", e);
        }
    }

    private void ensureImages() {
        try {
            if (manager == null) {
                manager = NDManager.newBaseManager();
            }
            loadImageModels();
        } catch (Exception e) {
            throw new WebCrawlerException("Could not load the local image model", e);
        }
    }

    /**
     * Picks the embedding out of whatever the model returned.
     *
     * <p>
     * Selection is by shape and name rather than by position: an ONNX export may hand back the
     * pooled vector, the per-token states, or both in either order, and taking output zero on faith
     * is how a run ends up embedding the input pixels. A two dimensional output is already the
     * embedding; a three dimensional one is per-token and gets averaged.
     */
    private float[] pooledOf(NDList output, long[] mask) {
        NDArray pooled = null;
        NDArray tokens = null;
        for (NDArray candidate : output) {
            long[] shape = candidate.getShape().getShape();
            if (shape.length == 2 && "pooler_output".equals(candidate.getName())) {
                pooled = candidate;
                break;
            }
            if (shape.length == 2 && pooled == null) {
                pooled = candidate;
            } else if (shape.length == 3 && tokens == null) {
                tokens = candidate;
            }
        }
        if (pooled != null) {
            return pooled.toFloatArray();
        }
        if (tokens != null) {
            return meanPool(tokens, mask);
        }
        throw new WebCrawlerException("The model returned nothing that looks like an embedding: "
                + describe(output));
    }

    private String describe(NDList output) {
        StringBuilder str = new StringBuilder();
        for (NDArray array : output) {
            str.append(array.getName()).append(array.getShape()).append(' ');
        }
        return str.toString().trim();
    }

    /**
     * Mean of the token vectors, counting only real tokens. A transformer emits one vector per
     * token; a sentence embedding is their average, and padding must not drag it towards zero.
     */
    private float[] meanPool(NDArray tokens, long[] mask) {
        float[] flat = tokens.toFloatArray();
        long[] shape = tokens.getShape().getShape();
        int length = (int) shape[shape.length - 2];
        int width = (int) shape[shape.length - 1];

        float[] pooled = new float[width];
        int counted = 0;
        for (int token = 0; token < length; token++) {
            if (mask != null && (token >= mask.length || mask[token] == 0)) {
                continue;
            }
            counted++;
            for (int i = 0; i < width; i++) {
                pooled[i] += flat[token * width + i];
            }
        }
        if (counted > 0) {
            for (int i = 0; i < width; i++) {
                pooled[i] /= counted;
            }
        }
        return pooled;
    }

    /** Unit length, so cosine similarity is a dot product and the collection's metric agrees. */
    static float[] normalise(float[] vector) {
        double sum = 0d;
        for (float value : vector) {
            sum += value * value;
        }
        double norm = Math.sqrt(sum);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= (float) norm;
            }
        }
        return vector;
    }

    /**
     * Resize to the model's square input and scale into the range it was trained on.
     */
    static float[] pixelsOf(BufferedImage source) {
        BufferedImage scaled =
                new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_RGB);
        var graphics = scaled.createGraphics();
        graphics.drawImage(source.getScaledInstance(IMAGE_SIZE, IMAGE_SIZE,
                java.awt.Image.SCALE_SMOOTH), 0, 0, null);
        graphics.dispose();

        // channel-first, as the model expects: all red, then all green, then all blue
        float[] pixels = new float[3 * IMAGE_SIZE * IMAGE_SIZE];
        int plane = IMAGE_SIZE * IMAGE_SIZE;
        for (int y = 0; y < IMAGE_SIZE; y++) {
            for (int x = 0; x < IMAGE_SIZE; x++) {
                int rgb = scaled.getRGB(x, y);
                int offset = y * IMAGE_SIZE + x;
                pixels[offset] = scale((rgb >> 16) & 0xFF);
                pixels[plane + offset] = scale((rgb >> 8) & 0xFF);
                pixels[2 * plane + offset] = scale(rgb & 0xFF);
            }
        }
        return pixels;
    }

    static float scale(int channel) {
        return ((channel / 255f) - IMAGE_MEAN) / IMAGE_STD;
    }

    private long[] truncate(long[] values) {
        int limit = Math.min(values.length, config.getMaxTextTokens());
        long[] truncated = new long[limit];
        System.arraycopy(values, 0, truncated, 0, limit);
        return truncated;
    }

    static long[] pad(long[] values, int length) {
        long[] padded = new long[length];
        System.arraycopy(values, 0, padded, 0, Math.min(values.length, length));
        return padded;
    }

    private String safe(String text) {
        return text != null ? text : "";
    }

    /** A single grey pixel, used only to measure the width of an image vector. */
    private byte[] blankPng() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        var out = new java.io.ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Override
    public void destroy() {
        closeQuietly(textPredictor);
        closeQuietly(textModel);
        closeQuietly(visionPredictor);
        closeQuietly(visionModel);
        closeQuietly(siglipTextPredictor);
        closeQuietly(siglipTextModel);
        closeQuietly(textTokenizer);
        closeQuietly(siglipTokenizer);
        closeQuietly(manager);
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // shutting down; a model that will not close cleanly is not worth failing over
        }
    }

}
