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

package com.github.greenfinger.core.component.extractor;

import org.apache.commons.lang3.StringUtils;

/**
 * What the last crawl was told about a page, offered back to the site so it can answer "still the
 * same" instead of sending the page again.
 *
 * <p>
 * Only a refresh has anything to offer here; a first crawl has never seen the page. Even on a
 * refresh the saving is bandwidth and parsing rather than storage -- an unchanged page was already
 * detected by its content hash and written nowhere. What changes is that a site with ten thousand
 * pages can answer most of a merge with a header instead of a megabyte.
 *
 * @param etag the {@code ETag} the site sent last time
 * @param lastModified the {@code Last-Modified} it sent last time
 *
 * @Description: ConditionalGet
 * @Author: Fred Feng
 * @Date: 01/09/2026
 * @Version 2.0.0
 */
public record ConditionalGet(String etag, String lastModified) {

    public static final ConditionalGet NONE = new ConditionalGet(null, null);

    public static ConditionalGet of(String etag, String lastModified) {
        return isBlank(etag) && isBlank(lastModified) ? NONE
                : new ConditionalGet(trimmed(etag), trimmed(lastModified));
    }

    /** Nothing to ask with, so the request goes out as an ordinary one. */
    public boolean isEmpty() {
        return isBlank(etag) && isBlank(lastModified);
    }

    private static boolean isBlank(String value) {
        return StringUtils.isBlank(value);
    }

    private static String trimmed(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

}
