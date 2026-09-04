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
import com.github.greenfinger.core.ManagedBeanLifeCycle;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.acceptor.UrlPathAcceptor;
import com.github.greenfinger.core.component.dedup.ContentDedupFilter;
import com.github.greenfinger.core.component.dedup.ExistingUrlPathFilter;
import com.github.greenfinger.core.component.extractor.Extractor;
import com.github.greenfinger.core.component.completion.CompletionChecker;
import com.github.greenfinger.core.component.state.GlobalStateManager;

/**
 * The assembled set of components for one crawl, and their shared lifecycle.
 * 
 * @Description: WebCrawlerExecutionContext
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public interface WebCrawlerExecutionContext extends ManagedBeanLifeCycle {

    CatalogDetails getCatalogDetails();

    List<CompletionChecker> getCompletionCheckers();

    List<UrlPathAcceptor> getUrlPathAcceptors();

    Extractor getExtractor();

    ExistingUrlPathFilter getExistingUrlPathFilter();

    ContentDedupFilter getContentDedupFilter();

    GlobalStateManager getGlobalStateManager();

    CrawlFrontier getCrawlFrontier();

    boolean isUrlAcceptable(String referUrl, String url, CrawlTask task);

    boolean isCompleted();

    /**
     * Asks the checkers that are answered on the crawl's own thread, then reports whether the
     * crawl is over. The engine calls this as it goes past; the checkers answered by the clock
     * are asked on a schedule instead, and both write the same shared flag.
     */
    boolean checkCompletion();

    /** Why the crawl ended, in the words of whoever ended it. Null while it runs. */
    String getCompletionReason();

    /**
     * Whether the end was an intervention rather than a completion. An intervention leaves the
     * previous search version standing.
     */
    boolean isInterrupted();

}
