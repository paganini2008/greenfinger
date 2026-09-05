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
 * What a crawled site is about.
 *
 * <p>
 * It was free text, and free text is the wrong shape for the job it does. The category is a search
 * facet: it appears in the index, it narrows a query, and it groups the catalog list. A facet only
 * works when everyone spells it the same, and a free-text column guarantees that sooner or later
 * somebody types {@code News}, {@code news} and {@code 新闻} for the same three sites and the facet
 * silently splits into three.
 *
 * <h2>Why these eight</h2>
 * Broad enough that most sites land somewhere without deliberation, and few enough to fit in a
 * dropdown that can be read at a glance. They are topics rather than site shapes -- what the pages
 * are about, not whether the site is a forum or a blog -- because that is the question a person
 * narrowing a search is asking.
 *
 * <h2>Why OTHER rather than a rejection</h2>
 * A closed list with no escape makes the crawler refuse work that is otherwise perfectly fine, and
 * whoever hits that will pick whichever value is nearest rather than the right one, which corrupts
 * the facet far worse than an honest {@code other} would. Anything unrecognised -- including every
 * value a 1.x catalog may be carrying -- becomes {@link #OTHER} rather than an error.
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
