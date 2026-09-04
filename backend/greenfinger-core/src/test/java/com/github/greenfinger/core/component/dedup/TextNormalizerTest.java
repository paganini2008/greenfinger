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

package com.github.greenfinger.core.component.dedup;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 
 * @Description: TextNormalizerTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class TextNormalizerTest {

    @Test
    @DisplayName("case, punctuation and runs of whitespace are all flattened")
    void normalize() {
        assertThat(TextNormalizer.normalize("  Hello,   WORLD!!  ")).isEqualTo("hello world");
        assertThat(TextNormalizer.normalize(null)).isEmpty();
        assertThat(TextNormalizer.normalize("   ")).isEmpty();
    }

    @Test
    @DisplayName("two renderings of the same sentence normalise alike")
    void normalizeIsStableAcrossFormatting() {
        assertThat(TextNormalizer.normalize("The quick brown fox."))
                .isEqualTo(TextNormalizer.normalize("the  QUICK - brown,  fox!"));
    }

    @Test
    void latinTextSplitsIntoWords() {
        assertThat(TextNormalizer.tokenize("hello world again"))
                .containsExactly("hello", "world", "again");
    }

    @Test
    @DisplayName("CJK has no spaces to split on, so it becomes character bigrams")
    void cjkSplitsIntoBigrams() {
        List<String> tokens = TextNormalizer.tokenize(TextNormalizer.normalize("\u4e2d\u6587\u5206\u8bcd"));
        assertThat(tokens).containsExactly("\u4e2d\u6587", "\u6587\u5206", "\u5206\u8bcd");
    }

    @Test
    void mixedTextKeepsBothForms() {
        List<String> tokens =
                TextNormalizer.tokenize(TextNormalizer.normalize("java \u7f16\u7a0b guide"));
        assertThat(tokens).contains("java", "guide", "\u7f16\u7a0b");
    }

    @Test
    void emptyInputTokenizesToNothing() {
        assertThat(TextNormalizer.tokenize("")).isEmpty();
        assertThat(TextNormalizer.tokenize(null)).isEmpty();
    }

}
