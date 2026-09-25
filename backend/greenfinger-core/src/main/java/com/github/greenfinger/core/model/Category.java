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
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What a crawled site is about. A facet, not free text: it appears in the index, narrows a query and
 * groups the catalog list, and a facet only works when everyone spells it the same -- free text
 * splits {@code News}, {@code news} and {@code 新闻} into three.
 *
 * <p>
 * Eight topics, broad enough that most sites land somewhere and few enough to read at a glance;
 * topics rather than site shapes, because that is what somebody narrowing a search asks. Anything
 * unrecognised, including 1.x values, becomes {@link #OTHER} rather than an error -- a refusal just
 * makes people pick the nearest value, which corrupts the facet worse.
 * 
 * @Description: Category
 * @Author: Fred Feng
 * @Date: 05/09/2026
 * @Version 2.0.0
 */
public enum Category {

    NEWS("news"),

    TECH("tech"),

    BUSINESS("business"),

    FOOD("food"),

    TRAVEL("travel"),

    HEALTH("health"),

    EDUCATION("education"),

    ENTERTAINMENT("entertainment"),

    /** Everything else, and everything that was written before this was a closed list. */
    OTHER("other");

    private final String repr;

    Category(String repr) {
        this.repr = repr;
    }

    @JsonValue
    public String getRepr() {
        return repr;
    }

    /**
     * @return {@link #OTHER} for anything unrecognised, and for nothing at all. Never null and
     *         never an exception: see the class comment.
     */
    @JsonCreator
    public static Category of(String repr) {
        if (repr == null || repr.isBlank()) {
            return OTHER;
        }
        String value = repr.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(c -> c.repr.equals(value)).findFirst()
                .orElse(OTHER);
    }

    /** The accepted values, for a message that has to tell somebody what to type. */
    public static String choices() {
        return Arrays.stream(values()).map(Category::getRepr).collect(Collectors.joining(", "));
    }

}
