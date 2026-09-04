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

import java.io.Console;
import org.springframework.shell.core.ConsoleInputProvider;
import com.github.greenfinger.shell.render.Ansi;

/**
 * The prompt, which says which program is waiting.
 *
 * <p>
 * Spring Shell's own is {@code $>}, and a bare dollar sign in a screenshot or a bug report is
 * indistinguishable from a shell. Naming it costs one line and settles every "which of these was
 * I typing into".
 *
 * <p>
 * Handed to the shell runner directly rather than declared as a {@code ConsoleInputProvider} bean.
 * The bean was created -- and the runner was still built with the autoconfiguration's own, which
 * is where {@code $>} kept coming from.
 * 
 * @Description: GreenfingerPrompt
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
public class GreenfingerPrompt extends ConsoleInputProvider {

    static final String PROMPT = "greenfinger:> ";

    @Override
    public String readInput() {
        Console console = getConsole();
        if (console == null) {
            // no terminal: end of input rather than a stack trace on the way out
            return null;
        }
        return console.readLine("%s", Ansi.green(PROMPT));
    }

}
