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

import org.apache.commons.lang3.StringUtils;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawlTask;

/**
 * Keeps a crawl under its starting path.
 *
 * <p>
 * {@code start_url} is both the seed and a prefix: with {@code https://example.com/a}, the pages
 * {@code /a/b} and {@code /a/c} are in scope and {@code /x} is not. When it is left unset it falls
 * back to the catalog's url, which makes the whole site the scope.
 *
 * <p>
 * Like {@link DomainScopeUrlPathAcceptor} this is always applied and is not configurable; the path
 * patterns narrow what it lets through, they do not widen it.
 * 
 * @Description: StartUrlPrefixUrlPathAcceptor
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public class StartUrlPrefixUrlPathAcceptor implements UrlPathAcceptor {

    @Override
    public String getName() {
        return "startUrlPrefix";
    }

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE + 1;
    }

    @Override
    public boolean accept(CatalogDetails catalogDetails, String referUrl, String url,
            CrawlTask task) {
        String prefix = StringUtils.isNotBlank(catalogDetails.getStartUrl())
                ? catalogDetails.getStartUrl()
                : catalogDetails.getUrl();
        if (StringUtils.isBlank(prefix)) {
            return true;
        }
        return startsWith(url, normalize(prefix));
    }

    /**
     * Compared without the scheme, so a site that redirects http to https, or links to itself
     * through the other scheme, does not fall out of its own scope.
     */
    private String normalize(String url) {
        String value = StringUtils.removeStart(StringUtils.removeStart(url.trim(), "https://"),
                "http://");
        return StringUtils.removeEnd(value, "/");
    }

    private boolean startsWith(String url, String prefix) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        String value = normalize(url);
        if (!StringUtils.startsWithIgnoreCase(value, prefix)) {
            return false;
        }
        // "/about" must not be admitted by a prefix of "/ab": the next character has to be a
        // boundary, not the middle of a segment
        if (value.length() == prefix.length()) {
            return true;
        }
        char next = value.charAt(prefix.length());
        return next == '/' || next == '?' || next == '#';
    }

}
