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

package com.github.greenfinger.core.component.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.CatalogFixtures;

/**
 * 
 * @Description: CountingTypeTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class CountingTypeTest {

    @Test
    @DisplayName("the stored ordinals are the ones 1.x wrote, so existing rows keep their meaning")
    void storedValuesAreStable() {
        assertThat(CountingType.TOTAL_URL_COUNT.getValue()).isZero();
        assertThat(CountingType.INVALID_URL_COUNT.getValue()).isEqualTo(1);
        assertThat(CountingType.EXISTING_URL_COUNT.getValue()).isEqualTo(2);
        assertThat(CountingType.FILTERED_URL_COUNT.getValue()).isEqualTo(3);
        assertThat(CountingType.SAVED_RESOURCE_COUNT.getValue()).isEqualTo(4);
        assertThat(CountingType.INDEXED_RESOURCE_COUNT.getValue()).isEqualTo(5);
        assertThat(CountingType.SAVED_IMAGE_COUNT.getValue()).isEqualTo(6);
        assertThat(CountingType.DUPLICATED_CONTENT_COUNT.getValue()).isEqualTo(7);
        assertThat(CountingType.HANDLED_URL_COUNT.getValue()).isEqualTo(8);
        assertThat(CountingType.ABANDONED_URL_COUNT.getValue()).isEqualTo(9);
        assertThat(CountingType.VECTORED_RESOURCE_COUNT.getValue()).isEqualTo(10);
    }

    @Test
    @DisplayName("the two outputs are counted apart: an index that works says nothing about vectors")
    void indexAndVectorAreCountedApart() {
        DefaultDashboard dashboard = new DefaultDashboard(CatalogFixtures.details());
        dashboard.counterOf(CountingType.INDEXED_RESOURCE_COUNT).addAndGet(2);
        dashboard.counterOf(CountingType.VECTORED_RESOURCE_COUNT).incrementAndGet();

        assertThat(CountingType.INDEXED_RESOURCE_COUNT.getValue(dashboard)).isEqualTo(2);
        assertThat(CountingType.VECTORED_RESOURCE_COUNT.getValue(dashboard)).isEqualTo(1);
    }

    @Test
    void roundTripsThroughItsStoredValue() {
        for (CountingType countingType : CountingType.values()) {
            assertThat(CountingType.valueOf(countingType.getValue())).isEqualTo(countingType);
            assertThat(countingType.getRepr()).isNotBlank();
        }
    }

    @Test
    void rejectsAnUnknownValue() {
        assertThatThrownBy(() -> CountingType.valueOf(99))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparesAgainstTheLimit() throws Exception {
        DefaultGlobalStateManager stateManager =
                new DefaultGlobalStateManager(CatalogFixtures.details());
        stateManager.afterPropertiesSet();
        Dashboard dashboard = stateManager.getDashboard();

        assertThat(CountingType.SAVED_RESOURCE_COUNT.compare(dashboard, 1)).isFalse();
        stateManager.incrementCount(0L, CountingType.SAVED_RESOURCE_COUNT, 2);
        assertThat(CountingType.SAVED_RESOURCE_COUNT.compare(dashboard, 1)).isTrue();
        // a limit of zero or less means no limit
        assertThat(CountingType.SAVED_RESOURCE_COUNT.compare(dashboard, 0)).isFalse();
    }

}
