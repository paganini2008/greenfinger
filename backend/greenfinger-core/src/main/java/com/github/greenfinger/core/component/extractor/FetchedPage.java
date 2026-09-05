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

/**
 * One fetch: the html, or the site saying it has not changed.
 *
 * <p>
 * The validators are carried back out so they can be stored against the page and offered again on
 * the next merge. An engine that cannot ask a conditional question returns them as null, and the
 * next merge simply asks unconditionally -- the mechanism degrades to what it was before.
 *
 * @param html what came back, empty when nothing did
 * @param notModified the site answered 304: no body, and nothing to do
 * @param etag the {@code ETag} to offer next time, if the site sent one
 * @param lastModified the {@code Last-Modified} to offer next time, if the site sent one
 *
 * @Description: FetchedPage
 * @Author: Fred Feng
 * @Date: 01/09/2026
 * @Version 2.0.0
 */
public record FetchedPage(String html, boolean notModified, String etag, String lastModified) {

    /** A page that came back in full, from an engine that reports no validators. */
    public static FetchedPage of(String html) {
        return new FetchedPage(html, false, null, null);
    }

    public static FetchedPage notModified(ConditionalGet conditions) {
        return new FetchedPage("", true, conditions.etag(), conditions.lastModified());
    }

}
