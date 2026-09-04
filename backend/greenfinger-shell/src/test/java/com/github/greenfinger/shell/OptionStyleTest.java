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
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import com.github.greenfinger.shell.command.CatalogCommands;
import com.github.greenfinger.shell.command.CrawlCommands;
import com.github.greenfinger.shell.command.QueryCommands;

/**
 * That every option is long form, and that every one of them says what it accepts.
 *
 * <p>
 * There are two parsers -- the prompt uses the shell's, which reads the {@code @Option}
 * annotations, and a command given on the command line is parsed by {@link CommandLine}. While
 * there were one-letter forms the two had to agree on what each letter meant, and when they did
 * not the failure was silent: an option written under one name and read under another is not
 * rejected, it is ignored, and the crawl runs with the default. Two of the five letters were
 * doing exactly that when this was last checked.
 *
 * <p>
 * They are gone, and this is what keeps them gone. The second half is the other half of the same
 * bargain: an option nobody can guess the values of is an option nobody can use, so every one of
 * them carries its range in its description.
 * 
 * @Description: OptionStyleTest
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
class OptionStyleTest {

    private static final Class<?>[] COMMAND_CLASSES =
            {CrawlCommands.class, CatalogCommands.class, QueryCommands.class};

    @Test
    @DisplayName("no option has a one letter form")
    void everyOptionIsLongForm() {
        List<String> letters = new ArrayList<>();
        forEachOption((method, option) -> {
            // the annotation's default is not a letter; both spellings of "none" are treated as
            // none rather than as an option called " "
            if (option.shortName() != '\0' && !Character.isWhitespace(option.shortName())) {
                letters.add(method.getName() + " --" + option.longName() + " has -"
                        + option.shortName());
            }
        });
        assertThat(letters).isEmpty();
    }

    @Test
    @DisplayName("every option says what it accepts")
    void everyOptionDescribesItsRange() {
        List<String> undescribed = new ArrayList<>();
        forEachOption((method, option) -> {
            if (option.description() == null || option.description().isBlank()) {
                undescribed.add(method.getName() + " --" + option.longName());
            }
        });
        assertThat(undescribed).isEmpty();
    }

    @Test
    @DisplayName("a catalog is addressed by --id, never by --catalog or --name")
    void catalogsAreAddressedById() {
        List<String> byName = new ArrayList<>();
        forEachOption((method, option) -> {
            if (List.of("catalog", "name", "q", "before").contains(option.longName())) {
                undescribed(byName, method, option);
            }
        });
        assertThat(byName).isEmpty();
    }

    private void undescribed(List<String> found, Method method, Option option) {
        found.add(method.getName() + " --" + option.longName());
    }

    @Test
    @DisplayName("the long name is what the command line parses")
    void longNamesAreWhatIsParsed() {
        CommandLine parsed = CommandLine
                .parse(new String[] {"crawl", "--id", "abc", "--threads", "4"});

        assertThat(parsed.getOptions().get("id", null)).isEqualTo("abc");
        assertThat(parsed.getOptions().getInt("threads", 0)).isEqualTo(4);
    }

    private void forEachOption(java.util.function.BiConsumer<Method, Option> visitor) {
        for (Class<?> type : COMMAND_CLASSES) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getAnnotation(Command.class) == null) {
                    continue;
                }
                for (Parameter parameter : method.getParameters()) {
                    Option option = parameter.getAnnotation(Option.class);
                    if (option != null) {
                        visitor.accept(method, option);
                    }
                }
            }
        }
    }

}
