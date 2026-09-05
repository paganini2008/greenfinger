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

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 
 * @Description: SearchResponse
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@Getter
@Builder
public class SearchResponse {

    private final List<SearchResult> results;
    private final long total;
    private final int page;
    private final int pageSize;
    private final long elapsedMillis;

    /**
     * Feed this back as the next request's cursor to read past the 10,000 result ceiling. Null once
     * there is nothing more to read.
     */
    private final List<Object> nextCursor;

    public int getTotalPages() {
        return pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
    }

}
