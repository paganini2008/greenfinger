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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 
 * @Description: CommandLineTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class CommandLineTest {

    @Test
    @DisplayName("a space separated pair is one option, not an option and a stray word")
    void parsesSpaceSeparatedOptions() {
        CommandLine line = CommandLine.parse(
                new String[] {"crawl", "--url", "https://a.com", "--max-size", "50"});

        assertThat(line.getCommand()).isEqualTo("crawl");
        assertThat(line.getOptions().get("url", null)).isEqualTo("https://a.com");
        assertThat(line.getOptions().getInt("maxSize", 0)).isEqualTo(50);
    }

    @Test
    void parsesEqualsSeparatedOptions() {
        CommandLine line =
                CommandLine.parse(new String[] {"crawl", "--url=https://a.com", "--depth=3"});

        assertThat(line.getOptions().get("url", null)).isEqualTo("https://a.com");
        assertThat(line.getOptions().getInt("depth", 0)).isEqualTo(3);
    }

    @Test
    @DisplayName("there are no one letter options, and a stray one is not an option")
    void takesLongNamesOnly() {
        CommandLine line =
                CommandLine.parse(new String[] {"crawl", "--id", "abc", "--threads", "4"});

        assertThat(line.getOptions().get("id", null)).isEqualTo("abc");
        assertThat(line.getOptions().getInt("threads", 0)).isEqualTo(4);

        // there were five letters, and two of them expanded to a name nothing downstream read --
        // so the option was accepted and then silently ignored. A letter is now not an option at
        // all, which is a word the command does not know rather than a default nobody chose
        CommandLine letters = CommandLine.parse(new String[] {"crawl", "-i", "abc"});
        assertThat(letters.getOptions().get("id", null)).isNull();
        assertThat(letters.getOptions().get("i", null)).isNull();
    }

    @Test
    @DisplayName("a bare flag means true")
    void treatsABareFlagAsTrue() {
        CommandLine line = CommandLine.parse(new String[] {"crawl", "--fresh", "--url", "x"});

        assertThat(line.getOptions().getBoolean("fresh", false)).isTrue();
        assertThat(line.getOptions().get("url", null)).isEqualTo("x");
    }

    @Test
    @DisplayName("two words are one command, so 'catalog list' works as well as 'catalog-list'")
    void joinsTwoWordCommands() {
        CommandLine line = CommandLine.parse(new String[] {"catalog", "list"});

        assertThat(line.getCommand()).isEqualTo("catalog-list");
        assertThat(line.getPrimaryCommand()).isEqualTo("catalog");
    }

    @Test
    void singleWordCommandsHaveNoJoinedForm() {
        CommandLine line = CommandLine.parse(new String[] {"status"});
        assertThat(line.getCommand()).isEqualTo("status");
        assertThat(line.getPrimaryCommand()).isEqualTo("status");
    }

    @Test
    @DisplayName("framework arguments belong to Spring, not to the crawl")
    void ignoresFrameworkArguments() {
        CommandLine line = CommandLine.parse(new String[] {"crawl",
                "--spring.shell.interactive.enabled=false", "--logging.level.root=INFO", "--debug",
                "--url", "https://a.com"});

        assertThat(line.getCommand()).isEqualTo("crawl");
        assertThat(line.getOptions().get("url", null)).isEqualTo("https://a.com");
        assertThat(line.getOptions().asMap()).doesNotContainKey("springshellinteractiveenabled");
    }

    @Test
    void anEmptyLineHasNoCommand() {
        assertThat(CommandLine.parse(new String[0]).hasCommand()).isFalse();
        assertThat(CommandLine.parse(new String[] {"--url", "x"}).hasCommand()).isFalse();
    }

    @Test
    @DisplayName("'face' is the word for the prompt, so it is the absence of a command")
    void faceIsNotACommand() {
        CommandLine line = CommandLine.parse(new String[] {"face"});

        assertThat(line.getCommand()).isEqualTo("face");
        // not dispatched: the shell takes over instead of the one-shot runner failing on a word
        // it does not know
        assertThat(line.hasCommand()).isFalse();
    }

    @Test
    void commandsAreCaseInsensitive() {
        assertThat(CommandLine.parse(new String[] {"CRAWL"}).getCommand()).isEqualTo("crawl");
    }


    @Test
    @DisplayName("--offline becomes the property, because that is what the model store reads")
    void translatesTheOfflineFlag() {
        String[] translated =
                CommandLine.toSpringArguments(new String[] {"crawl", "--url", "x", "--offline"});

        assertThat(translated).contains("--greenfinger.embedding.offline=true");
        // the original is kept, so the command and its options still parse the same way
        assertThat(translated).contains("crawl", "--url", "x", "--offline");
    }

    @Test
    void translatesTheExplicitForms() {
        assertThat(CommandLine.toSpringArguments(new String[] {"--offline=true"}))
                .contains("--greenfinger.embedding.offline=true");
        assertThat(CommandLine.toSpringArguments(new String[] {"--offline=false"}))
                .contains("--greenfinger.embedding.offline=false");
    }

    @Test
    void leavesEveryOtherArgumentAlone() {
        String[] args = {"models", "pull", "--layers", "text"};

        assertThat(CommandLine.toSpringArguments(args)).containsExactly(args);
    }

}
