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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.model.ContentMode;
import com.github.greenfinger.core.model.ExtractorType;
import com.github.greenfinger.core.model.OutputType;

/**
 * 
 * @Description: InterviewTest
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
class InterviewTest {

    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();

    private Interview interview(String typed) {
        ConsoleIO io = new ConsoleIO(null,
                new ByteArrayInputStream(typed.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(captured, true, StandardCharsets.UTF_8));
        return new Interview(io);
    }

    private String output() {
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("return keeps what is in the brackets")
    void anEmptyAnswerKeepsTheCurrentValue() {
        Interview interview = interview("\n\n\n\n");

        assertThat(interview.text("name", "unique text", "example.com", true))
                .isEqualTo("example.com");
        assertThat(interview.integer("max-size", "1 or more", 10000, 1)).isEqualTo(10000);
        assertThat(interview.bool("images", Boolean.TRUE)).isTrue();
        assertThat(interview.extractor(ExtractorType.ADAPTIVE)).isEqualTo(ExtractorType.ADAPTIVE);
    }

    @Test
    @DisplayName("the question carries what it accepts, so nothing has to be looked up")
    void everyQuestionShowsItsRange() {
        interview("\n").integer("depth", "-1 for no limit, or 1 or more", -1, -1);

        assertThat(output()).contains("depth").contains("-1 for no limit").contains("[-1]");
    }

    @Test
    void takesWhatWasTyped() {
        Interview interview = interview("https://example.com\n42\nfalse\nselenium\ntext\n");

        assertThat(interview.text("url", "http:// or https://", null, true))
                .isEqualTo("https://example.com");
        assertThat(interview.integer("max-size", "1 or more", 10, 1)).isEqualTo(42);
        assertThat(interview.bool("images", Boolean.TRUE)).isFalse();
        assertThat(interview.extractor(ExtractorType.ADAPTIVE)).isEqualTo(ExtractorType.SELENIUM);
        assertThat(interview.content(ContentMode.TEXT_IMAGE)).isEqualTo(ContentMode.TEXT);
    }

    @Test
    @DisplayName("a value outside the range is asked again rather than accepted")
    void refusesAndAsksAgain() {
        Interview interview = interview("0\n5\n");

        assertThat(interview.integer("max-size", "1 or more saved pages", null, 1)).isEqualTo(5);
        assertThat(output()).contains("max-size takes 1 or more saved pages");
    }

    @Test
    void refusesAnUnknownExtractorAndAsksAgain() {
        Interview interview = interview("lynx\nhtmlunit\n");

        assertThat(interview.extractor(null)).isEqualTo(ExtractorType.HTMLUNIT);
        assertThat(output()).contains("lynx").contains("adaptive");
    }

    @Test
    @DisplayName("a required field with no current value is asked again")
    void requiredFieldsAreInsistedOn() {
        Interview interview = interview("\nhttps://example.com\n");

        assertThat(interview.text("url", "http:// or https://", null, true))
                .isEqualTo("https://example.com");
        assertThat(output()).contains("url is required");
    }

    @Test
    void anOptionalFieldMayBeLeftEmpty() {
        assertThat(interview("\n").text("exclude", "ant path pattern", null, false)).isEmpty();
    }

    @Test
    @DisplayName("outputs are joined with +, and file is always there")
    void outputsAlwaysIncludeFile() {
        Interview interview = interview("index+vector\n");

        Set<OutputType> outputs = interview.outputs(Set.of(OutputType.FILE));
        assertThat(outputs).containsExactly(OutputType.FILE, OutputType.INDEX, OutputType.VECTOR);
    }

    @Test
    void refusesAnUnknownOutputAndAsksAgain() {
        Interview interview = interview("sqlite\nindex\n");

        assertThat(interview.outputs(null)).containsExactly(OutputType.FILE, OutputType.INDEX);
        assertThat(output()).contains("sqlite");
    }

    @Test
    @DisplayName("a closed question takes the first answer on return")
    void chooseDefaultsToTheFirstAnswer() {
        assertThat(interview("\n").choose("start now", List.of("no", "crawl", "rebuild")))
                .isEqualTo("no");
        assertThat(interview("rebuild\n").choose("start now", List.of("no", "crawl", "rebuild")))
                .isEqualTo("rebuild");
    }

    @Test
    void refusesAnAnswerThatIsNotOnTheList() {
        Interview interview = interview("maybe\ncrawl\n");

        assertThat(interview.choose("start now", List.of("no", "crawl"))).isEqualTo("crawl");
        assertThat(output()).contains("answer one of: no | crawl");
    }

    @Test
    @DisplayName("cancel at any question abandons the whole thing")
    void cancelStopsEverything() {
        assertThatThrownBy(() -> interview("cancel\n").text("url", "a url", null, true))
                .isInstanceOf(Interview.Cancelled.class);
        assertThatThrownBy(() -> interview("cancel\n").integer("depth", "1 or more", 1, 1))
                .isInstanceOf(Interview.Cancelled.class);
        assertThatThrownBy(() -> interview("cancel\n").bool("images", true))
                .isInstanceOf(Interview.Cancelled.class);
        assertThatThrownBy(() -> interview("cancel\n").outputs(null))
                .isInstanceOf(Interview.Cancelled.class);
        assertThatThrownBy(() -> interview("cancel\n").content(null))
                .isInstanceOf(Interview.Cancelled.class);
        assertThatThrownBy(() -> interview("cancel\n").choose("start now", List.of("no")))
                .isInstanceOf(Interview.Cancelled.class);
    }

    @Test
    @DisplayName("input that simply ends is a cancel, so a piped command never hangs")
    void endOfInputCancels() {
        assertThatThrownBy(() -> interview("").text("url", "a url", null, true))
                .isInstanceOf(Interview.Cancelled.class);
    }

    @Test
    void durationsTakeTheSameShape() {
        Interview interview = interview("0\n90\n");

        assertThat(interview.duration("duration", "minutes, 1 or more", 30L, 1L)).isEqualTo(90L);
        assertThat(output()).contains("duration takes minutes, 1 or more");
    }

}
