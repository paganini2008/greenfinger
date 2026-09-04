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

package com.github.greenfinger.shell.render;

import lombok.experimental.UtilityClass;

/**
 * How many terminal columns a string occupies.
 *
 * <p>
 * Not the same as its length. A CJK ideograph, a fullwidth form and most emoji each take two
 * columns, while combining marks and zero-width joiners take none. Padding by {@code length()}
 * therefore misaligns any table containing Chinese, Japanese or Korean text -- the column that
 * looked right in the source drifts further out with every row.
 * 
 * @Description: TextWidth
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@UtilityClass
public class TextWidth {

    /**
     * Columns occupied by the whole string, ANSI escape sequences excluded.
     */
    public int of(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int width = 0;
        int i = 0;
        int length = text.length();
        while (i < length) {
            char ch = text.charAt(i);
            if (ch == '\u001B') {
                // skip a CSI escape sequence: it renders as nothing
                int end = i + 1;
                while (end < length && text.charAt(end) != 'm') {
                    end++;
                }
                i = end + 1;
                continue;
            }
            int codePoint = text.codePointAt(i);
            width += of(codePoint);
            i += Character.charCount(codePoint);
        }
        return width;
    }

    /**
     * Columns occupied by one code point.
     */
    public int of(int codePoint) {
        if (codePoint == 0) {
            return 0;
        }
        int type = Character.getType(codePoint);
        if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
                || type == Character.COMBINING_SPACING_MARK || type == Character.FORMAT) {
            return 0;
        }
        if (codePoint < 32 || (codePoint >= 0x7F && codePoint < 0xA0)) {
            return 0;
        }
        return isWide(codePoint) ? 2 : 1;
    }

    private boolean isWide(int codePoint) {
        return (codePoint >= 0x1100 && codePoint <= 0x115F) // Hangul Jamo
                || (codePoint >= 0x2E80 && codePoint <= 0x303E) // CJK radicals, punctuation
                || (codePoint >= 0x3041 && codePoint <= 0x33FF) // kana, CJK compatibility
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF) // CJK extension A
                || (codePoint >= 0x4E00 && codePoint <= 0x9FFF) // CJK unified ideographs
                || (codePoint >= 0xA000 && codePoint <= 0xA4CF) // Yi
                || (codePoint >= 0xAC00 && codePoint <= 0xD7A3) // Hangul syllables
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF) // CJK compatibility ideographs
                || (codePoint >= 0xFE10 && codePoint <= 0xFE19) // vertical forms
                || (codePoint >= 0xFE30 && codePoint <= 0xFE6F) // CJK compatibility forms
                || (codePoint >= 0xFF00 && codePoint <= 0xFF60) // fullwidth forms
                || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6) // fullwidth signs
                || (codePoint >= 0x1F300 && codePoint <= 0x1F64F) // emoji
                || (codePoint >= 0x1F900 && codePoint <= 0x1F9FF)
                || (codePoint >= 0x20000 && codePoint <= 0x3FFFD); // CJK extensions B and beyond
    }

    /**
     * Pads to a column width, measuring by display columns rather than characters.
     */
    public String pad(String text, int width, boolean rightAlign) {
        String value = text != null ? text : "";
        int padding = width - of(value);
        if (padding <= 0) {
            return value;
        }
        String spaces = " ".repeat(padding);
        return rightAlign ? spaces + value : value + spaces;
    }

    /**
     * Shortens to a column width, appending an ellipsis when anything was dropped.
     */
    public String truncate(String text, int width) {
        if (text == null) {
            return "";
        }
        if (of(text) <= width) {
            return text;
        }
        StringBuilder str = new StringBuilder();
        int used = 0;
        int i = 0;
        while (i < text.length()) {
            int codePoint = text.codePointAt(i);
            int columns = of(codePoint);
            if (used + columns > width - 1) {
                break;
            }
            str.appendCodePoint(codePoint);
            used += columns;
            i += Character.charCount(codePoint);
        }
        return str.append('\u2026').toString();
    }

}
