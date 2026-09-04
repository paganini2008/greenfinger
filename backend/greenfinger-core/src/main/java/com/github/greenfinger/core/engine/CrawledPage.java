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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Data;

/**
 * Everything one fetched page yields: what to store, what to follow, and what to download.
 *
 * <p>
 * The text is extracted once, here, and carried onward. 1.x stored only the html and re-parsed it
 * with Jsoup every time the text was needed.
 * 
 * @Description: CrawledPage
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@Data
public class CrawledPage {

    private String catalogId;
    private String catalogName;
    private String cat;
    private int version;

    private String url;
    private String referer;
    private int depth;

    private String title;
    private String html;
    private String text;

    /** Set once the content dedup filter has fingerprinted the text. */
    private String contentHash;

    /** Carried from the response so the next merge can ask "still this one?" */
    private String etag;

    private String lastModified;

    private Date fetchedAt = new Date();

    /** Links found on the page, already resolved to absolute form. */
    private List<String> links = new ArrayList<>();

    /** Characters of text that sit inside links; with the text length this is the link density. */
    private int linkTextLength;

    /** Images referenced by the page, before any of them have been fetched. */
    private List<ImageRef> images = new ArrayList<>();

    /** Images actually downloaded, still holding their bytes until the file layer writes them. */
    private List<StoredImage> storedImages = new ArrayList<>();

    /**
     * 
     * @Description: StoredImage
     * @Author: Fred Feng
     * @Date: 29/08/2026
     * @Version 2.0.0
     */
    @Data
    public static class StoredImage {

        private String sourceUrl;
        private String contentHash;
        private String contentType;

        /** How this page referenced it; the same picture carries different wording elsewhere. */
        private String alt;
        private String title;
        private String context;

        private Integer width;
        private Integer height;
        private long bytes;

        /**
         * The bytes, held until the file layer writes them.
         *
         * <p>
         * They are carried rather than written on arrival because the database is the gate: a page
         * that the unique constraint rejects must not have left files behind. The amount held is
         * bounded per page by {@code max-per-page} and {@code max-page-bytes}.
         */
        private byte[] data;

    }

}
