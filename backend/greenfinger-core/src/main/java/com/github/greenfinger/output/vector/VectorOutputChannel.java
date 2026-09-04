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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.StringUtils;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.output.ContentReader;
import com.github.greenfinger.core.output.OutputChannel;
import com.github.greenfinger.core.output.OutputPayload;
import com.github.greenfinger.core.record.ResourceRecord;
import com.github.greenfinger.core.utils.UuidUtils;
import com.github.greenfinger.output.OutputProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Semantic and cross modal search.
 *
 * <p>
 * Two collections, because the text vectors and the image vectors are not comparable: they come
 * from different models, in different spaces, and usually of different widths. The collection name
 * carries its width, so pointing the crawler at a different model puts the new vectors somewhere of
 * their own instead of failing against a collection that cannot hold them.
 *
 * <p>
 * Images are stored per page-image reference rather than per image. The same picture on twenty
 * pages is embedded once and written twenty times, which costs storage but no compute, and buys
 * self-sufficiency: a hit in the image collection already knows which page the picture came from,
 * so search never has to consult the database.
 * 
 * @Description: VectorOutputChannel
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class VectorOutputChannel implements OutputChannel {

    private final OutputProperties.Vector config;
    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;
    private final ContentReader contentReader;
    private final TextChunker chunker;

    private final List<PendingChunk> textBuffer = new ArrayList<>();
    private final List<PendingImage> imageBuffer = new ArrayList<>();
    private final AtomicLong textWritten = new AtomicLong();
    private final AtomicLong imageWritten = new AtomicLong();

    @Getter
    private String textCollection;
    @Getter
    private String imageCollection;

    private CatalogDetails catalogDetails;
    private boolean imagesSupported;

    public VectorOutputChannel(OutputProperties.Vector config, EmbeddingClient embeddingClient,
            VectorStore vectorStore, ContentReader contentReader) {
        this.config = config;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.contentReader = contentReader;
        this.chunker = new TextChunker(config.getChunkSize(), config.getChunkOverlap(),
                config.getMaxChunksPerPage());
    }

    @Override
    public String getName() {
        return "vector";
    }

    @Override
    public OutputType getType() {
        return OutputType.VECTOR;
    }

    @Override
    public void open(CatalogDetails catalogDetails) throws Exception {
        this.catalogDetails = catalogDetails;
        int textDimensions = embeddingClient.textDimensions();
        this.textCollection = config.getTextCollection() + "_" + textDimensions;
        vectorStore.ensureCollection(textCollection, textDimensions);

        // an embedding client that only does text is a perfectly ordinary thing; the images are
        // still crawled, stored and indexed, they simply get no vectors
        this.imagesSupported = embeddingClient.supportsImages()
                && catalogDetails.getContentMode().includesImages();
        if (imagesSupported) {
            int imageDimensions = embeddingClient.imageDimensions();
            this.imageCollection = config.getImageCollection() + "_" + imageDimensions;
            vectorStore.ensureCollection(imageCollection, imageDimensions);
        } else if (catalogDetails.getContentMode().includesImages()) {
            log.info("'{}' does not embed images; image vectors are skipped",
                    embeddingClient.getName());
        }
        log.info("Embedding into {} via {}", vectorStore.getName(), embeddingClient.getName());
    }

    @Override
    public void write(OutputPayload payload) throws Exception {
        ResourceRecord record = payload.getRecord();
        String text = payload.getText();
        if (StringUtils.isNotBlank(text)) {
            List<String> chunks = chunker.split(text);
            synchronized (textBuffer) {
                for (int i = 0; i < chunks.size(); i++) {
                    textBuffer.add(new PendingChunk(record, i, chunks.get(i)));
                }
            }
        }
        if (imagesSupported) {
            for (ResourceRecord.ImageRecord image : record.images()) {
                byte[] bytes = imageBytes(payload, image);
                if (bytes != null) {
                    synchronized (imageBuffer) {
                        imageBuffer.add(new PendingImage(record, image, bytes));
                    }
                }
            }
        }
        drain(false);
    }

    /**
     * Read back from the file layer, like the text, so a replay works from the same bytes.
     */
    private byte[] imageBytes(OutputPayload payload, ResourceRecord.ImageRecord image) {
        try {
            return contentReader.readBytes(image.image().getImageFilePath()).orElse(null);
        } catch (Exception e) {
            log.warn("Could not read image '{}': {}", image.image().getImageFilePath(),
                    e.getMessage());
            return null;
        }
    }

    private void drain(boolean force) throws Exception {
        List<PendingChunk> chunks = null;
        synchronized (textBuffer) {
            if (force ? !textBuffer.isEmpty() : textBuffer.size() >= config.getQdrant()
                    .getBatchSize()) {
                chunks = new ArrayList<>(textBuffer);
                textBuffer.clear();
            }
        }
        if (chunks != null) {
            embedText(chunks);
        }

        List<PendingImage> images = null;
        synchronized (imageBuffer) {
            if (force ? !imageBuffer.isEmpty() : imageBuffer.size() >= config.getQdrant()
                    .getBatchSize()) {
                images = new ArrayList<>(imageBuffer);
                imageBuffer.clear();
            }
        }
        if (images != null) {
            embedImages(images);
        }
    }

    private void embedText(List<PendingChunk> batch) throws Exception {
        List<float[]> vectors =
                embeddingClient.textToVectors(batch.stream().map(PendingChunk::text).toList());
        List<VectorPoint> points = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            PendingChunk chunk = batch.get(i);
            points.add(new VectorPoint(textPointId(chunk), vectors.get(i), textPayload(chunk)));
        }
        vectorStore.upsert(textCollection, points);
        textWritten.addAndGet(points.size());
    }

    private void embedImages(List<PendingImage> batch) throws Exception {
        List<float[]> vectors = embeddingClient.imagesToVectors(
                batch.stream().map(PendingImage::bytes).toList(),
                batch.stream().map(p -> p.image().image().getContentType()).toList());
        List<VectorPoint> points = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            PendingImage pending = batch.get(i);
            points.add(new VectorPoint(pending.image().reference().getId(), vectors.get(i),
                    imagePayload(pending)));
        }
        vectorStore.upsert(imageCollection, points);
        imageWritten.addAndGet(points.size());
    }

    private String textPointId(PendingChunk chunk) {
        UUID namespace = UUID.fromString(chunk.record().resource().getCatalogId());
        return UuidUtils.nameBased(namespace, chunk.record().resource().getVersion() + "|"
                + chunk.record().resource().getId() + "|" + chunk.index()).toString();
    }

    private Map<String, Object> textPayload(PendingChunk chunk) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("catalogVersion", catalogVersion(chunk.record()));
        payload.put("catalogId", chunk.record().resource().getCatalogId());
        payload.put("catalog", catalogDetails.getName());
        payload.put("version", chunk.record().resource().getVersion());
        payload.put("resourceId", chunk.record().resource().getId());
        payload.put("url", chunk.record().resource().getUrl());
        payload.put("title", chunk.record().resource().getTitle());
        payload.put("cat", chunk.record().resource().getCat());
        payload.put("chunkIndex", chunk.index());
        payload.put("chunkText", chunk.text());
        payload.put("htmlFilePath", chunk.record().resource().getHtmlFilePath());
        // the same two ranking signals the index carries, so a caller can push detail pages above
        // listings on this side too
        payload.put("linkCount", chunk.record().resource().getLinkCount());
        payload.put("textLength", chunk.record().resource().getTextLength());
        return payload;
    }

    /**
     * Carries the referring page, so a picture found by similarity can be shown in context without
     * a database lookup -- which matters because search is not allowed to touch the database.
     */
    private Map<String, Object> imagePayload(PendingImage pending) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("catalogVersion", catalogVersion(pending.record()));
        payload.put("catalogId", pending.record().resource().getCatalogId());
        payload.put("catalog", catalogDetails.getName());
        payload.put("version", pending.record().resource().getVersion());
        payload.put("imageId", pending.image().image().getId());
        payload.put("imageFilePath", pending.image().image().getImageFilePath());
        // where the picture was on the site. The archived copy is what gets displayed -- this is
        // for linking back, and for telling two identical-looking images apart
        payload.put("imageUrl", pending.image().reference().getSourceUrl());
        payload.put("contentType", pending.image().image().getContentType());
        payload.put("width", pending.image().image().getWidth());
        payload.put("height", pending.image().image().getHeight());
        payload.put("alt", pending.image().reference().getAltText());
        payload.put("context", pending.image().reference().getContextText());
        payload.put("resourceId", pending.record().resource().getId());
        payload.put("url", pending.record().resource().getUrl());
        payload.put("title", pending.record().resource().getTitle());
        return payload;
    }

    private String catalogVersion(ResourceRecord record) {
        return record.resource().getCatalogId() + ":" + record.resource().getVersion();
    }

    @Override
    public void flush() throws Exception {
        drain(true);
    }

    @Override
    public void close() throws Exception {
        flush();
        log.info("Embedded {} text chunk(s) and {} image(s)", textWritten.get(),
                imageWritten.get());
    }

    public long getTextWrittenCount() {
        return textWritten.get();
    }

    public long getImageWrittenCount() {
        return imageWritten.get();
    }

    private record PendingChunk(ResourceRecord record, int index, String text) {
    }

    private record PendingImage(ResourceRecord record, ResourceRecord.ImageRecord image,
            byte[] bytes) {
    }

}
