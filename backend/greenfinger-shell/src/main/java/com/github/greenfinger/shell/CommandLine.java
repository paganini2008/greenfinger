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
 * Parses one command line into a command name and its options.
 *
 * <p>
 * Accepts what a user would naturally type -- {@code --id abc}, {@code --id=abc}, and a bare
 * {@code --refresh} for a flag. Spring's own {@code ApplicationArguments} understands only the
 * {@code --key=value} form and would silently turn the value of a space-separated pair into a
 * stray positional argument.
 *
 * <p>
 * Long names only. There were five one-letter forms, and they were a standing source of the one
 * bug that never announces itself: a letter expanded to one name here and declared as another on
 * the {@code @Option} annotation is not rejected, it is ignored, and the crawl runs with a default
 * nobody chose. Two of the five were doing exactly that. Options are now typed out.
 *
 * <p>
 * Arguments meant for the framework rather than for us -- anything under {@code --spring.},
 * {@code --logging.} or {@code --debug} -- are recognised and skipped.
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
     * Flags that have to reach Spring as properties rather than as command options.
     *
     * <p>
     * {@code --offline} is one: the model store reads it when it is constructed, which happens deep
     * inside a command that is already running, so a dispatcher that noticed the flag would notice
     * it too late. Translating it here keeps what a user types short -- {@code --offline} rather
     * than {@code --greenfinger.embedding.offline=true} -- without the setting having two sources
     * of truth, since the property remains the only thing anything reads.
     *
     * <p>
     * The original argument is kept rather than replaced, so option parsing and the positional
     * arguments that name the command are untouched.
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
