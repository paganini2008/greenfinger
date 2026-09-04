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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import com.github.greenfinger.shell.render.Ansi;
import com.github.greenfinger.core.WebCrawlerConstants;
import com.github.greenfinger.core.model.ContentMode;
import com.github.greenfinger.core.model.ExtractorType;
import com.github.greenfinger.core.model.OutputType;

/**
 * One question per field, each with what it accepts and what it already says.
 *
 * <p>
 * A catalog has seventeen settings. As flags that is a command line nobody types twice and a
 * reference page open beside it; asked one at a time, with the current value in the brackets, the
 * whole thing is seventeen presses of return and the two that matter typed in the middle. The
 * accepted range travels with the question, so nothing has to be looked up to answer it.
 *
 * <p>
 * {@code cancel} at any question abandons the whole thing, which is the reason this exists rather
 * than a chain of reads: half a catalog written because somebody changed their mind at question
 * twelve is worse than no catalog.
 * 
 * @Description: Interview
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
public class Interview {

    /**
     * Thrown when the reader typed {@code cancel}, or the input ended. Caught by the command,
     * which then writes nothing.
     */
    public static class Cancelled extends RuntimeException {

        private static final long serialVersionUID = 5060959975919287981L;

        Cancelled() {
            super("cancelled");
        }

    }

    private final ConsoleIO io;

    public Interview(ConsoleIO io) {
        this.io = io;
    }

    /**
     * @param label what the field is called, which is also its option name.
     * @param range what it accepts, shown so nothing has to be looked up.
     * @param current the value it already has; return keeps it.
     * @param required whether an empty answer with no current value is refused.
     */
    public String text(String label, String range, String current, boolean required) {
        while (true) {
            String answer = ask(label, range, current);
            if (StringUtils.isNotBlank(answer)) {
                return answer.trim();
            }
            if (StringUtils.isNotBlank(current)) {
                return current;
            }
            if (!required) {
                return "";
            }
            io.print(Ansi.red("  " + label + " is required."));
        }
    }

    public Integer integer(String label, String range, Integer current, int min) {
        return number(label, range, current, value -> {
            int parsed = Integer.parseInt(value);
            if (parsed < min) {
                throw new NumberFormatException(label + " cannot be below " + min);
            }
            return parsed;
        });
    }

    public Long duration(String label, String range, Long current, long min) {
        return number(label, range, current, value -> {
            long parsed = Long.parseLong(value);
            if (parsed < min) {
                throw new NumberFormatException(label + " cannot be below " + min);
            }
            return parsed;
        });
    }

    private <T> T number(String label, String range, T current, Function<String, T> parser) {
        while (true) {
            String answer = ask(label, range, current != null ? String.valueOf(current) : null);
            if (StringUtils.isBlank(answer)) {
                return current;
            }
            try {
                return parser.apply(answer.trim());
            } catch (RuntimeException e) {
                io.print(Ansi.red("  " + label + " takes " + range));
            }
        }
    }

    public Boolean bool(String label, Boolean current) {
        while (true) {
            String answer = ask(label, "true | false", current != null ? String.valueOf(current)
                    : null);
            if (StringUtils.isBlank(answer)) {
                return current;
            }
            String value = answer.trim().toLowerCase(Locale.ROOT);
            if (List.of("true", "yes", "y", "1", "on").contains(value)) {
                return Boolean.TRUE;
            }
            if (List.of("false", "no", "n", "0", "off").contains(value)) {
                return Boolean.FALSE;
            }
            io.print(Ansi.red("  " + label + " takes true or false"));
        }
    }

    public ExtractorType extractor(ExtractorType current) {
        return oneOf("extractor", ExtractorType.choices().replace(", ", " | "), current,
                ExtractorType::of);
    }

    public ContentMode content(ContentMode current) {
        return oneOf("content", "text+image | text", current, ContentMode::of);
    }

    /**
     * How urls already seen are remembered.
     *
     * <p>
     * rocksdb is what ships: exact, durable, and it grows with the site. It is not the only name
     * this takes, which is why the answer is not checked against a list -- a deployment that has
     * supplied its own component factory has its own filter and its own name for it, and being
     * told here that the name is invalid would be wrong. An unknown name is refused when the crawl
     * starts, by the factory that was asked for it, which is the only thing that knows.
     */
    public String urlPathFilter(String current) {
        return oneOf("url-dedup", WebCrawlerConstants.URL_PATH_FILTER_ROCKSDB + " (built in)",
                StringUtils.defaultIfBlank(current, WebCrawlerConstants.URL_PATH_FILTER_ROCKSDB),
                answer -> answer.toLowerCase());
    }

    private <T> T oneOf(String label, String range, T current, Function<String, T> parser) {
        while (true) {
            String answer = ask(label, range, current != null ? String.valueOf(display(current))
                    : null);
            if (StringUtils.isBlank(answer)) {
                return current;
            }
            try {
                return parser.apply(answer.trim());
            } catch (RuntimeException e) {
                io.print(Ansi.red("  " + e.getMessage()));
            }
        }
    }

    /**
     * The outputs, joined with {@code +} because that is how a set of things reads.
     *
     * <p>
     * {@code file} is added whether or not it was typed: the database holds metadata alone, so a
     * catalog whose pages are never written down has nothing for the index or the vectors to be
     * rebuilt from, and no way to say what a search result actually said.
     */
    public Set<OutputType> outputs(Set<OutputType> current) {
        String shown = current == null ? null
                : String.join("+", current.stream().map(OutputType::getRepr).toList());
        while (true) {
            String answer = ask("output-types", "file+index+vector, joined with + (file is"
                    + " always on)", shown);
            if (StringUtils.isBlank(answer)) {
                return current;
            }
            try {
                Set<OutputType> parsed = new LinkedHashSet<>();
                parsed.add(OutputType.FILE);
                Arrays.stream(answer.trim().split("[+,;\\s]+")).filter(StringUtils::isNotBlank)
                        .map(OutputType::of).forEach(parsed::add);
                return parsed;
            } catch (RuntimeException e) {
                io.print(Ansi.red("  " + e.getMessage()));
            }
        }
    }

    /**
     * A closed question. Return takes the first answer listed, which is always the safe one.
     */
    public String choose(String question, List<String> answers) {
        String range = String.join(" | ", answers);
        while (true) {
            String answer = ask(question, range, answers.get(0));
            if (StringUtils.isBlank(answer)) {
                return answers.get(0);
            }
            String value = answer.trim().toLowerCase(Locale.ROOT);
            if (answers.contains(value)) {
                return value;
            }
            io.print(Ansi.red("  answer one of: " + range));
        }
    }

    private String display(Object value) {
        if (value instanceof ExtractorType extractorType) {
            return extractorType.getRepr();
        }
        if (value instanceof ContentMode contentMode) {
            return contentMode.getRepr();
        }
        return String.valueOf(value);
    }

    /**
     * Prints the question and reads the answer. The only place {@code cancel} is recognised, so
     * every question accepts it without any of them having to say so.
     */
    private String ask(String label, String range, String current) {
        String question = String.format("  %-14s %s %s ", label, Ansi.dim("(" + range + ")"),
                Ansi.cyan("[" + StringUtils.defaultIfBlank(current, "") + "]:"));
        String answer = io.readLine(question);
        if (ConsoleIO.isCancel(answer)) {
            throw new Cancelled();
        }
        return answer;
    }

}
