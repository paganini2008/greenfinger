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

package com.github.greenfinger.shell;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import org.springframework.stereotype.Component;

/**
 * Reading a line from whoever is at the other end, and saying whether there is anybody there.
 *
 * <p>
 * Two commands need this and nothing else does. {@code catalog-save} asks a question per field, and
 * {@code status} watches for the key that ends the watch. Both have to behave when there is no
 * terminal -- a script pipes a here-document into the first, and redirects the second to a file --
 * and neither may hang waiting for input that will never arrive.
 *
 * <p>
 * {@link Console} first, because the interactive shell reads through it and a second reader on
 * {@code System.in} would take the buffered characters out from under it. The stream is the
 * fallback for the redirected case, where {@code System.console()} is null on this jdk.
 * 
 * @Description: ConsoleIO
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
@Component
public class ConsoleIO {

    /** The words that end a live view. */
    private static final java.util.Set<String> QUIT = java.util.Set.of("q", "quit", "exit");

    /** The word that abandons an interactive command. */
    public static final String CANCEL = "cancel";

    private final Console console;
    private final BufferedReader reader;
    private final InputStream in;
    private final PrintStream out;

    public ConsoleIO() {
        this(System.console(), System.in, System.out);
    }

    ConsoleIO(Console console, InputStream in, PrintStream out) {
        this.console = console;
        this.reader = console != null ? null
                : new BufferedReader(new InputStreamReader(in, Charset.defaultCharset()));
        this.in = in;
        this.out = out;
    }

    /**
     * A pair of plain streams, with no terminal behind them. What a piped invocation gets, and
     * what a test drives.
     */
    public static ConsoleIO of(InputStream in, PrintStream out) {
        return new ConsoleIO(null, in, out);
    }

    /**
     * Whether there is a person at a terminal. False when the output is redirected, which is when
     * a question is a hang rather than a question.
     */
    public boolean isInteractive() {
        return console != null;
    }

    /**
     * @return the line typed, without its newline, or null at end of input.
     */
    public String readLine(String prompt) {
        try {
            if (console != null) {
                return console.readLine("%s", prompt);
            }
            out.print(prompt);
            out.flush();
            return reader.readLine();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * A line already typed, or null when nothing is waiting. Never blocks.
     *
     * <p>
     * Read a byte at a time from the raw stream, and only while there is one to read, so nothing
     * is buffered ahead. A {@link BufferedReader} here would swallow whatever the user typed next
     * and the interactive shell would then miss their following command -- which is the failure
     * this exists to avoid, and it would look like the shell had simply ignored them.
     */
    public String pollLine() {
        try {
            if (in == null || in.available() <= 0) {
                return null;
            }
            StringBuilder line = new StringBuilder();
            while (in.available() > 0) {
                int ch = in.read();
                if (ch < 0 || ch == '\n') {
                    break;
                }
                if (ch != '\r') {
                    line.append((char) ch);
                }
            }
            return line.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Whether a line already typed asks to stop watching.
     */
    public boolean quitRequested() {
        String typed = pollLine();
        return typed != null && QUIT.contains(typed.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public static boolean isCancel(String answer) {
        return answer == null
                || CANCEL.equalsIgnoreCase(answer.trim());
    }

    public void print(String text) {
        out.println(text);
    }

    public PrintStream out() {
        return out;
    }

}
