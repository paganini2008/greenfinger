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
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The prompt, and the file the history outlives a session in.
 *
 * <p>
 * The line editing itself is JLine's and is not retested here. What is worth a test is the half
 * that decides where the history goes and that a session with no terminal still reads a line,
 * because that is every test and every container started without a tty.
 *
 * @Description: GreenfingerPromptTest
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
class GreenfingerPromptTest {

    @Test
    @DisplayName("the history goes where it was told to")
    void thePathCanBeNamed(@TempDir Path directory) {
        Path wanted = directory.resolve("typed.txt");
        System.setProperty(GreenfingerPrompt.HISTORY_PROPERTY, wanted.toString());
        try {
            assertThat(GreenfingerPrompt.historyFile()).isEqualTo(wanted);
        } finally {
            System.clearProperty(GreenfingerPrompt.HISTORY_PROPERTY);
        }
    }

    @Test
    @DisplayName("otherwise beside the models, and the directory is made")
    void otherwiseUnderTheHome() {
        Path file = GreenfingerPrompt.historyFile();
        assertThat(file).hasFileName("shell_history");
        assertThat(file.getParent()).hasFileName(".greenfinger");
        // what somebody typed belongs to them, not to one installation of four
        assertThat(file.getParent().getParent().toString())
                .isEqualTo(System.getProperty("user.home"));
    }

    @Test
    @DisplayName("no terminal is not an error: the line is still read")
    void noTerminalStillReads() throws Exception {
        // surefire gives the tests no console at all, so this is the fallback path end to end
        assertThat(new GreenfingerPrompt().readInput()).isNull();
    }

}
