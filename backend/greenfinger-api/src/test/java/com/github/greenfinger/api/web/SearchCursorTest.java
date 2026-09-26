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
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a cursor has to survive: going out as json and coming back as a query string.
 *
 * <p>
 * The stores were given the score as the string it arrives as, and read it as zero. The sort is by
 * score descending, so every hit then counted as sorting before the cursor and page two came back
 * empty while the total still said there were twenty-two. Nothing below this layer could catch it,
 * because below this layer the value is still a float.
 *
 * @Description: SearchCursorTest
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
class SearchCursorTest {

    @Test
    @DisplayName("the score becomes a number again; the tiebreaker stays text")
    void theScoreIsANumberAgain() {
        List<Object> typed = SearchApiController.typed(List.of("4.686497", "d579bffb-7300"));

        assertThat(typed.get(0)).isInstanceOf(Number.class);
        assertThat(((Number) typed.get(0)).doubleValue()).isEqualTo(4.686497d);
        // an id that happens to look like a number is still an id
        assertThat(SearchApiController.typed(List.of("1.0", "12345")).get(1)).isEqualTo("12345");
    }

    @Test
    @DisplayName("a cursor that is already typed is left alone")
    void alreadyTypedIsLeftAlone() {
        assertThat(SearchApiController.typed(List.of(4.5d, "an-id")).get(0)).isEqualTo(4.5d);
    }

    @Test
    @DisplayName("nothing to convert is not an error")
    void nothingToConvert() {
        assertThat(SearchApiController.typed(null)).isNull();
        assertThat(SearchApiController.typed(List.of())).isEmpty();
        // a first element that is not a score at all is handed on as it is, so the store can say
        // what is wrong with it rather than this layer guessing
        assertThat(SearchApiController.typed(List.of("not-a-score", "an-id")).get(0))
                .isEqualTo("not-a-score");
        assertThat(SearchApiController.typed(Arrays.asList(null, "an-id")).get(0)).isNull();
    }

}
