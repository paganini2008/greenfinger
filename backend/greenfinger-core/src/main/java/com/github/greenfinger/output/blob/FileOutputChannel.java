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

package com.github.greenfinger.output.blob;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawledPage;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.output.BlobStore;
import com.github.greenfinger.core.output.FileLayout;
import com.github.greenfinger.core.output.OutputChannel;
import com.github.greenfinger.core.output.OutputPayload;
import com.github.greenfinger.core.record.ResourceRecord;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * The mandatory output: html, extracted text and image bytes, written where the database rows
 * already say they are.
 *
 * <p>
 * Paths are never invented here. They come from the record, which got them from
 * {@link FileLayout}, which derived them from ids that were derived from the content -- so a page
 * rejected by the database's unique constraint has left nothing behind, and a page written twice
 * overwrites itself rather than accumulating.
 * 
 * @Description: FileOutputChannel
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class FileOutputChannel implements OutputChannel {

    private final BlobStore blobStore;
    private final FileLayout layout;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Filled in by the launcher, so the settings file records how the run actually went. */
    @Setter
    private Supplier<Map<String, Object>> runSummarySupplier;

    private CatalogDetails catalogDetails;

    public FileOutputChannel(BlobStore blobStore, FileLayout layout) {
        this.blobStore = blobStore;
        this.layout = layout;
    }

    @Override
    public String getName() {
        return "file:" + blobStore.getName();
    }

    @Override
    public OutputType getType() {
        return OutputType.FILE;
    }

    @Override
    public void open(CatalogDetails catalogDetails) throws Exception {
        this.catalogDetails = catalogDetails;
        log.info("Writing files to {}/{}", blobStore.getName(), layout.versionPrefix());
    }

    @Override
    public void write(OutputPayload payload) throws Exception {
        if (payload.isReplay()) {
            // the files are already there; a replay is only rebuilding the index or the vectors
            return;
        }
        ResourceRecord record = payload.getRecord();
        CrawledPage page = payload.getPage();

        blobStore.write(record.resource().getHtmlFilePath(),
                bytesOf(page.getHtml()), "text/html; charset=utf-8");
        blobStore.write(record.resource().getHtmlContentFilePath(),
                bytesOf(page.getText()), "text/plain; charset=utf-8");

        for (ResourceRecord.ImageRecord image : record.images()) {
            byte[] data = findBytes(page, image.image().getContentHash());
            if (data == null) {
                // already written by an earlier page that referenced the same bytes
                continue;
            }
            if (!blobStore.exists(image.image().getImageFilePath())) {
                blobStore.write(image.image().getImageFilePath(), data,
                        image.image().getContentType());
            }
        }
    }

    private byte[] findBytes(CrawledPage page, String contentHash) {
        return page.getStoredImages().stream()
                .filter(s -> contentHash.equals(s.getContentHash())).map(
                        CrawledPage.StoredImage::getData)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    private byte[] bytesOf(String text) {
        return text != null ? text.getBytes(StandardCharsets.UTF_8) : new byte[0];
    }

    @Override
    public void flush() {
        // every write goes straight through; nothing is buffered here
    }

    @Override
    public void close() throws Exception {
        writeSettings();
    }

    /**
     * Records the configuration and the counters this version ran with, beside its data, so
     * deleting the version takes its settings with it.
     */
    private void writeSettings() throws Exception {
        if (catalogDetails == null) {
            return;
        }
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("catalogId", catalogDetails.getId());
        settings.put("name", catalogDetails.getName());
        settings.put("url", catalogDetails.getUrl());
        settings.put("startUrl", catalogDetails.getStartUrl());
        settings.put("cat", catalogDetails.getCategory());
        settings.put("version", catalogDetails.getVersion());
        settings.put("pathPatterns", catalogDetails.getPathPatterns());
        settings.put("excludedPathPatterns", catalogDetails.getExcludedPathPatterns());
        settings.put("maxFetchSize", catalogDetails.getMaxFetchSize());
        settings.put("maxFetchDepth", catalogDetails.getMaxFetchDepth());
        settings.put("extractor", catalogDetails.getExtractor().getRepr());
        settings.put("outputTypes", catalogDetails.getOutputTypes());
        settings.put("contentMode", catalogDetails.getContentMode());
        settings.put("imageEnabled", catalogDetails.isImageEnabled());
        if (runSummarySupplier != null) {
            settings.put("lastRun", runSummarySupplier.get());
        }
        blobStore.writeText(layout.settings(),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(settings));
    }

}
