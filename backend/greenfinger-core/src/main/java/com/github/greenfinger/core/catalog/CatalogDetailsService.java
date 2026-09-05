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

package com.github.greenfinger.core.catalog;

import com.github.greenfinger.core.WebCrawlerException;

/**
 * The only way to obtain a {@link CatalogDetails}.
 *
 * <p>
 * Everything that runs a crawl goes through here, so a crawl can never be launched from parameters
 * that were never persisted. The default implementation reads the database; the interface exists
 * so a distributed deployment can serve the same view from elsewhere without the engine noticing.
 * 
 * @Description: CatalogDetailsService
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public interface CatalogDetailsService {

    CatalogDetails loadCatalogDetails(String id) throws CatalogDetailsNotFoundException;

    CatalogDetails loadCatalogDetailsByName(String name) throws CatalogDetailsNotFoundException;

    /**
     * The catalog currently crawling, if any. Only one runs at a time.
     */
    CatalogDetails loadRunningCatalogDetails() throws WebCrawlerException;

}
