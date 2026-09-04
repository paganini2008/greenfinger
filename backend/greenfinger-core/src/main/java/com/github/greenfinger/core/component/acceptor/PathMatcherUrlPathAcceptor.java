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

import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawlTask;
import com.github.greenfinger.core.utils.UrlPathPatterns;

/**
 * Keeps a crawl inside the patterns the catalog declares. Exclusions are tested first, so an
 * excluded pattern always beats an included one.
 *
 * <p>
 * Patterns are expanded by {@link UrlPathPatterns} before matching, which is what lets a user write
 * {@code **.google.com} instead of {@code **://**.google.com/**}.
 * 
 * @Description: PathMatcherUrlPathAcceptor
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class PathMatcherUrlPathAcceptor implements UrlPathAcceptor {

    private final PathMatcher pathMatcher = new AntPathMatcher();
    private final List<String> includedPatterns;
    private final List<String> excludedPatterns;

    public PathMatcherUrlPathAcceptor(CatalogDetails catalogDetails) {
        this.includedPatterns = UrlPathPatterns.expandAll(catalogDetails.getPathPatterns());
        this.excludedPatterns =
                UrlPathPatterns.expandAll(catalogDetails.getExcludedPathPatterns());
    }

    @Override
    public boolean accept(CatalogDetails catalogDetails, String referUrl, String url,
            CrawlTask task) {
        for (String pattern : excludedPatterns) {
            if (pathMatcher.match(pattern, url)) {
                return false;
            }
        }
        if (CollectionUtils.isEmpty(includedPatterns)) {
            return url.startsWith(referUrl);
        }
        for (String pattern : includedPatterns) {
            if (pathMatcher.match(pattern, url)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return 1;
    }

}
