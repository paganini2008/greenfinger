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

package com.github.greenfinger.core.model;

import java.util.Arrays;
import java.util.Locale;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Whether the index and the vector store receive images alongside the text.
 *
 * <p>
 * Separate from {@code imageEnabled}, which decides whether images are fetched at all, because the
 * two costs are nothing alike: fetching an image is one http call, while carrying it downstream
 * inflates the index with nested documents and adds a vector for every page-image reference.
 * Crawling images under {@link #TEXT} keeps the option open -- turning them on later is a
 * {@code replay}, not a re-crawl.
 * 
 * @Description: ContentMode
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public enum ContentMode {

    /** Images are fetched and stored, but neither indexed nor embedded. */
    TEXT("text"),

    /** Images reach every configured output. The default. */
    TEXT_IMAGE("text+image");

    private final String repr;

    ContentMode(String repr) {
        this.repr = repr;
    }

    @JsonValue
    public String getRepr() {
        return repr;
    }

    public boolean includesImages() {
        return this == TEXT_IMAGE;
    }

    @JsonCreator
    public static ContentMode of(String repr) {
        if (repr == null || repr.isBlank()) {
            return TEXT_IMAGE;
        }
        String value = repr.trim().toLowerCase(Locale.ROOT).replace("_", "+").replace(",", "+");
        return Arrays.stream(values()).filter(m -> m.repr.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ContentMode: " + repr));
    }

}
