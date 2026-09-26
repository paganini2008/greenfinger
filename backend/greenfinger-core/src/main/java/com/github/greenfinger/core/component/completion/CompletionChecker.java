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
import com.github.greenfinger.core.component.WebCrawlerComponent;
import com.github.greenfinger.core.component.state.Dashboard;

/**
 * Decides that a crawl has reached its own end. There are exactly two: {@code maxFetchSize} reached
 * or {@code fetchDuration} elapsed. Ctrl+C, {@code interrupt} and the watchdog also end a run, but
 * as interventions -- same flag with {@code interrupted} true, and no version is published.
 *
 * <p>
 * A checker is passive: handed the dashboard, no state, no idea which node it runs on. That is what
 * lets every node reach the same answer from the same shared counters, first to notice writing it.
 *
 * @Description: CompletionChecker
 * @Author: Fred Feng
 * @Date: 04/09/2026
 * @Version 2.0.0
 */
public interface CompletionChecker extends WebCrawlerComponent {

    /** Whether the crawl has reached the limit this checker watches. */
    boolean isCompleted(CatalogDetails catalogDetails, Dashboard dashboard);

    /**
     * Whether a clock has to ask this rather than the crawl. A limit in pages can only be reached
     * by fetching one; a limit in time comes due whether or not anything is being fetched, and a
     * crawl blocked on a slow site is exactly when it matters.
     */
    default boolean scheduled() {
        return false;
    }

    default String getReason(CatalogDetails catalogDetails, Dashboard dashboard) {
        return getName();
    }

}
