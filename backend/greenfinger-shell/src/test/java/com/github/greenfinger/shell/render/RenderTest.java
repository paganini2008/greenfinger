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

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 
 * @Description: RenderTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class RenderTest {

    @Test
    @DisplayName("a CJK ideograph occupies two terminal columns, not one")
    void measuresWideCharacters() {
        assertThat(TextWidth.of("abc")).isEqualTo(3);
        assertThat(TextWidth.of("\u4e2d\u6587")).isEqualTo(4);
        assertThat(TextWidth.of("a\u4e2db")).isEqualTo(4);
        assertThat(TextWidth.of("")).isZero();
        assertThat(TextWidth.of(null)).isZero();
    }

    @Test
    @DisplayName("colour codes render as nothing and must not count toward the width")
    void ignoresEscapeSequences() {
        assertThat(TextWidth.of("\u001B[32mabc\u001B[0m")).isEqualTo(3);
    }

    @Test
    void padsByColumnsRatherThanCharacters() {
        assertThat(TextWidth.pad("\u4e2d\u6587", 6, false)).hasSize(4);
        assertThat(TextWidth.of(TextWidth.pad("\u4e2d\u6587", 6, false))).isEqualTo(6);
        assertThat(TextWidth.of(TextWidth.pad("ab", 6, true))).isEqualTo(6);
        assertThat(TextWidth.pad("abcdef", 3, false)).isEqualTo("abcdef");
    }

    @Test
    void truncatesWithAnEllipsis() {
        assertThat(TextWidth.truncate("abcdefgh", 5)).endsWith("\u2026");
        assertThat(TextWidth.of(TextWidth.truncate("abcdefgh", 5))).isLessThanOrEqualTo(5);
        assertThat(TextWidth.truncate("abc", 10)).isEqualTo("abc");
        assertThat(TextWidth.truncate(null, 5)).isEmpty();
    }

    @Test
    @DisplayName("a table of Chinese text lines up, which is the whole point of measuring columns")
    void tableAlignsWideText() {
        String rendered = TextTable.of("Name", "Value").row("\u4e2d\u6587\u540d\u79f0", "1")
                .row("ascii", "22").render();

        java.util.List<String> lines = rendered.lines().toList();
        int width = TextWidth.of(lines.get(0));
        assertThat(lines).allSatisfy(line -> assertThat(TextWidth.of(line)).isEqualTo(width));
    }

    @Test
    void tableRendersHeadersRowsAndBorders() {
        String rendered = TextTable.of("A", "B").row(1, 2).row(3, null).render();

        assertThat(rendered).contains("A", "B", "1", "2", "3");
        assertThat(rendered.lines().count()).isEqualTo(6);
    }

    @Test
    void tableSupportsTitleAlignmentAndCaps() {
        TextTable table = TextTable.of("Key", "Value").title("Report").rightAlign(1)
                .maxWidth(1, 6).style(TableStyle.ASCII).row("k", "a-very-long-value");

        String rendered = table.render();
        assertThat(rendered).contains("Report").contains("\u2026").contains("+");
        assertThat(table.lineCount()).isEqualTo(6);
    }

    @Test
    void ansiFallsBackToPlainTextWhenColourIsOff() {
        // tests do not run on a terminal, so colour is disabled and the text passes through
        assertThat(Ansi.enabled()).isFalse();
        assertThat(Ansi.green("ok")).isEqualTo("ok");
        assertThat(Ansi.bold("ok")).isEqualTo("ok");
        assertThat(Ansi.dim("ok")).isEqualTo("ok");
        assertThat(Ansi.red("ok")).isEqualTo("ok");
        assertThat(Ansi.yellow("ok")).isEqualTo("ok");
        assertThat(Ansi.cyan("ok")).isEqualTo("ok");
        assertThat(Ansi.redraw(3)).isEmpty();
    }

    @Test
    @DisplayName("search markers become colour on a terminal and disappear off one")
    void highlightStripsMarkersWhenColourIsOff() {
        assertThat(Ansi.highlight("a <em>match</em> here", Ansi.YELLOW))
                .isEqualTo("a match here");
        assertThat(Ansi.highlight(null, Ansi.YELLOW)).isEmpty();
    }

}
