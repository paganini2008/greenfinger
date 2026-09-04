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

package com.github.greenfinger.output.index;

import lombok.experimental.UtilityClass;

/**
 * The field names, which are deliberately the Elasticsearch mapping's field names.
 *
 * <p>
 * A crawl written to the embedded index and the same crawl written to Elasticsearch produce
 * documents with the same fields, filtered by the same {@code catalogVersion}, in an index named by
 * the same rule. That is what makes moving between them a {@code replay} rather than a migration,
 * and it is why these are written down in one place rather than typed out at each use.
 * 
 * @Description: LuceneFields
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
@UtilityClass
public class LuceneFields {

    public static final String ID = "id";
    public static final String TITLE = "title";
    public static final String CONTENT = "content";
    public static final String URL = "url";
    public static final String HOST = "host";
    public static final String CAT = "cat";
    public static final String CATALOG = "catalog";
    public static final String CATALOG_ID = "catalogId";
    public static final String CATALOG_VERSION = "catalogVersion";
    public static final String REFERER = "referer";
    public static final String CONTENT_HASH = "contentHash";
    public static final String HTML_FILE_PATH = "htmlFilePath";
    public static final String HTML_CONTENT_FILE_PATH = "htmlContentFilePath";
    public static final String VERSION = "version";
    public static final String DEPTH = "depth";
    public static final String LINK_COUNT = "linkCount";
    public static final String TEXT_LENGTH = "textLength";
    public static final String LINK_TEXT_LENGTH = "linkTextLength";
    public static final String CREATE_TIME = "createTime";

    /**
     * The images, as the json Elasticsearch would have held in a nested field.
     *
     * <p>
     * Lucene has no nested documents outside a block join, and a block join would buy the ability
     * to ask "which picture matched", which nothing here asks. Stored as json and returned whole;
     * what makes them findable is {@link #IMAGE_TEXT}.
     */
    public static final String IMAGES = "images";

    /**
     * Every image's alt, title and surrounding wording, flattened into one searchable field, so a
     * page is still found by what its pictures are of.
     */
    public static final String IMAGE_TEXT = "imageText";

    /** The sort key that gives the cursor a unique tiebreaker. */
    public static final String SORT_ID = "id_sort";

}
