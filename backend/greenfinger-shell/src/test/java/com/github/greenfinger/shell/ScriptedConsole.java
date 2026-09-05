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

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A terminal that answers what it was told to answer.
 *
 * <p>
 * In this package because {@link ConsoleIO}'s stream constructor is package private -- the
 * three-argument form takes a {@link java.io.Console}, and there is no way to make one.
 *
 * <p>
 * Writes through {@code System.out} rather than through a stream captured at construction, so a
 * {@link ConsoleCapture} opened around one question still sees the question.
 * 
 * @Description: ScriptedConsole
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
public class ScriptedConsole extends ConsoleIO {

    private final Deque<String> answers = new ArrayDeque<>();

    public ScriptedConsole() {
        super(null, InputStream.nullInputStream(), System.out);
    }

    public ScriptedConsole script(String... lines) {
        answers.clear();
        answers.addAll(List.of(lines));
        return this;
    }

    @Override
    public boolean isInteractive() {
        return true;
    }

    @Override
    public void print(String text) {
        System.out.println(text);
    }

    @Override
    public String readLine(String prompt) {
        System.out.print(prompt);
        // nothing left is end of input, which every question treats as a cancel
        return answers.poll();
    }

}
