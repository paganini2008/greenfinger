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

package com.github.greenfinger.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.core.model.Catalog;

/**
 * The shipped initial_catalogs.json, read the way InitialCatalogs reads it.
 *
 * <p>
 * It once carried {@code maxFetchDepth}, which is not a field on {@link Catalog}. The mapper is
 * strict, so the whole file was refused and a fresh install got no catalogs at all -- with one
 * WARN to say so. A misspelled field is invisible in review and only shows up on a clean data
 * directory, which is what this test stands in for.
 *
 * @Description: InitialCatalogsFileTest
 * @Author: Fred Feng
 * @Date: 27/09/2026
 * @Version 2.0.0
 */
class InitialCatalogsFileTest {

    private static final String LOCATION = "/initial_catalogs.json";

    @Test
    @DisplayName("every shipped catalog converts to a Catalog, with no unknown field")
    void readsTheShippedFile() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        List<Map<String, Object>> rows;
        try (InputStream in = getClass().getResourceAsStream(LOCATION)) {
            assertThat(in).as(LOCATION).isNotNull();
            rows = objectMapper.readValue(in, List.class);
        }
        assertThat(rows).isNotEmpty();
        for (Map<String, Object> row : rows) {
            row.remove("description");
            Catalog catalog = objectMapper.convertValue(row, Catalog.class);
            assertThat(catalog.getName()).isNotBlank();
            assertThat(catalog.getUrl()).startsWith("http");
            assertThat(catalog.getDepth()).as("depth of '%s'", catalog.getName()).isNotNull();
            assertThat(catalog.getMaxFetchSize()).as("maxFetchSize of '%s'", catalog.getName())
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("names are unique, because the idempotent load matches on them")
    void namesAreUnique() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        List<Map<String, Object>> rows;
        try (InputStream in = getClass().getResourceAsStream(LOCATION)) {
            rows = objectMapper.readValue(in, List.class);
        }
        List<Object> names = rows.stream().map(row -> row.get("name")).toList();
        assertThat(names).doesNotHaveDuplicates();
    }

}
