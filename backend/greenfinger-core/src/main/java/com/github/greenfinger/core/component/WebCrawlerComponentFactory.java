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

package com.github.greenfinger.core.component;

import java.util.List;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.acceptor.UrlPathAcceptor;
import com.github.greenfinger.core.component.dedup.ContentDedupFilter;
import com.github.greenfinger.core.component.dedup.ExistingUrlPathFilter;
import com.github.greenfinger.core.component.extractor.Extractor;
import com.github.greenfinger.core.component.completion.CompletionChecker;
import com.github.greenfinger.core.component.state.GlobalStateManager;
import com.github.greenfinger.core.engine.CrawlFrontier;

/**
 * Builds the pluggable components for one crawl. Replacing this bean is how an application swaps in
 * its own implementations without touching the engine.
 * 
 * @Description: WebCrawlerComponentFactory
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public interface WebCrawlerComponentFactory {

    List<CompletionChecker> getCompletionCheckers(CatalogDetails catalogDetails);

    List<UrlPathAcceptor> getUrlPathAcceptors(CatalogDetails catalogDetails);

    Extractor getExtractor(CatalogDetails catalogDetails);

    ExistingUrlPathFilter getExistingUrlPathFilter(CatalogDetails catalogDetails);

    ContentDedupFilter getContentDedupFilter(CatalogDetails catalogDetails);

    /**
     * @param initiator whether this node started the run. Only that one clears what a previous
     *        run left in the shared counters; a node joining a crawl already under way must not
     *        zero the numbers it is about to add to.
     */
    GlobalStateManager getGlobalStateManager(CatalogDetails catalogDetails, boolean initiator);

    CrawlFrontier getCrawlFrontier(CatalogDetails catalogDetails);

}
