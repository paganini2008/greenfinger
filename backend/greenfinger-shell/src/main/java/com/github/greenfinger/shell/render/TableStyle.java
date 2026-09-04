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

/**
 * Box drawing characters for {@link TextTable}.
 * 
 * @Description: TableStyle
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public enum TableStyle {

    ROUNDED("\u256d", "\u256e", "\u2570", "\u256f", "\u2500", "\u2502", "\u252c", "\u2534",
            "\u251c", "\u2524", "\u253c"),

    HEAVY("\u250f", "\u2513", "\u2517", "\u251b", "\u2501", "\u2503", "\u2533", "\u253b",
            "\u2523", "\u252b", "\u254b"),

    ASCII("+", "+", "+", "+", "-", "|", "+", "+", "+", "+", "+");

    final String topLeft;
    final String topRight;
    final String bottomLeft;
    final String bottomRight;
    final String horizontal;
    final String vertical;
    final String topJoin;
    final String bottomJoin;
    final String leftJoin;
    final String rightJoin;
    final String cross;

    TableStyle(String topLeft, String topRight, String bottomLeft, String bottomRight,
            String horizontal, String vertical, String topJoin, String bottomJoin, String leftJoin,
            String rightJoin, String cross) {
        this.topLeft = topLeft;
        this.topRight = topRight;
        this.bottomLeft = bottomLeft;
        this.bottomRight = bottomRight;
        this.horizontal = horizontal;
        this.vertical = vertical;
        this.topJoin = topJoin;
        this.bottomJoin = bottomJoin;
        this.leftJoin = leftJoin;
        this.rightJoin = rightJoin;
        this.cross = cross;
    }

}
