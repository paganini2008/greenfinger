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
 * Where a discovered url goes next, and when the crawl is over.
 *
 * <p>
 * A crawl is a recursive function: handle a page, and for every link on it, call handle again. The
 * two methods here are the only points where that recursion is allowed to leave this process --
 * the call goes out over the network and lands in some other node's {@code handle}, which is why
 * the second question stops being a local one. A node's own frontier running dry no longer means
 * the crawl finished; it means this node has nothing to do <em>at this moment</em>, and more work
 * may arrive from a peer a millisecond later.
 *
 * <p>
 * The engine never asks which of the two it is talking to. A single process is a cluster of one:
 * the dispatch is a local queue write and the crawl is over when that queue is empty, which is
 * exactly what the distributed answer degenerates to when there are no peers.
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
     * Tells the cluster the run is over, once, so that every node can publish a
     * {@link WebCrawlerCompletionEvent} locally.
     *
     * <p>
     * Called on the node that winds the run down, and only there -- the announcement reaches
     * everybody including the sender, so announcing it on each node would be one event per node
     * per node.
     *
     * @return false when there is no cluster to tell, and the caller publishes the event itself.
     *         One process is not a degenerate cluster here: there is nothing to send and nobody
     *         to send it to, and a local publish is the whole of it.
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
     * Whether this node is the one that makes the finished version visible to search, and prunes
     * what the retention policy no longer wants.
     *
     * <p>
     * Neither is a correctness mechanism: publishing the same version twice writes the same row
     * twice and pruning what is already pruned removes nothing. Asking one node is about not
     * doing the same work three times. On one process that is whoever ran the command; across a
     * cluster it is the leader, asked at the moment the crawl ends rather than when it began, so
     * that a crawl whose leader changed part way through still gets published.
     */
    default boolean shouldPublish() {
        return true;
    }

    /** Released with the crawl. */
    default void close() {}

}
