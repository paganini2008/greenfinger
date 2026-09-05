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

import com.github.greenfinger.core.ManagedBeanLifeCycle;
import com.github.greenfinger.core.component.WebCrawlerComponent;

/**
 * The queue of urls waiting to be fetched, and the reason an interrupted crawl can be resumed.
 *
 * <p>
 * A crawl is a graph traversal, so the only thing that describes "where it had got to" is the set
 * of urls it had discovered but not yet visited. 1.x kept that set in memory and, on restart, tried
 * to reconstruct it from the single most recently saved page -- which is unrelated to what was
 * pending, so everything queued at the moment of interruption was simply lost. Persisting the
 * frontier is what Scrapy's JOBDIR, Nutch's CrawlDb and Heritrix's frontier all do, and it makes
 * resume exact.
 *
 * <p>
 * Because the frontier lives beside the visited set in RocksDB rather than in the record database,
 * resume works whether or not database backup is switched on.
 * 
 * @Description: CrawlFrontier
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public interface CrawlFrontier extends WebCrawlerComponent, ManagedBeanLifeCycle {

    /**
     * Adds a url to be fetched. Durable before it returns, so a crash cannot lose it.
     *
     * @return false when this url was already queued during this run and has not been queued
     *         again. Delivery is at-least-once, so the same task can arrive twice; the caller has
     *         to account for the copy it dropped, because a dispatched url that is never handled
     *         is a url the completion test waits for for ever.
     */
    boolean put(CrawlTask task) throws Exception;

    /**
     * Takes the next url to fetch, or null when nothing is waiting.
     */
    CrawlTask poll() throws Exception;

    /**
     * Marks a task finished, which is what removes it from the frontier. A task that was taken but
     * never completed stays behind and is handed out again on the next run.
     */
    void complete(CrawlTask task) throws Exception;

    /**
     * How many urls are still outstanding: never fetched, plus taken but not completed.
     */
    long remaining() throws Exception;

    /**
     * Number of tasks recovered from a previous run when this frontier opened.
     */
    long recoveredCount();

    /** Discards the frontier and its files. */
    default void clean() throws Exception {}

}
