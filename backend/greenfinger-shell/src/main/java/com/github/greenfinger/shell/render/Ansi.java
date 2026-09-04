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
 * The handful of terminal escape sequences the console output needs.
 *
 * <p>
 * Colour is switched off when the output is not a terminal, so redirecting to a file gives clean
 * text rather than escape sequences, and honours the {@code NO_COLOR} convention.
 * 
 * @Description: Ansi
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@UtilityClass
public class Ansi {

    private static final boolean ENABLED = isEnabled();

    public static final String RESET = "\u001B[0m";
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String MAGENTA = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";

    private boolean isEnabled() {
        if (System.getenv("NO_COLOR") != null) {
            return false;
        }
        String term = System.getenv("TERM");
        if ("dumb".equals(term)) {
            return false;
        }
        return System.console() != null;
    }

    public boolean enabled() {
        return ENABLED;
    }

    public String paint(String text, String colour) {
        return ENABLED ? colour + text + RESET : text;
    }

    public String bold(String text) {
        return paint(text, BOLD);
    }

    public String dim(String text) {
        return paint(text, DIM);
    }

    public String green(String text) {
        return paint(text, GREEN);
    }

    public String yellow(String text) {
        return paint(text, YELLOW);
    }

    public String red(String text) {
        return paint(text, RED);
    }

    public String cyan(String text) {
        return paint(text, CYAN);
    }

    /**
     * Replaces the {@code <em>} markers a search engine returns with colour, or removes them when
     * the output is not a terminal. Emitting escape codes into a redirected file would leave the
     * markers visible as noise.
     */
    public String highlight(String text, String colour) {
        if (text == null) {
            return "";
        }
        if (!ENABLED) {
            return text.replace("<em>", "").replace("</em>", "");
        }
        return text.replace("<em>", colour).replace("</em>", RESET);
    }

    /**
     * Moves the cursor up and clears those lines, so a block can be redrawn where it stands
     * instead of scrolling a new copy into view.
     */
    public String redraw(int lines) {
        if (!ENABLED || lines <= 0) {
            return "";
        }
        return "\u001B[" + lines + "A" + "\u001B[0J";
    }

}
