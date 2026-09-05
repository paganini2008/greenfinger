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

import static org.assertj.core.api.Assertions.assertThat;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 
 * @Description: ConsoleIOTest
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
class ConsoleIOTest {

    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();

    private ConsoleIO io(String typed) {
        return new ConsoleIO(null, stream(typed), new PrintStream(captured, true,
                StandardCharsets.UTF_8));
    }

    private InputStream stream(String typed) {
        return new ByteArrayInputStream(typed.getBytes(StandardCharsets.UTF_8));
    }

    private String output() {
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("with no terminal the prompt is printed and the answer read from the stream")
    void readsFromTheStreamWhenThereIsNoConsole() {
        ConsoleIO io = io("first\nsecond\n");

        assertThat(io.readLine("name: ")).isEqualTo("first");
        assertThat(io.readLine("url: ")).isEqualTo("second");
        assertThat(io.readLine("more: ")).isNull();
        assertThat(output()).contains("name: ").contains("url: ");
    }

    @Test
    void isInteractiveIsFalseWithoutATerminal() {
        assertThat(io("").isInteractive()).isFalse();
    }

    @Test
    @DisplayName("polling never blocks, and never reads past the line it returns")
    void pollLineTakesOnlyWhatIsWaiting() {
        ConsoleIO io = io("q\nnext-command\n");

        assertThat(io.pollLine()).isEqualTo("q");
        // the rest is still on the stream: a reader that buffered ahead here would swallow the
        // user's following command and the shell would look like it had ignored them
        assertThat(io.pollLine()).isEqualTo("next-command");
        assertThat(io.pollLine()).isNull();
    }

    @Test
    void nothingWaitingIsNotAQuit() {
        assertThat(io("").quitRequested()).isFalse();
    }

    @Test
    void everySpellingOfQuitEndsTheWatch() {
        for (String word : new String[] {"q", "quit", "exit", " Q ", "QUIT"}) {
            assertThat(io(word + "\n").quitRequested()).as(word).isTrue();
        }
        assertThat(io("no\n").quitRequested()).isFalse();
    }

    @Test
    @DisplayName("end of input cancels, so a piped command never hangs on a question")
    void endOfInputIsACancel() {
        assertThat(ConsoleIO.isCancel(null)).isTrue();
        assertThat(ConsoleIO.isCancel("cancel")).isTrue();
        assertThat(ConsoleIO.isCancel("  CANCEL  ")).isTrue();
        assertThat(ConsoleIO.isCancel("")).isFalse();
        assertThat(ConsoleIO.isCancel("https://example.com")).isFalse();
    }

    @Test
    void printsThroughTheGivenStream() {
        ConsoleIO io = io("");
        io.print("a line");

        assertThat(output()).contains("a line");
        assertThat(io.out()).isNotNull();
    }

}
