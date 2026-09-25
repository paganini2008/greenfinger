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
 * Where a discovered url goes next, and when the crawl is over -- the only two points where the
 * crawl's recursion may leave this process. That is why an empty local frontier no longer means
 * finished: it means nothing to do <em>right now</em>, with work possibly arriving from a peer.
 *
 * <p>
 * The engine never asks which implementation it holds; a single process is a cluster of one, where
 * dispatch is a local queue write and empty does mean over.
 * 
 * @Description: CrawlCoordinator
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
public interface CrawlCoordinator {

    /**
     * Hands a url to whichever node will fetch it -- possibly this one.
     *
     * <p>
     * Called after the url has passed the acceptors and the dedup filter, so what arrives here is
     * work that genuinely has to be done by somebody.
     */
    void dispatch(CrawlTask task) throws Exception;

    /**
     * Tells the cluster the run is over, once, so every node publishes a
     * {@link WebCrawlerCompletionEvent} locally. Called only on the node winding the run down --
     * the announcement reaches the sender too, so announcing per node is N events per node.
     *
     * @return false when there is no cluster to tell, and the caller publishes the event itself
     */
    default boolean announceCompleted(String catalogId, int version, String reason,
            boolean interrupted) {
        return false;
    }

    /**
     * Which node this is, for the run report. One process answers "local", which reads correctly
     * in a directory that only ever has one node's reports in it.
     */
    default String nodeId() {
        return "local";
    }

    /**
     * Reports that a url reached a conclusion, whatever it was. Separate from the dispatch count
     * on purpose: the two together are what make the end of a distributed crawl decidable.
     */
    default void afterHandled(CrawlTask task) {}

    /**
     * Whether this node publishes the finished version and prunes what retention drops. Both are
     * idempotent, so this is about not doing the work three times, not correctness. Asked when the
     * crawl ends rather than when it began, so a run whose leader changed still gets published.
     */
    default boolean shouldPublish() {
        return true;
    }

    /** Released with the crawl. */
    default void close() {}

}
