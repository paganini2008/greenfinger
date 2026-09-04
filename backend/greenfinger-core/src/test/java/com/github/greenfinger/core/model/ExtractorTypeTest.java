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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 
 * @Description: ExtractorTypeTest
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
class ExtractorTypeTest {

    @Test
    void readsEveryName() {
        assertThat(ExtractorType.of("adaptive")).isEqualTo(ExtractorType.ADAPTIVE);
        assertThat(ExtractorType.of("restclient")).isEqualTo(ExtractorType.RESTCLIENT);
        assertThat(ExtractorType.of("HtmlUnit")).isEqualTo(ExtractorType.HTMLUNIT);
        assertThat(ExtractorType.of(" playwright ")).isEqualTo(ExtractorType.PLAYWRIGHT);
        assertThat(ExtractorType.of("selenium")).isEqualTo(ExtractorType.SELENIUM);
    }

    @Test
    @DisplayName("the two 1.x names for restclient still load")
    void acceptsTheOldSpellings() {
        assertThat(ExtractorType.of("default")).isEqualTo(ExtractorType.RESTCLIENT);
        assertThat(ExtractorType.of("resttemplate")).isEqualTo(ExtractorType.RESTCLIENT);
    }

    @Test
    @DisplayName("nothing given is nothing chosen, so a caller can fall back to its default")
    void blankIsNull() {
        assertThat(ExtractorType.of(null)).isNull();
        assertThat(ExtractorType.of("  ")).isNull();
    }

    @Test
    @DisplayName("an unknown name is refused, and the message says what to type instead")
    void refusesAnUnknownName() {
        assertThatThrownBy(() -> ExtractorType.of("lynx"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lynx")
                .hasMessageContaining("adaptive").hasMessageContaining("selenium");
        assertThat(ExtractorType.choices()).contains("adaptive").contains("restclient")
                .contains("htmlunit").contains("playwright").contains("selenium");
    }

    @Test
    @DisplayName("json is the same string the column holds, so nothing downstream had to change")
    void serialisesAsItsName() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        assertThat(objectMapper.writeValueAsString(ExtractorType.HTMLUNIT))
                .isEqualTo("\"htmlunit\"");
        assertThat(objectMapper.readValue("\"adaptive\"", ExtractorType.class))
                .isEqualTo(ExtractorType.ADAPTIVE);

        Catalog catalog = new Catalog();
        catalog.setExtractorType(ExtractorType.SELENIUM);
        assertThat(objectMapper.writeValueAsString(catalog)).contains("\"extractor\":\"selenium\"");
    }

    @Test
    void theCatalogStoresTheNameAndReadsBackTheEnum() {
        Catalog catalog = new Catalog();
        assertThat(catalog.getExtractorType()).isNull();

        catalog.setExtractorType(ExtractorType.PLAYWRIGHT);
        assertThat(catalog.getExtractorValue()).isEqualTo("playwright");
        assertThat(catalog.getExtractorType()).isEqualTo(ExtractorType.PLAYWRIGHT);

        catalog.setExtractorType(null);
        assertThat(catalog.getExtractorValue()).isNull();
    }

}
