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

package com.github.greenfinger.cluster.support;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsImpl;
import com.github.greenfinger.core.component.acceptor.UrlPathAcceptor;
import com.github.greenfinger.core.component.dedup.ContentDedupFilter;
import com.github.greenfinger.core.component.dedup.ExistingUrlPathFilter;
import com.github.greenfinger.core.component.extractor.Extractor;
import com.github.greenfinger.core.component.completion.CompletionChecker;
import com.github.greenfinger.core.component.state.GlobalStateManager;
import com.github.greenfinger.core.engine.CrawlFrontier;
import com.github.greenfinger.core.engine.CrawlTask;
import com.github.greenfinger.core.engine.WebCrawlerExecutionContext;
import com.github.greenfinger.core.model.Catalog;

/**
 * A crawl that exists only as far as the cluster can see it: a catalog, a frontier, the two dedup
 * filters and the counters.
 *
 * <p>
 * Everything a real run also has -- extractors, acceptors, output channels -- is left out, because
 * nothing in this module touches any of it. A url arriving from a peer reaches a frontier and
 * stops there; what fetches it afterwards is the engine's business and the engine has its own
 * tests.
 * 
 * @Description: TestRun
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
public class TestRun implements WebCrawlerExecutionContext {

    private final CatalogDetails catalogDetails;
    private final MemoryFrontier frontier = new MemoryFrontier();
    private final MemoryUrls urls = new MemoryUrls();
    private final MemoryContents contents = new MemoryContents();
    private final AtomicBoolean completed = new AtomicBoolean();

    /**
     * A local one by default. Most of these tests are about where a message went rather than
     * about the counters, and a run without a state manager is not a shape the engine ever
     * produces -- so leaving it null would fail for a reason that has nothing to teach.
     */
    private GlobalStateManager stateManager;
    private ExistingUrlPathFilter urlFilter = urls;
    private ContentDedupFilter contentFilter = contents;

    public TestRun(String catalogId, String name) {
        Catalog catalog = new Catalog();
        catalog.setId(catalogId);
        catalog.setName(name);
        catalog.setUrl("https://example.com");
        catalog.setIndexVersion(0);
        this.catalogDetails = new CatalogDetailsImpl(catalog, new WebCrawlerProperties());
        this.stateManager =
                new com.github.greenfinger.core.component.state.DefaultGlobalStateManager(
                        catalogDetails);
    }

    public TestRun withStateManager(GlobalStateManager stateManager) {
        this.stateManager = stateManager;
        return this;
    }

    public TestRun withFilters(ExistingUrlPathFilter urlFilter, ContentDedupFilter contentFilter) {
        this.urlFilter = urlFilter;
        this.contentFilter = contentFilter;
        return this;
    }

    public MemoryFrontier frontier() {
        return frontier;
    }

    public MemoryUrls plainUrls() {
        return urls;
    }

    public MemoryContents plainContents() {
        return contents;
    }

    @Override
    public CatalogDetails getCatalogDetails() {
        return catalogDetails;
    }

    @Override
    public CrawlFrontier getCrawlFrontier() {
        return frontier;
    }

    @Override
    public ExistingUrlPathFilter getExistingUrlPathFilter() {
        return urlFilter;
    }

    @Override
    public ContentDedupFilter getContentDedupFilter() {
        return contentFilter;
    }

    @Override
    public GlobalStateManager getGlobalStateManager() {
        return stateManager;
    }

    @Override
    public List<CompletionChecker> getCompletionCheckers() {
        return List.of();
    }

    @Override
    public List<UrlPathAcceptor> getUrlPathAcceptors() {
        return List.of();
    }

    @Override
    public Extractor getExtractor() {
        return null;
    }

    @Override
    public boolean isUrlAcceptable(String referUrl, String url, CrawlTask task) {
        return true;
    }

    @Override
    public boolean isCompleted() {
        return completed.get();
    }

    @Override
    public boolean checkCompletion() {
        return completed.get();
    }

    @Override
    public String getCompletionReason() {
        return null;
    }

    @Override
    public boolean isInterrupted() {
        return false;
    }

    /**
     * In memory rather than RocksDB: what these tests assert is which node a url reached, and a
     * temporary directory per test would say the same thing more slowly.
     * 
     * @Description: MemoryFrontier
     * @Author: Fred Feng
     * @Date: 02/09/2026
     * @Version 2.0.0
     */
    public static class MemoryFrontier implements CrawlFrontier {

        private final Queue<CrawlTask> queue = new ConcurrentLinkedQueue<>();
        private final List<String> accepted = new ArrayList<>();

        @Override
        public String getName() {
            return "memory";
        }

        /**
         * Keeps everything it is given, duplicates included: what a test wants to see is what
         * arrived, not what a real frontier would have filtered out of it.
         */
        @Override
        public boolean put(CrawlTask task) {
            queue.add(task);
            synchronized (accepted) {
                accepted.add(task.getUrl());
            }
            return true;
        }

        @Override
        public CrawlTask poll() {
            return queue.poll();
        }

        @Override
        public void complete(CrawlTask task) {
            // nothing is taken back out in these tests
        }

        @Override
        public long remaining() {
            return queue.size();
        }

        @Override
        public long recoveredCount() {
            return 0;
        }

        public List<String> accepted() {
            synchronized (accepted) {
                return List.copyOf(accepted);
            }
        }
    }

    /**
     * 
     * @Description: MemoryUrls
     * @Author: Fred Feng
     * @Date: 02/09/2026
     * @Version 2.0.0
     */
    public static class MemoryUrls implements ExistingUrlPathFilter {

        private final Set<String> seen = new HashSet<>();

        @Override
        public String getName() {
            return "memory";
        }

        @Override
        public synchronized boolean mightExist(String path) {
            return !seen.add(path);
        }

        public synchronized boolean knows(String path) {
            return seen.contains(path);
        }
    }

    /**
     * 
     * @Description: MemoryContents
     * @Author: Fred Feng
     * @Date: 02/09/2026
     * @Version 2.0.0
     */
    public static class MemoryContents implements ContentDedupFilter {

        private final Set<String> seen = new HashSet<>();

        @Override
        public String getName() {
            return "memory";
        }

        @Override
        public synchronized boolean isDuplicate(String text) {
            return !seen.add(fingerprint(text));
        }

        @Override
        public String fingerprint(String text) {
            return "fp-" + text.hashCode();
        }

        public synchronized boolean knows(String text) {
            return seen.contains(fingerprint(text));
        }
    }

}
