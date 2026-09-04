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

package com.github.greenfinger.core.engine;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import javax.imageio.ImageIO;
import org.apache.commons.lang3.StringUtils;
import com.github.greenfinger.core.WebCrawlerConstants;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.engine.CrawledPage.StoredImage;
import com.github.greenfinger.core.utils.HashUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Downloads the images a page references and hands them to the blob store. New in 2.0.
 *
 * <p>
 * Most images on a page are not content: icons, spacers, tracking pixels, avatars, sprite sheets.
 * Each candidate is therefore judged on its declared size, its media type, its byte count and its
 * real decoded dimensions before being kept. Every threshold is configurable, and setting them to
 * zero keeps everything.
 *
 * <p>
 * Images are addressed by the SHA-256 of their bytes, so the same picture reached from a hundred
 * pages is downloaded a hundred times but stored once.
 * 
 * @Description: ImageFetcher
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class ImageFetcher {

    private final WebCrawlerProperties.Image config;
    private final HttpClient httpClient;

    public ImageFetcher(WebCrawlerProperties.Image config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getConnectTimeout()))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    /**
     * Fetches every image the page references that passes the filters, recording the results on the
     * page. A failure on one image never fails the page.
     */
    public void fetchAll(CrawledPage page) {
        long held = 0L;
        for (ImageRef ref : page.getImages()) {
            if (!isPlausible(ref)) {
                continue;
            }
            if (config.getMaxPageBytes() > 0 && held >= config.getMaxPageBytes()) {
                // the bytes are carried until the database has accepted the page, so the amount
                // one page can hold has to be bounded
                log.debug("Image budget reached on '{}'; skipping the rest", page.getUrl());
                break;
            }
            try {
                Optional<StoredImage> stored = fetch(ref, page.getUrl());
                if (stored.isPresent()) {
                    page.getStoredImages().add(stored.get());
                    held += stored.get().getBytes();
                }
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug("Skipped image '{}': {}", ref.getUrl(), e.getMessage());
                }
            }
        }
    }

    /**
     * One image by its url, for a restore rather than a crawl.
     *
     * <p>
     * The plausibility check is skipped on purpose: it reads the width and height the markup
     * declared, and a restore has no markup -- it has a row saying this picture was kept once
     * already. The size and type limits still apply, since those are read from what comes back.
     */
    public Optional<StoredImage> fetchOne(String url, String pageUrl) throws Exception {
        ImageRef ref = new ImageRef();
        ref.setUrl(url);
        return fetch(ref, pageUrl);
    }

    /**
     * Cheap rejection on what the markup already told us, so an icon costs no request at all.
     */
    private boolean isPlausible(ImageRef ref) {
        if (StringUtils.isBlank(ref.getUrl()) || ref.getUrl().startsWith("data:")) {
            return false;
        }
        if (config.getMinWidth() > 0 && ref.getDeclaredWidth() != null
                && ref.getDeclaredWidth() < config.getMinWidth()) {
            return false;
        }
        if (config.getMinHeight() > 0 && ref.getDeclaredHeight() != null
                && ref.getDeclaredHeight() < config.getMinHeight()) {
            return false;
        }
        return true;
    }

    private Optional<StoredImage> fetch(ImageRef ref, String pageUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ref.getUrl()))
                .timeout(Duration.ofMillis(config.getReadTimeout()))
                .header("User-Agent", randomUserAgent()).header("Referer", pageUrl).GET().build();
        HttpResponse<byte[]> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() / 100 != 2) {
            return Optional.empty();
        }

        String contentType = response.headers().firstValue("content-type").map(String::trim)
                .map(v -> v.split(";")[0].toLowerCase(Locale.ROOT)).orElse("");
        if (!config.getMimeTypes().isEmpty() && !config.getMimeTypes().contains(contentType)) {
            return Optional.empty();
        }

        byte[] bytes = response.body();
        if (bytes.length == 0
                || (config.getMaxBytes() > 0 && bytes.length > config.getMaxBytes())) {
            return Optional.empty();
        }

        // the markup lies about size often enough that the decoded dimensions are what count
        Integer width = null;
        Integer height = null;
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(in);
            if (image != null) {
                width = image.getWidth();
                height = image.getHeight();
            }
        } catch (Exception ignored) {
            // an undecodable format is still storable; it just cannot be measured
        }
        if (width != null && height != null) {
            if ((config.getMinWidth() > 0 && width < config.getMinWidth())
                    || (config.getMinHeight() > 0 && height < config.getMinHeight())) {
                return Optional.empty();
            }
        }

        StoredImage stored = new StoredImage();
        stored.setSourceUrl(ref.getUrl());
        stored.setContentHash(HashUtils.sha256(bytes));
        stored.setContentType(contentType);
        stored.setAlt(ref.getAlt());
        stored.setTitle(ref.getTitle());
        stored.setContext(ref.getContext());
        stored.setWidth(width);
        stored.setHeight(height);
        stored.setBytes(bytes.length);
        stored.setData(bytes);
        return Optional.of(stored);
    }


    private String randomUserAgent() {
        var agents = WebCrawlerConstants.USER_AGENTS;
        return agents.get(ThreadLocalRandom.current().nextInt(agents.size()));
    }

}
