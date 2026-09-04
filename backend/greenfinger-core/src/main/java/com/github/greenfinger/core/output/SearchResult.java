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

import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * One hit from a search over crawled content.
 * 
 * @Description: SearchResult
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@Getter
@Builder
public class SearchResult {

    public static final String FIELD_TITLE = "title";
    public static final String FIELD_CONTENT = "content";
    public static final String FIELD_CAT = "cat";
    public static final String FIELD_CATALOG = "catalog";
    public static final String FIELD_VERSION = "version";

    private final String id;
    private final String title;
    private final String url;
    private final String cat;
    private final String catalog;
    private final Integer version;
    private final Date createTime;
    private final double score;

    /**
     * The matching passages, with the search terms marked. What makes a result list readable.
     */
    private final List<String> highlights;

}
