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

package com.github.greenfinger.api.web;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.core.output.BlobStore;
import com.github.greenfinger.core.utils.BeanLifeCycleUtils;
import com.github.greenfinger.output.OutputFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;

/**
 * Hands back an image the crawl saved: the stored blob path
 * ({@code {catalog}/v0/images/ab/cd/{id}.jpg}) is not a url, so this is the one call that turns it
 * back into bytes.
 *
 * <p>
 * It needs a bearer token like every other GET, which an {@code <img src>} cannot send -- the front
 * end fetches the bytes and hands the tag an object url. Serving the archived copy is the point of
 * having archived it: it survives the site rearranging itself, cannot be hotlink-refused, is not
 * mixed content, and does not tell every crawled origin who is looking.
 *
 * <p>
 * The caller's path is checked against the shape the layout produces rather than scanned for
 * {@code ..}: a deny list must anticipate every encoding, an allow list describes one pattern.
 *
 * @Description: ImageApiController
 * @Author: Fred Feng
 * @Date: 31/08/2026
 * @Version 2.0.0
 */
@ApiEndpoint
@RequestMapping("${greenfinger.api.prefix:/v2}/image")
@RequiredArgsConstructor
public class ImageApiController {

    /**
     * {@code {catalog}/v{n}/images/{shards}/{uuid}.{ext}} and nothing else, with the extension
     * named rather than left as "some letters" -- so this endpoint can only ever reach the image
     * directory, and only ever a file the crawl itself named.
     */
    private static final Pattern IMAGE_PATH = Pattern.compile(
            "^[A-Za-z0-9._-]{1,128}/v\\d{1,6}/images/(?:[0-9a-f]{2}/){0,4}"
                    + "[0-9a-fA-F-]{36}\\.(?:jpg|jpeg|png|gif|webp|bmp|avif|svg|ico)$");

    private static final CacheControl IMMUTABLE =
            CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic().immutable();

    private final OutputFactory outputFactory;

    private BlobStore blobStore;

    /**
     * One store for the server's lifetime. A MinIO client per request would open a connection pool
     * per thumbnail, and a gallery is twenty thumbnails at once.
     */
    @PostConstruct
    public void init() throws Exception {
        this.blobStore = outputFactory.getBlobStore();
        BeanLifeCycleUtils.afterPropertiesSet(blobStore);
    }

    @PreDestroy
    public void destroy() {
        BeanLifeCycleUtils.destroyQuietly(blobStore);
    }

    @GetMapping
    public ResponseEntity<byte[]> image(@RequestParam("path") String path) throws Exception {
        if (!IMAGE_PATH.matcher(path).matches()) {
            throw new WebCrawlerException("Not an image path: " + path);
        }
        Optional<byte[]> bytes = blobStore.readBytes(path);
        if (bytes.isEmpty()) {
            // the version it belonged to has been deleted, or never reached the file layer
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(mediaTypeOf(path)).cacheControl(IMMUTABLE)
                // These bytes came off somebody else's website and are now served from our own
                // origin, which is exactly the shape of an XSS. nosniff stops a mislabelled file
                // being executed as script, and the sandbox neutralises an SVG that carries any --
                // neither affects how an <img> draws.
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(bytes.get());
    }

    /**
     * From the extension, not from a caller-supplied header: the id in the name is a hash of the
     * bytes, so the file at a given path can never become a different kind of thing.
     */
    private static MediaType mediaTypeOf(String path) {
        String extension = path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            case "svg" -> MediaType.parseMediaType("image/svg+xml");
            case "bmp" -> MediaType.parseMediaType("image/bmp");
            case "avif" -> MediaType.parseMediaType("image/avif");
            default -> MediaType.IMAGE_JPEG;
        };
    }

}
