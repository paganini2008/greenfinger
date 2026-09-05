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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import lombok.experimental.UtilityClass;

/**
 * Reduces page text to the part that identifies the document, so that two renderings of the same
 * article hash alike. Collapses whitespace, folds case and unicode form, and drops punctuation --
 * the things a template changes without the content changing.
 * 
 * @Description: TextNormalizer
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@UtilityClass
public class TextNormalizer {

    public String normalize(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        String value = Normalizer.normalize(text, Normalizer.Form.NFKC);
        value = value.toLowerCase(Locale.ROOT);
        StringBuilder str = new StringBuilder(value.length());
        boolean lastWasSpace = true;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                str.append(ch);
                lastWasSpace = false;
            } else if (!lastWasSpace) {
                str.append(' ');
                lastWasSpace = true;
            }
        }
        return str.toString().trim();
    }

    /**
     * Splits normalised text into the features a simhash is built from: latin words as they stand,
     * and CJK text as overlapping character bigrams, since it carries no spaces to split on.
     */
    public List<String> tokenize(String normalizedText) {
        List<String> tokens = new ArrayList<>();
        if (StringUtils.isBlank(normalizedText)) {
            return tokens;
        }
        StringBuilder latin = new StringBuilder();
        char previousCjk = 0;
        for (int i = 0; i < normalizedText.length(); i++) {
            char ch = normalizedText.charAt(i);
            if (isCjk(ch)) {
                if (latin.length() > 0) {
                    tokens.add(latin.toString());
                    latin.setLength(0);
                }
                if (previousCjk != 0) {
                    tokens.add(new String(new char[] {previousCjk, ch}));
                }
                previousCjk = ch;
            } else if (Character.isLetterOrDigit(ch)) {
                previousCjk = 0;
                latin.append(ch);
            } else {
                previousCjk = 0;
                if (latin.length() > 0) {
                    tokens.add(latin.toString());
                    latin.setLength(0);
                }
            }
        }
        if (latin.length() > 0) {
            tokens.add(latin.toString());
        }
        return tokens;
    }

    private boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS.equals(block)
                || Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A.equals(block)
                || Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS.equals(block)
                || Character.UnicodeBlock.HIRAGANA.equals(block)
                || Character.UnicodeBlock.KATAKANA.equals(block)
                || Character.UnicodeBlock.HANGUL_SYLLABLES.equals(block);
    }

}
