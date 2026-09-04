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

package com.github.greenfinger.shell.render;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.shell.ConsoleIO;

/**
 * 
 * @Description: LiveDashboardTest
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
class LiveDashboardTest {

    private LiveDashboard dashboard() {
        return new LiveDashboard(null, null, null,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    private ConsoleIO io(String typed) {
        return ConsoleIO.of(new ByteArrayInputStream(typed.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("the height is counted from what was printed, not worked out in advance")
    void heightIsCountedFromTheBlock() {
        assertThat(LiveDashboard.heightOf("")).isZero();
        assertThat(LiveDashboard.heightOf("one\n")).isEqualTo(1);
        assertThat(LiveDashboard.heightOf("one\ntwo\nthree\n")).isEqualTo(3);
        // no trailing newline: the last line is still on the cursor's line, so it is not counted
        assertThat(LiveDashboard.heightOf("one\ntwo")).isEqualTo(1);
    }

    @Test
    @DisplayName("watching ends when the crawl does")
    void awaitReturnsWhenTheCrawlFinishes() {
        AtomicBoolean finished = new AtomicBoolean(false);
        new Thread(() -> {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            finished.set(true);
        }).start();

        assertThat(dashboard().await(finished::get, io(""))).isTrue();
    }

    @Test
    @DisplayName("q ends the watch and says so, which is not the same as ending the crawl")
    void awaitReturnsFalseWhenTheReaderLeaves() {
        assertThat(dashboard().await(() -> false, io("q\n"))).isFalse();
    }

    @Test
    @DisplayName("with no reader there is nothing to leave with, so it waits")
    void withoutAReaderItWaitsForTheEnd() {
        AtomicBoolean finished = new AtomicBoolean(true);
        assertThat(dashboard().await(finished::get, null)).isTrue();
    }

    @Test
    void closingWithoutHavingDrawnIsHarmless() {
        LiveDashboard live = dashboard();
        live.close();
    }

}
