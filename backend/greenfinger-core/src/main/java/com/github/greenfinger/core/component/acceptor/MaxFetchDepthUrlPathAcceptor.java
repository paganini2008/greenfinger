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

import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawlTask;

/**
 * Stops the crawl from descending past {@code maxFetchDepth} links from the seed.
 *
 * <p>
 * 1.x inferred depth by counting slashes in the url, which reported the wrong number for any site
 * with a flat routing scheme or query-string pagination. The task now carries the real link
 * distance, so this is an exact check.
 * 
 * @Description: MaxFetchDepthUrlPathAcceptor
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class MaxFetchDepthUrlPathAcceptor implements UrlPathAcceptor {

    @Override
    public boolean accept(CatalogDetails catalogDetails, String referUrl, String url,
            CrawlTask task) {
        int maxFetchDepth = catalogDetails.getMaxFetchDepth();
        if (maxFetchDepth < 0) {
            return true;
        }
        // the link being judged would live one level below the page it was found on
        return task.getDepth() + 1 <= maxFetchDepth;
    }

    @Override
    public int getOrder() {
        return 0;
    }

}
