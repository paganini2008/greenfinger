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

package com.github.greenfinger.core.component.completion;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.CatalogFixtures;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.component.state.DefaultGlobalStateManager;
import com.github.greenfinger.core.model.Catalog;

/**
 * 
 * @Description: CompletionCheckerTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class CompletionCheckerTest {

    private DefaultGlobalStateManager stateManagerFor(CatalogDetails details) throws Exception {
        DefaultGlobalStateManager stateManager = new DefaultGlobalStateManager(details);
        stateManager.afterPropertiesSet();
        return stateManager;
    }

    @Test
    @DisplayName("the crawl stops once the counted limit is passed")
    void maxFetchSizeFiresWhenExceeded() throws Exception {
        Catalog catalog = CatalogFixtures.catalog();
        catalog.setMaxFetchSize(3);
        CatalogDetails details = CatalogFixtures.details(catalog);
        DefaultGlobalStateManager stateManager = stateManagerFor(details);
        MaxFetchSizeCompletionChecker checker = new MaxFetchSizeCompletionChecker();

        stateManager.incrementCount(0L, CountingType.SAVED_RESOURCE_COUNT, 3);
        assertThat(checker.isCompleted(details, stateManager.getDashboard())).isFalse();

        stateManager.incrementCount(0L, CountingType.SAVED_RESOURCE_COUNT);
        assertThat(checker.isCompleted(details, stateManager.getDashboard())).isTrue();
        assertThat(checker.getReason(details, stateManager.getDashboard()))
                .contains("maxFetchSize", "savedResourceCount");
        assertThat(checker.getName()).isEqualTo("maxFetchSize");
    }

    @Test
    void maxFetchSizeOfZeroMeansNoLimit() throws Exception {
        Catalog catalog = CatalogFixtures.catalog();
        catalog.setMaxFetchSize(0);
        CatalogDetails details = CatalogFixtures.details(catalog);
        DefaultGlobalStateManager stateManager = stateManagerFor(details);

        stateManager.incrementCount(0L, CountingType.SAVED_RESOURCE_COUNT, 1000);
        assertThat(new MaxFetchSizeCompletionChecker().isCompleted(details,
                stateManager.getDashboard())).isFalse();
    }

    @Test
    @DisplayName("a zero duration stops immediately; a real one does not")
    void fetchDurationFiresWhenElapsed() throws Exception {
        Catalog expired = CatalogFixtures.catalog();
        expired.setDuration(0L);
        CatalogDetails expiredDetails = CatalogFixtures.details(expired);
        FetchDurationCompletionChecker checker = new FetchDurationCompletionChecker();
        // a duration of zero means no limit rather than an instant stop
        assertThat(checker.isCompleted(expiredDetails,
                stateManagerFor(expiredDetails).getDashboard())).isFalse();

        CatalogDetails details = CatalogFixtures.details();
        assertThat(checker.isCompleted(details, stateManagerFor(details).getDashboard()))
                .isFalse();
        assertThat(checker.getReason(details, stateManagerFor(details).getDashboard()))
                .contains("fetchDuration");
        assertThat(checker.getName()).isEqualTo("fetchDuration");
    }

}
