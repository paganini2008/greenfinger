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

package com.github.greenfinger.core.output;

import java.util.Locale;
import com.github.greenfinger.core.catalog.CatalogDetails;

/**
 * Where a crawl's assets go, as paths relative to the assets root. The same string is the path on
 * disk and the object key in MinIO, so the two layouts are identical and a crawl can move between
 * them without anything being rewritten.
 *
 * <pre>
 * {catalogId}/v{version}/settings.json
 *                        reports/{stamp}-{action}-{node}.json
 *                        pages/{ab}/{cd}/{resourceId}.html
 *                        pages/{ab}/{cd}/{resourceId}.txt
 *                        images/{ab}/{cd}/{imageId}.jpg
 * </pre>
 *
 * <p>
 * The catalog is addressed by its id and not by its name, which is what the index directories and
 * the vector payloads use as well: a rename then moves nothing on disk, and no two catalogs can
 * collide through a name that only differs in a character the file system will not take.
 *
 * <p>
 * The version is part of the path, so deleting one version is deleting one directory, or one
 * prefix in MinIO. Sites are not separated into subdirectories: file names are ids and carry no
 * readability anyway, and which site a page came from is recorded in the database and in the
 * search metadata.
 *
 * <p>
 * Sharding takes characters from the <em>front</em> of the id, which is safe only because resource
 * and image ids are UUID v5 -- a SHA-1 digest, so uniformly distributed. Were they v7 the leading
 * characters would be a timestamp, near identical across one crawl, and every file of a run would
 * land in a single directory; that variant would have to shard on the tail instead.
 * 
 * @Description: FileLayout
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public final class FileLayout {

    public static final String SETTINGS_NAME = "settings.json";

    /** Public because the storage report tells the two apart by the path they are under. */
    public static final String PAGES = "pages";
    public static final String IMAGES = "images";

    private final String catalogId;
    private final int version;
    private final int shardDepth;

    public FileLayout(String catalogId, int version, int shardDepth) {
        this.catalogId = safeName(catalogId);
        this.version = version;
        this.shardDepth = Math.max(0, Math.min(shardDepth, 4));
    }

    public static FileLayout of(CatalogDetails catalogDetails, int shardDepth) {
        return new FileLayout(catalogDetails.getId(), catalogDetails.getVersion(), shardDepth);
    }

    /** {@code {catalogId}/v{version}} -- the unit a delete removes. */
    public String versionPrefix() {
        return catalogId + "/v" + version;
    }

    public String catalogPrefix() {
        return catalogId;
    }

    public String settings() {
        return versionPrefix() + "/" + SETTINGS_NAME;
    }

    public String html(String resourceId) {
        return page(resourceId, ".html");
    }

    public String text(String resourceId) {
        return page(resourceId, ".txt");
    }

    public String image(String imageId, String contentType, String sourceUrl) {
        return versionPrefix() + "/" + IMAGES + "/" + shard(imageId) + imageId
                + extensionOf(contentType, sourceUrl);
    }

    private String page(String resourceId, String extension) {
        return versionPrefix() + "/" + PAGES + "/" + shard(resourceId) + resourceId + extension;
    }

    /**
     * Two hex characters per level, so a flat directory of millions of files never happens.
     */
    private String shard(String id) {
        if (shardDepth == 0) {
            return "";
        }
        String flat = id.replace("-", "");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < shardDepth && (i * 2 + 2) <= flat.length(); i++) {
            sb.append(flat, i * 2, i * 2 + 2).append('/');
        }
        return sb.toString();
    }

    public static String extensionOf(String contentType, String url) {
        String type = contentType != null ? contentType.toLowerCase(Locale.ROOT) : "";
        int semicolon = type.indexOf(';');
        if (semicolon > 0) {
            type = type.substring(0, semicolon).trim();
        }
        return switch (type) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/avif" -> ".avif";
            case "image/bmp" -> ".bmp";
            case "image/svg+xml" -> ".svg";
            default -> fromUrl(url);
        };
    }

    private static String fromUrl(String url) {
        if (url == null) {
            return ".bin";
        }
        // the query has to go first: measuring the extension with "?v=2" still attached makes
        // "/pic.jpg?v=2" look like a six-plus character suffix and fall through to .bin
        String path = url.split("[?#]")[0];
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        if (dot > slash && dot > 0 && path.length() - dot <= 6) {
            return path.substring(dot);
        }
        return ".bin";
    }

    /**
     * Ids reach the file system, so anything unusual becomes an underscore. A uuid never needs
     * it; the guard is here because the id is a column somebody could have filled in by hand.
     */
    public static String safeName(String name) {
        return name == null ? "catalog" : name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

}
