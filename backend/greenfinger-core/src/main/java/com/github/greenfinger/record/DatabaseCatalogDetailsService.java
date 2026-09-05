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

package com.github.greenfinger.record;

import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsImpl;
import com.github.greenfinger.core.catalog.CatalogDetailsNotFoundException;
import com.github.greenfinger.core.catalog.CatalogDetailsService;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.model.Catalog;
import lombok.RequiredArgsConstructor;

/**
 * The default {@link CatalogDetailsService}: reads the definition from the database and applies
 * the configured defaults.
 * 
 * @Description: DatabaseCatalogDetailsService
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@RequiredArgsConstructor
public class DatabaseCatalogDetailsService implements CatalogDetailsService {

    private final CatalogStore catalogStore;
    private final WebCrawlerProperties webCrawlerProperties;

    @Override
    public CatalogDetails loadCatalogDetails(String id) {
        return wrap(catalogStore.findById(id).orElseThrow(
                () -> new CatalogDetailsNotFoundException("No catalog with id: " + id)));
    }

    @Override
    public CatalogDetails loadCatalogDetailsByName(String name) {
        return wrap(catalogStore.findByName(name).orElseThrow(
                () -> new CatalogDetailsNotFoundException("No catalog named: " + name)));
    }

    @Override
    public CatalogDetails loadRunningCatalogDetails() {
        return catalogStore.findRunning().stream().findFirst().map(this::wrap).orElse(null);
    }

    private CatalogDetails wrap(Catalog catalog) {
        return new CatalogDetailsImpl(catalog, webCrawlerProperties);
    }

}
