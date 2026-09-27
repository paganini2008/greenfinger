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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.springframework.shell.core.ConsoleInputProvider;
import org.springframework.shell.core.command.Command;
import org.springframework.shell.core.command.CommandRegistry;
import com.github.greenfinger.shell.render.Ansi;
import lombok.extern.slf4j.Slf4j;

/**
 * The line somebody types, read with the editing and the history a terminal is expected to have.
 *
 * <p>
 * Spring Shell 4 reads a line with {@link Console#readLine}, which has neither: the arrow keys
 * arrive as escape sequences printed into the line, and a command is retyped in full every time.
 * On a tool whose commands carry thirty-six character ids that is most of the typing. JLine gives
 * the history, the editing and a file that outlives the session.
 *
 * <p>
 * It falls back to the console when there is no terminal to build -- input from a pipe, a
 * container started without a tty, a test -- because those read a line and expect nothing else,
 * and a line editor with nowhere to draw would fail where reading a line would have worked.
 *
 * @Description: GreenfingerPrompt
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class GreenfingerPrompt extends ConsoleInputProvider {

    static final String PROMPT = "greenfinger:> ";

    /**
     * Where the history is kept. Beside the models rather than in the installation: what somebody
     * typed belongs to them, and they may drive four clusters from the same terminal.
     */
    static final String HISTORY_PROPERTY = "greenfinger.shell.history";

    private final CommandRegistry registry;

    private LineReader reader;
    private boolean lineEditingUnavailable;

    /**
     * @param registry what tab offers. Null is allowed and turns completion off rather than the
     *                 prompt: a session with history and no completion is still a session.
     */
    public GreenfingerPrompt(CommandRegistry registry) {
        this.registry = registry;
    }

    public GreenfingerPrompt() {
        this(null);
    }

    @Override
    public String readInput() throws Exception {
        LineReader lineReader = lineReader();
        if (lineReader == null) {
            return fromConsole();
        }
        try {
            return lineReader.readLine(Ansi.green(PROMPT));
        } catch (UserInterruptException e) {
            // ctrl+c abandons the line being typed, as it does in a shell. Leaving is `exit`,
            // which is a decision rather than a slip of the hand
            return "";
        } catch (EndOfFileException e) {
            // ctrl+d: there is no more input, which is how a session ends
            return null;
        }
    }

    /** Built once, on the first line, and never retried after a terminal could not be had. */
    private LineReader lineReader() {
        if (reader != null || lineEditingUnavailable) {
            return reader;
        }
        try {
            Terminal terminal = TerminalBuilder.builder().dumb(false).build();
            reader = LineReaderBuilder.builder().terminal(terminal).completer(completer())
                    .variable(LineReader.HISTORY_FILE, historyFile())
                    // enough to page back through a session's work, and it is a text file
                    .variable(LineReader.HISTORY_FILE_SIZE, 2000)
                    .variable(LineReader.HISTORY_SIZE, 500)
                    // a command typed twice in a row is one line of history, and a line that
                    // starts with a space is not kept at all
                    .option(LineReader.Option.HISTORY_IGNORE_DUPS, true)
                    .option(LineReader.Option.HISTORY_IGNORE_SPACE, true)
                    .build();
        } catch (Exception e) {
            // no terminal: a pipe, a container with no tty, a test. Reading a line still works
            lineEditingUnavailable = true;
            log.debug("No terminal for line editing, falling back to the console", e);
        }
        return reader;
    }

    /**
     * Tab offers the command names, and the options of the command already typed.
     *
     * <p>
     * Names first because they are what a session starts with, and the options only once there is
     * a command to have them: offering {@code --keep-latest} before anything is typed is a list of
     * every flag in the product. The description rides along, so tab twice is also the help.
     */
    private Completer completer() {
        return (LineReader reader, ParsedLine line, List<Candidate> candidates) -> {
            if (registry == null) {
                return;
            }
            if (line.wordIndex() == 0) {
                for (Command command : registry.getCommands()) {
                    candidates.add(new Candidate(command.getName(), command.getName(),
                            command.getGroup(), command.getDescription(), null, null, true));
                }
                return;
            }
            Command command = registry.getCommandByName(line.words().get(0));
            if (command == null) {
                return;
            }
            for (var option : command.getOptions()) {
                if (StringUtils.isBlank(option.longName())) {
                    continue;
                }
                String flag = "--" + option.longName();
                candidates.add(new Candidate(flag, flag, null, option.description(), null, null,
                        false));
            }
        };
    }

    static Path historyFile() {
        String configured = System.getProperty(HISTORY_PROPERTY,
                System.getenv("GF_SHELL_HISTORY"));
        if (StringUtils.isNotBlank(configured)) {
            return Paths.get(configured);
        }
        Path file = Paths.get(System.getProperty("user.home"), ".greenfinger", "shell_history");
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            log.debug("Cannot make {}; history will not be kept", file.getParent(), e);
        }
        return file;
    }

    private String fromConsole() {
        Console console = getConsole();
        if (console == null) {
            // no terminal: end of input rather than a stack trace on the way out
            return null;
        }
        return console.readLine("%s", Ansi.green(PROMPT));
    }

}
