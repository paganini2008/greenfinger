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

/**
 * Builds the coordinator for one run, which is the only thing that has to change for a crawl to
 * span processes.
 *
 * <p>
 * It is a factory rather than a bean because a coordinator holds this run's frontier and this run's
 * counters, and a run is not a singleton.
 * 
 * @Description: CrawlCoordinatorFactory
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@FunctionalInterface
public interface CrawlCoordinatorFactory {

    CrawlCoordinator create(CrawlRun run);

    /**
     * The recursion never leaves this process: dispatch is a write to the local frontier.
     */
    static CrawlCoordinatorFactory local() {
        return run -> new LocalCrawlCoordinator(run.context().getCrawlFrontier(),
                run.context().getGlobalStateManager());
    }

}
