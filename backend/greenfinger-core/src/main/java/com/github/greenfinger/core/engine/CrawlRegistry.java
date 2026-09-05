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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import com.github.greenfinger.core.component.state.Dashboard;
import com.github.greenfinger.core.component.state.ReadonlyDashboard;

/**
 * The crawls running in this process, and the last result of the ones that have finished.
 *
 * <p>
 * This is what lets a second command ask a running crawl to stop, or report on one that has already
 * ended. 1.x kept the same map but could only ever answer for a live crawl; keeping the final
 * snapshot means a summary is still available afterwards, which is when it is usually wanted.
 * 
 * @Description: CrawlRegistry
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class CrawlRegistry {

    private final Map<String, WebCrawlerExecutionContext> running = new ConcurrentHashMap<>();
    private final Map<String, Dashboard> lastKnown = new ConcurrentHashMap<>();

    public void register(String catalogId, WebCrawlerExecutionContext context) {
        running.put(catalogId, context);
    }

    /**
     * Takes the crawl out of the running set, keeping a frozen copy of its final counters.
     */
    public void unregister(String catalogId) {
        WebCrawlerExecutionContext context = running.remove(catalogId);
        if (context != null && context.getGlobalStateManager() != null) {
            lastKnown.put(catalogId,
                    new ReadonlyDashboard(context.getGlobalStateManager().getDashboard()));
        }
    }

    /**
     * The live context of a crawl running here, or null. Distributed dispatch needs it: a url that
     * arrives from a peer has to reach this node's frontier for that catalog, and the frontier
     * belongs to the run rather than to the process.
     */
    public WebCrawlerExecutionContext getContext(String catalogId) {
        return running.get(catalogId);
    }

    public boolean isRunning(String catalogId) {
        WebCrawlerExecutionContext context = running.get(catalogId);
        return context != null && !context.isCompleted();
    }

    public List<String> getRunningCatalogIds() {
        return running.keySet().stream().filter(this::isRunning).toList();
    }

    /**
     * Asks a running crawl to stop. It winds down at the next check rather than being killed, so
     * whatever is in flight still reaches the output channel.
     *
     * @return false when no such crawl is running.
     */
    public boolean interrupt(String catalogId) {
        WebCrawlerExecutionContext context = running.get(catalogId);
        if (context == null) {
            return false;
        }
        context.getGlobalStateManager().interrupt("interrupted by request");
        return true;
    }

    /**
     * The live dashboard while a crawl runs, or its final snapshot afterwards.
     */
    public Optional<Dashboard> getDashboard(String catalogId) {
        WebCrawlerExecutionContext context = running.get(catalogId);
        if (context != null && context.getGlobalStateManager() != null) {
            return Optional.of(context.getGlobalStateManager().getDashboard());
        }
        return Optional.ofNullable(lastKnown.get(catalogId));
    }

    public void clear() {
        running.clear();
        lastKnown.clear();
    }

}
