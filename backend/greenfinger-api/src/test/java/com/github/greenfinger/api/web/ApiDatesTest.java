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

package com.github.greenfinger.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.api.web.GreenfingerWebConfiguration.ApiDates;

/**
 * The three ways a date arrives in a query string.
 *
 * @Description: ApiDatesTest
 * @Author: Fred Feng
 * @Date: 23/09/2026
 * @Version 2.0.0
 */
class ApiDatesTest {

    @Test
    @DisplayName("a day on its own is the moment it starts, in utc")
    void aPlainDate() {
        assertThat(ApiDates.parse("2026-09-01"))
                .isEqualTo(Date.from(Instant.parse("2026-09-01T00:00:00Z")));
    }

    @Test
    @DisplayName("an instant is read as it always was, with a zone or without")
    void anInstant() {
        Date expected = Date.from(Instant.parse("2026-09-01T09:30:00Z"));

        assertThat(ApiDates.parse("2026-09-01T09:30:00Z")).isEqualTo(expected);
        assertThat(ApiDates.parse("2026-09-01T09:30:00.000Z")).isEqualTo(expected);
        // what a browser's date input produces, which carries no zone
        assertThat(ApiDates.parse("2026-09-01T09:30:00")).isEqualTo(expected);
    }

    @Test
    @DisplayName("nothing is nothing, not the epoch")
    void emptyIsNull() {
        assertThat(ApiDates.parse("")).isNull();
        assertThat(ApiDates.parse("   ")).isNull();
        assertThat(ApiDates.parse(null)).isNull();
    }

    @Test
    @DisplayName("anything else says what it wanted rather than naming a java class")
    void saysWhatItWanted() {
        assertThatThrownBy(() -> ApiDates.parse("last tuesday"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2026-09-01");
    }

}
