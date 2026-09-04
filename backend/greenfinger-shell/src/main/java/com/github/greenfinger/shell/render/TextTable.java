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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A box-drawn terminal table.
 *
 * <p>
 * Column widths are measured in display columns rather than characters, so a table holding Chinese
 * text lines up as well as one holding only ASCII. Colour applied to a cell does not disturb the
 * layout either: escape sequences are excluded from the measurement.
 * 
 * @Description: TextTable
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class TextTable {

    private final List<String> headers;
    private final List<List<String>> rows = new ArrayList<>();
    private final Map<Integer, Boolean> rightAligned = new HashMap<>();
    private final Map<Integer, Integer> maxWidths = new HashMap<>();

    private TableStyle style = TableStyle.ROUNDED;
    private String title;

    private TextTable(List<String> headers) {
        this.headers = new ArrayList<>(headers);
    }

    public static TextTable of(String... headers) {
        return new TextTable(Arrays.asList(headers));
    }

    public TextTable style(TableStyle style) {
        this.style = style;
        return this;
    }

    public TextTable title(String title) {
        this.title = title;
        return this;
    }

    /** Numbers read better flushed right. */
    public TextTable rightAlign(int... columns) {
        for (int column : columns) {
            rightAligned.put(column, Boolean.TRUE);
        }
        return this;
    }

    /** Caps a column, shortening longer values with an ellipsis. Useful for urls. */
    public TextTable maxWidth(int column, int width) {
        maxWidths.put(column, width);
        return this;
    }

    public TextTable row(Object... cells) {
        List<String> row = new ArrayList<>(cells.length);
        for (Object cell : cells) {
            row.add(cell != null ? String.valueOf(cell) : "");
        }
        rows.add(row);
        return this;
    }

    public String render() {
        int columns = headers.size();
        int[] widths = new int[columns];
        for (int i = 0; i < columns; i++) {
            widths[i] = TextWidth.of(headers.get(i));
        }
        for (List<String> row : rows) {
            for (int i = 0; i < columns && i < row.size(); i++) {
                widths[i] = Math.max(widths[i], TextWidth.of(row.get(i)));
            }
        }
        for (int i = 0; i < columns; i++) {
            Integer cap = maxWidths.get(i);
            if (cap != null) {
                widths[i] = Math.min(widths[i], cap);
            }
        }

        StringBuilder str = new StringBuilder();
        if (title != null) {
            str.append(Ansi.bold(title)).append(System.lineSeparator());
        }
        str.append(border(widths, style.topLeft, style.topJoin, style.topRight));
        str.append(renderRow(headers, widths, true));
        str.append(border(widths, style.leftJoin, style.cross, style.rightJoin));
        for (List<String> row : rows) {
            str.append(renderRow(row, widths, false));
        }
        str.append(border(widths, style.bottomLeft, style.bottomJoin, style.bottomRight));
        return str.toString();
    }

    private String border(int[] widths, String left, String join, String right) {
        StringBuilder str = new StringBuilder(left);
        for (int i = 0; i < widths.length; i++) {
            str.append(style.horizontal.repeat(widths[i] + 2));
            str.append(i < widths.length - 1 ? join : right);
        }
        return str.append(System.lineSeparator()).toString();
    }

    private String renderRow(List<String> cells, int[] widths, boolean header) {
        StringBuilder str = new StringBuilder(style.vertical);
        for (int i = 0; i < widths.length; i++) {
            String cell = i < cells.size() ? cells.get(i) : "";
            Integer cap = maxWidths.get(i);
            if (cap != null) {
                cell = TextWidth.truncate(cell, cap);
            }
            String padded =
                    TextWidth.pad(cell, widths[i], Boolean.TRUE.equals(rightAligned.get(i)));
            str.append(' ').append(header ? Ansi.bold(padded) : padded).append(' ')
                    .append(style.vertical);
        }
        return str.append(System.lineSeparator()).toString();
    }

    /**
     * Number of terminal lines {@link #render()} produces, so a caller redrawing in place knows how
     * far to move the cursor back.
     */
    public int lineCount() {
        return rows.size() + 4 + (title != null ? 1 : 0);
    }

}
