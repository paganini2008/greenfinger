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

import lombok.Builder;
import lombok.Singular;
import lombok.Getter;

/**
 * 
 * @Description: SearchRequest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@Getter
@Builder
public class SearchRequest {

    private final String keyword;

    /** Restricts to one category; null searches them all. */
    private final String cat;

    /** Restricts to one catalog; null searches them all. */
    private final String catalog;

    /**
     * The versions to search, each as {@code <catalogId>:<version>}.
     *
     * <p>
     * A list rather than a single number because every catalog has its own current version: one
     * search across several of them is an any-of match on this one field, which is also exactly how
     * the vector store is queried.
     */
    private final java.util.List<String> catalogVersions;

    /**
     * Rank detail pages above listings. On by default: a listing matches the same words as the
     * page it links to and is almost never the answer.
     */
    @Builder.Default
    private final boolean preferDetailPages = true;

    /**
     * The cursor from the previous page's {@link SearchResponse#getNextCursor()}.
     *
     * <p>
     * Elasticsearch refuses {@code from + size} beyond 10,000, and raising that limit trades a
     * cluster's memory for the privilege of paging deeply. The way past it is a cursor: each page
     * carries the sort values of its last hit, and the next page resumes from there. Cost stays
     * flat however deep the paging goes, which {@code from} never does.
     */
    private final java.util.List<Object> cursor;

    @Builder.Default
    private final int page = 1;

    @Builder.Default
    private final int pageSize = 10;

}
