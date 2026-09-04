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

import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.component.state.Dashboard;

/**
 * Complete once the counter named by the catalog's {@code countingType} passes
 * {@code maxFetchSize}.
 *
 * <p>
 * Asked on the crawl's own thread rather than on a schedule: the counter can only move because a
 * page was fetched, so asking as each one goes past costs a comparison and stops the crawl on the
 * page that reached the limit rather than up to a tick later.
 * 
 * @Description: MaxFetchSizeCompletionChecker
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class MaxFetchSizeCompletionChecker implements CompletionChecker {

    @Override
    public String getName() {
        return "maxFetchSize";
    }

    @Override
    public boolean isCompleted(CatalogDetails catalogDetails, Dashboard dashboard) {
        Integer maxFetchSize = catalogDetails.getMaxFetchSize();
        if (maxFetchSize == null || maxFetchSize <= 0) {
            return false;
        }
        return catalogDetails.getCountingType().compare(dashboard, maxFetchSize);
    }

    @Override
    public String getReason(CatalogDetails catalogDetails, Dashboard dashboard) {
        CountingType countingType = catalogDetails.getCountingType();
        return String.format("reached maxFetchSize: %s = %d > %d", countingType.getRepr(),
                countingType.getValue(dashboard), catalogDetails.getMaxFetchSize());
    }

}
