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

import java.util.concurrent.TimeUnit;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.state.Dashboard;

/**
 * Complete once the crawl has been running for {@code fetchDuration} minutes.
 *
 * <p>
 * Asked by the clock, not by the crawl. Time passes whether or not a page is being fetched, and a
 * run whose threads are all sitting in a socket read on a site that stopped answering is precisely
 * the run this limit exists for -- asking only on the way past a fetch would never ask it again.
 * 
 * @Description: FetchDurationCompletionChecker
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class FetchDurationCompletionChecker implements CompletionChecker {

    @Override
    public String getName() {
        return "fetchDuration";
    }

    @Override
    public boolean isCompleted(CatalogDetails catalogDetails, Dashboard dashboard) {
        Long fetchDuration = catalogDetails.getFetchDuration();
        if (fetchDuration == null || fetchDuration <= 0) {
            return false;
        }
        return dashboard.getElapsedTime() > TimeUnit.MINUTES.toMillis(fetchDuration);
    }

    @Override
    public boolean scheduled() {
        return true;
    }

    @Override
    public String getReason(CatalogDetails catalogDetails, Dashboard dashboard) {
        return String.format("reached fetchDuration: %d minute(s)",
                catalogDetails.getFetchDuration());
    }

}
