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

package com.github.greenfinger.core.engine;

import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.component.state.GlobalStateManager;

/**
 * The recursion stays in this process: the next call is a write to the frontier on this disk, and
 * the crawl is over when that frontier is empty.
 *
 * <p>
 * This is what a cluster of one degenerates to, and it is also what core is able to test without
 * opening a port.
 * 
 * @Description: LocalCrawlCoordinator
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
public class LocalCrawlCoordinator implements CrawlCoordinator {

    private final CrawlFrontier frontier;
    private final GlobalStateManager stateManager;

    public LocalCrawlCoordinator(CrawlFrontier frontier, GlobalStateManager stateManager) {
        this.frontier = frontier;
        this.stateManager = stateManager;
    }

    @Override
    public void dispatch(CrawlTask task) throws Exception {
        // counted before it is queued, never after: the moment it is on a frontier it may be
        // taken and finished by somebody else, and a handled that arrives before its own dispatch
        // would make the two counters cross
        stateManager.incrementCount(task.getTimestamp(), CountingType.TOTAL_URL_COUNT);
        if (!frontier.put(task)) {
            // queued before, so nobody will report it handled -- and a dispatch nothing answers
            // for is one the completion test waits on for ever
            afterHandled(task);
            stateManager.incrementCount(task.getTimestamp(), CountingType.EXISTING_URL_COUNT);
        }
    }

    @Override
    public void afterHandled(CrawlTask task) {
        stateManager.incrementCount(task.getTimestamp(), CountingType.HANDLED_URL_COUNT);
    }

    @Override
    public boolean shouldPublish() {
        return true;
    }

}
