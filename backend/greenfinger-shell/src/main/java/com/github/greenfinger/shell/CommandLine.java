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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.Getter;

/**
 * Parses one command line into a command name and its options. Accepts {@code --id abc},
 * {@code --id=abc} and bare flags; Spring's {@code ApplicationArguments} takes only
 * {@code --key=value} and turns the other form's value into a stray positional argument.
 *
 * <p>
 * Long names only: a one-letter form that expands to one name here and another on the
 * {@code @Option} is not rejected but ignored, and the crawl runs with a default nobody chose.
 * Framework arguments ({@code --spring.}, {@code --logging.}, {@code --debug}) are skipped.
 * 
 * @Description: CommandLine
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@Getter
public class CommandLine {

    private static final List<String> FRAMEWORK_PREFIXES =
            List.of("--spring.", "--logging.", "--management.", "--server.", "--greenfinger.",
                    "--debug", "--trace");

    private final String command;
    private final String primaryCommand;
    private final CrawlOptions options;
    private final List<String> positionals;

    private CommandLine(String command, String primaryCommand, CrawlOptions options,
            List<String> positionals) {
        this.command = command;
        this.primaryCommand = primaryCommand;
        this.options = options;
        this.positionals = positionals;
    }

    public static CommandLine parse(String[] args) {
        CrawlOptions options = new CrawlOptions();
        List<String> positionals = new ArrayList<>();

        int i = 0;
        while (i < args.length) {
            String arg = args[i];
            if (isFrameworkArgument(arg)) {
                i++;
                continue;
            }
            if (arg.startsWith("--")) {
                String name = arg.substring(2);
                String value = null;
                int equals = name.indexOf('=');
                if (equals >= 0) {
                    value = name.substring(equals + 1);
                    name = name.substring(0, equals);
                } else if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    value = args[++i];
                } else {
                    // a bare flag means true
                    value = "true";
                }
                options.override(name, value);
            } else {
                positionals.add(arg.toLowerCase(Locale.ROOT));
            }
            i++;
        }
        // "catalog list" and "catalog-list" are the same command; the two-word form reads better
        // when typed, the hyphenated one is what the shell registers
        String primary = positionals.isEmpty() ? null : positionals.get(0);
        String joined = positionals.size() >= 2
                ? positionals.get(0) + "-" + positionals.get(1)
                : primary;
        return new CommandLine(joined, primary, options, positionals);
    }

    /**
     * Flags that must reach Spring as properties, not command options. {@code --offline} is read
     * when the model store is constructed, deep inside a running command, so a dispatcher would see
     * it too late. Translating here keeps the short spelling with the property as the only source
     * of truth. The original argument is kept, not replaced, so option parsing is untouched.
     */
    public static String[] toSpringArguments(String[] args) {
        List<String> translated = new ArrayList<>(List.of(args));
        for (String arg : args) {
            if ("--offline".equals(arg) || "--offline=true".equals(arg)) {
                translated.add("--greenfinger.embedding.offline=true");
            } else if ("--offline=false".equals(arg)) {
                translated.add("--greenfinger.embedding.offline=false");
            }
        }
        return translated.toArray(new String[0]);
    }

    private static boolean isFrameworkArgument(String arg) {
        return FRAMEWORK_PREFIXES.stream().anyMatch(arg::startsWith);
    }

    /**
     * The word for the prompt rather than for a command.
     *
     * <p>
     * Recognised here as well as in the launcher, so running the jar directly behaves the same
     * way: {@code face} is not a command that fails, it is the absence of one.
     */
    public static final String INTERACTIVE = "face";

    public boolean hasCommand() {
        return command != null && !command.isBlank() && !INTERACTIVE.equals(command);
    }

    /**
     * The first word alone, tried when the joined two-word form matches nothing.
     */
    public String getPrimaryCommand() {
        return primaryCommand;
    }

}
