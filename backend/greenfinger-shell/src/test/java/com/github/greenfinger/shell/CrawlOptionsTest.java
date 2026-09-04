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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.WebCrawlerException;

/**
 * 
 * @Description: CrawlOptionsTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class CrawlOptionsTest {

    @Test
    @DisplayName("one option, however it was spelled")
    void namesIgnoreCaseAndSeparators() {
        CrawlOptions options = new CrawlOptions().override("max-size", 500);

        assertThat(options.getInt("maxSize", 0)).isEqualTo(500);
        assertThat(options.getInt("max_size", 0)).isEqualTo(500);
        assertThat(options.getInt("MAXSIZE", 0)).isEqualTo(500);
    }

    @Test
    @DisplayName("a blank value never erases what is there")
    void overrideIgnoresBlanks() {
        CrawlOptions options = new CrawlOptions().override("id", "abc").override("id", "  ")
                .override("id", (Object) null);

        assertThat(options.get("id", null)).isEqualTo("abc");
    }

    @Test
    void nullableGettersDistinguishAbsentFromZero() {
        CrawlOptions options = new CrawlOptions().override("depth", 0).override("images", false);

        assertThat(options.getIntegerOrNull("depth")).isZero();
        assertThat(options.getIntegerOrNull("missing")).isNull();
        assertThat(options.getBooleanOrNull("images")).isFalse();
        assertThat(options.getBooleanOrNull("missing")).isNull();
        assertThat(options.getLongOrNull("missing")).isNull();
    }

    @Test
    @DisplayName("a value that should be a number says so plainly")
    void rejectsNonNumericValues() {
        CrawlOptions options = new CrawlOptions().override("maxSize", "many");

        assertThatThrownBy(() -> options.getInt("maxSize", 0))
                .isInstanceOf(WebCrawlerException.class).hasMessageContaining("whole number");
        assertThatThrownBy(() -> options.getLong("maxSize", 0L))
                .isInstanceOf(WebCrawlerException.class).hasMessageContaining("whole number");
    }

    @Test
    void defaultsApplyWhenNothingWasSet() {
        CrawlOptions options = new CrawlOptions();

        assertThat(options.get("url", "fallback")).isEqualTo("fallback");
        assertThat(options.getInt("depth", 7)).isEqualTo(7);
        assertThat(options.getLong("duration", 30L)).isEqualTo(30L);
        assertThat(options.getBoolean("images", true)).isTrue();
        assertThat(options.asMap()).isEmpty();
    }

}
