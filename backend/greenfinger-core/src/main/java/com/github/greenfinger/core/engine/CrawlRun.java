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
 * One crawl about to start on this node: its assembled components, and the three things about it
 * that a coordinator has to know before the first url moves.
 *
 * @param context   the components this run will use
 * @param action    crawl or update, which is what a node joining has to be told
 * @param refresh   whether this run revisits pages it already has and merges what changed
 * @param initiator whether the command arrived here. The node it arrived at seeds the entry point
 *                  and tells the others; from then on every node dispatches and receives on equal
 *                  terms, the leader included -- there is no node that only coordinates.
 * 
 * @Description: CrawlRun
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
public record CrawlRun(WebCrawlerExecutionContext context, String action, boolean refresh,
        boolean initiator) {

    public String catalogId() {
        return context.getCatalogDetails().getId();
    }

}
