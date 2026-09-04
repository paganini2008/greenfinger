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
 * Decides that a crawl has reached its own end.
 *
 * <p>
 * There are exactly two of these, and between them they are the whole definition of a finished
 * crawl: the catalog's {@code maxFetchSize} was reached, or its {@code fetchDuration} ran out.
 * Nothing else completes a crawl. Ctrl+C, the {@code interrupt} command and the watchdog all end
 * a run too, but they are interventions -- they set the same flag with {@code interrupted} true,
 * and the search version is not published.
 *
 * <p>
 * A checker is passive: it is handed the dashboard and answers a question about it. It has no
 * state of its own, no idea which node it is running on, and no way to reach the crawl. That is
 * what lets every node run the same checks against the same shared counters and reach the same
 * answer -- whoever notices first writes the flag, and everyone else reads it.
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
     * Whether this has to be asked by a clock rather than by the crawl.
     *
     * <p>
     * A limit measured in pages can only be reached by fetching one, so asking on the way past
     * costs nothing and stops the crawl the moment it is due. A limit measured in time is the
     * opposite: it comes due whether or not anything is being fetched, and a crawl whose threads
     * are all blocked on a slow site is exactly when it matters. So that one is asked on a
     * schedule.
     */
    default boolean scheduled() {
        return false;
    }

    default String getReason(CatalogDetails catalogDetails, Dashboard dashboard) {
        return getName();
    }

}
