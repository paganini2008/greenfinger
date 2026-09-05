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
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Where a crawl sends what it collects. Unlike 1.x, which could only say "elasticsearch or
 * nothing", and unlike the 2.0 draft, which made the three alternatives, these stack: a crawl may
 * feed any combination, and {@link #FILE} is always among them.
 *
 * <p>
 * The names describe the destination rather than the product that happens to serve it, so swapping
 * Elasticsearch for something else later does not force a rename of the enum, the column, or the
 * command line flag.
 * 
 * @Description: OutputType
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public enum OutputType {

    /**
     * Html, extracted text and images, on disk or in MinIO -- the assets/ directory under the
     * user data store. Mandatory: the database holds only metadata, so without the files there
     * is nothing for the other two to be rebuilt from.
     */
    FILE("file"),

    /** Full text search. Elasticsearch today. */
    INDEX("index"),

    /** Semantic and cross modal search. Qdrant or Weaviate. */
    VECTOR("vector");

    private final String repr;

    OutputType(String repr) {
        this.repr = repr;
    }

    @JsonValue
    public String getRepr() {
        return repr;
    }

    @JsonCreator
    public static OutputType of(String repr) {
        if (repr == null || repr.isBlank()) {
            return FILE;
        }
        String value = repr.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(t -> t.repr.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown OutputType: " + repr));
    }

    /**
     * Parses the stored comma separated form. {@link #FILE} is added whether or not it was listed,
     * which is what makes it impossible to configure a crawl whose pages are never written down.
     */
    public static Set<OutputType> parse(String text) {
        Set<OutputType> types = new LinkedHashSet<>();
        types.add(FILE);
        if (text != null && !text.isBlank()) {
            for (String part : text.split("[+,;\\s]+")) {
                if (!part.isBlank()) {
                    types.add(of(part));
                }
            }
        }
        return types;
    }

    /**
     * Parses the same form, taking it literally.
     *
     * <p>
     * {@link #parse} adds {@link #FILE} whether it was asked for or not, which is right for a
     * crawl -- a crawl that wrote no files would have nothing for the other layers to be rebuilt
     * from. It is wrong for a replay, where the file layer means "go and fetch every page again":
     * silently adding it would turn {@code replay --layers index} into a second crawl of the whole
     * site. So a replay asks for exactly what it was given, and gets files only by naming them.
     */
    public static Set<OutputType> parseExact(String text) {
        Set<OutputType> types = new LinkedHashSet<>();
        if (text != null && !text.isBlank()) {
            for (String part : text.split("[+,;\\s]+")) {
                if (!part.isBlank()) {
                    types.add(of(part));
                }
            }
        }
        return types;
    }

    public static String format(Set<OutputType> types) {
        Set<OutputType> all = new LinkedHashSet<>();
        all.add(FILE);
        if (types != null) {
            all.addAll(types);
        }
        return all.stream().map(OutputType::getRepr).collect(Collectors.joining(","));
    }

}
