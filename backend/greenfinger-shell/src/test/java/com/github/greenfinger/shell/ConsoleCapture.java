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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Captures what a command printed, so a test can assert on what the user would have seen.
 *
 * <p>
 * Both streams are captured separately, because which of the two a message went to is itself part
 * of the behaviour: results belong on stdout, failures on stderr.
 * 
 * @Description: ConsoleCapture
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class ConsoleCapture implements AutoCloseable {

    private final PrintStream original = System.out;
    private final PrintStream originalError = System.err;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errorBuffer = new ByteArrayOutputStream();

    public ConsoleCapture() {
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(errorBuffer, true, StandardCharsets.UTF_8));
    }

    public String output() {
        System.out.flush();
        return buffer.toString(StandardCharsets.UTF_8);
    }

    public String errorOutput() {
        System.err.flush();
        return errorBuffer.toString(StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        System.setOut(original);
        System.setErr(originalError);
    }

}
