/*
 * Copyright 2017-2025 Fred Feng (paganini.fy@gmail.com)
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

package com.github.greenfinger.searcher;

import com.github.doodler.common.page.PageReader;
import com.github.doodler.common.page.PageResponse;
import com.github.greenfinger.CatalogDetails;
import com.github.greenfinger.WebCrawlerException;
import com.github.greenfinger.model.Resource;

/**
 * 
 * @Description: ResourceIndexManager
 * @Author: Fred Feng
 * @Date: 30/01/2025
 * @Version 1.0.0
 */
public interface ResourceIndexManager {

    void createIndex(String indexName);

    void deleteIndex(String indexName);

    void deleteResource(long catalogId, int version);

    void saveResource(IndexedResource indexedResource);

    void deleteResource(IndexedResource indexedResource);

    long indexCount();

    void upgradeCatalogIndex() throws WebCrawlerException;

    void recreateCatalogIndex() throws WebCrawlerException;

    void recreateCatalogIndex(long catalogId) throws WebCrawlerException;

    void upgradeCatalogIndex(long catalogId) throws WebCrawlerException;

    void createCatalogIndex(long catalogId) throws WebCrawlerException;

    void indexResource(CatalogDetails catalogDetails, Resource resource, int version);

    PageResponse<SearchResult> search(String cat, String keyword, Integer version, int page,
            int size);

    PageReader<SearchResult> search(String cat, String keyword, Integer version);

}
