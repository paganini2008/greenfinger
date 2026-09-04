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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 
 * @Description: OutputTypeTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class OutputTypeTest {

    @Test
    void parsesEveryName() {
        assertThat(OutputType.of("file")).isEqualTo(OutputType.FILE);
        assertThat(OutputType.of("INDEX")).isEqualTo(OutputType.INDEX);
        assertThat(OutputType.of(" vector ")).isEqualTo(OutputType.VECTOR);
    }

    @Test
    void blankMeansFile() {
        assertThat(OutputType.of(null)).isEqualTo(OutputType.FILE);
        assertThat(OutputType.of("  ")).isEqualTo(OutputType.FILE);
    }

    @Test
    void rejectsAnUnknownName() {
        assertThatThrownBy(() -> OutputType.of("mongodb"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roundTripsThroughItsStoredForm() {
        for (OutputType outputType : OutputType.values()) {
            assertThat(OutputType.of(outputType.getRepr())).isEqualTo(outputType);
        }
    }

    @Test
    void parsesTheStoredCommaSeparatedForm() {
        assertThat(OutputType.parse("file,index,vector")).containsExactly(OutputType.FILE,
                OutputType.INDEX, OutputType.VECTOR);
        assertThat(OutputType.parse("index vector")).contains(OutputType.INDEX,
                OutputType.VECTOR);
    }

    @org.junit.jupiter.api.DisplayName("file is implied, so a crawl whose pages go nowhere cannot be configured")
    @Test
    void fileIsAlwaysIncluded() {
        assertThat(OutputType.parse("index")).contains(OutputType.FILE, OutputType.INDEX);
        // "+" is the form the interview and the help both use
        assertThat(OutputType.parse("file+index+vector")).containsExactly(OutputType.FILE,
                OutputType.INDEX, OutputType.VECTOR);
        assertThat(OutputType.parseExact("index+vector")).containsExactly(OutputType.INDEX,
                OutputType.VECTOR);
        assertThat(OutputType.parse(null)).containsExactly(OutputType.FILE);
        assertThat(OutputType.parse("")).containsExactly(OutputType.FILE);
    }

    @Test
    void formatsBackWithFilePresent() {
        assertThat(OutputType.format(java.util.Set.of(OutputType.INDEX))).contains("file")
                .contains("index");
        assertThat(OutputType.format(null)).isEqualTo("file");
    }

    @Test
    void theDeclarationOrderIsTheWriteOrder() {
        // the composite channel sorts on this, so file must come first and vector last
        assertThat(OutputType.FILE.ordinal()).isLessThan(OutputType.INDEX.ordinal());
        assertThat(OutputType.INDEX.ordinal()).isLessThan(OutputType.VECTOR.ordinal());
    }

    @Test
    @DisplayName("parseExact takes the list literally, so a replay is not turned into a crawl")
    void parseExactDoesNotAddTheFileLayer() {
        // parse adds FILE because a crawl that wrote no files would leave the other layers nothing
        // to be rebuilt from. For a replay the file layer means "fetch every page again", so
        // adding it unasked would turn a request to rebuild an index into a second crawl
        assertThat(OutputType.parse("index")).contains(OutputType.FILE);
        assertThat(OutputType.parseExact("index")).containsExactly(OutputType.INDEX);
        assertThat(OutputType.parseExact("file,index")).containsExactly(OutputType.FILE,
                OutputType.INDEX);
        assertThat(OutputType.parseExact("")).isEmpty();
        assertThat(OutputType.parseExact(null)).isEmpty();
    }

}
