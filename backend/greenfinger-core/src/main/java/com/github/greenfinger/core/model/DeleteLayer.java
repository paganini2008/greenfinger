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
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Which stores a delete touches. All four hold every version, so a version can be removed from any
 * combination of them -- dropping the vectors of an old version while keeping its files and its
 * index, say.
 *
 * <p>
 * The order these are declared in is the order deletion happens in, and it is the reverse of the
 * order writing happens in. The database goes last because until then it is the list of what there
 * is to delete.
 *
 * <p>
 * The crawl's working state -- the frontier and the two dedup filters, under the system data
 * directory -- is not a layer of its own. It goes with {@link #DB}: those stores exist to answer
 * "have I already fetched this", and the rows are what that question is asked about, so removing
 * the rows and keeping the filters would leave a version that nothing can crawl again.
 * 
 * @Description: DeleteLayer
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public enum DeleteLayer {

    VECTOR("vector"),

    INDEX("index"),

    FILE("file"),

    /**
     * The rows, and with them the RocksDB stores of the versions being removed -- the frontier
     * and the two dedup filters. They are removed together and not separately selectable.
     */
    DB("db");

    private final String repr;

    DeleteLayer(String repr) {
        this.repr = repr;
    }

    public String getRepr() {
        return repr;
    }

    /**
     * Parses the command line form. {@code all}, or nothing at all, means every layer.
     *
     * <p>
     * {@code +} separates, because that is what the help and the documentation say and what
     * {@code output-types} uses; comma, semicolon and whitespace do too, so a line typed either
     * way works.
     */
    public static Set<DeleteLayer> parse(String text) {
        if (text == null || text.isBlank() || "all".equalsIgnoreCase(text.trim())) {
            return EnumSet.allOf(DeleteLayer.class);
        }
        Set<DeleteLayer> layers = EnumSet.noneOf(DeleteLayer.class);
        for (String part : text.split("[+,;\\s]+")) {
            if (part.isBlank()) {
                continue;
            }
            String value = part.trim().toLowerCase(Locale.ROOT);
            if ("all".equals(value)) {
                return EnumSet.allOf(DeleteLayer.class);
            }
            layers.add(Arrays.stream(values()).filter(l -> l.repr.equals(value)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown layer: " + part)));
        }
        return layers.isEmpty() ? EnumSet.allOf(DeleteLayer.class) : layers;
    }

}
