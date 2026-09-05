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

package com.github.greenfinger.core.component.acceptor;

import org.springframework.core.Ordered;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.WebCrawlerComponent;
import com.github.greenfinger.core.engine.CrawlTask;

/**
 * Decides whether a discovered link is followed. Acceptors run in {@link Ordered} sequence and the
 * first refusal wins, so the cheap checks are ordered ahead of the expensive ones.
 * 
 * @Description: UrlPathAcceptor
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public interface UrlPathAcceptor extends WebCrawlerComponent, Ordered {

    boolean accept(CatalogDetails catalogDetails, String referUrl, String url, CrawlTask task);

}
