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

package com.github.greenfinger.core;

import java.util.Set;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsImpl;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.ContentMode;
import com.github.greenfinger.core.model.OutputType;

/**
 * 
 * @Description: CatalogFixtures
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public abstract class CatalogFixtures {

    /** A fixed uuid, so ids derived from it are stable across runs and can be asserted on. */
    public static final String CATALOG_ID = "0192f0c8-1234-7000-8000-0000000000aa";

    public static Catalog catalog() {
        Catalog catalog = new Catalog();
        catalog.setId(CATALOG_ID);
        catalog.setName("example");
        catalog.setUrl("https://www.example.com");
        catalog.setStartUrl("https://www.example.com");
        catalog.setCat("test");
        catalog.setPathPattern("**.example.com");
        catalog.setMaxFetchSize(100);
        catalog.setDepth(2);
        catalog.setDuration(5L);
        catalog.setFetchInterval(0L);
        catalog.setCountingType(CountingType.SAVED_RESOURCE_COUNT);
        catalog.setOutputTypes(Set.of(OutputType.FILE));
        catalog.setContentMode(ContentMode.TEXT_IMAGE);
        catalog.setImageEnabled(true);
        catalog.setIndexVersion(0);
        catalog.setSearchVersion(-1);
        catalog.setMaxVersions(10);
        return catalog;
    }

    public static CatalogDetails details() {
        return details(catalog());
    }

    public static CatalogDetails details(Catalog catalog) {
        return new CatalogDetailsImpl(catalog, new WebCrawlerProperties());
    }

}
