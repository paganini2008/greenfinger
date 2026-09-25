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
import com.github.greenfinger.core.utils.UrlUtils;

/**
 * The boundary a crawl can never cross: a run started on www.a.com must not end up on www.b.com.
 *
 * <p>
 * Not configurable and not removable. Every other acceptor narrows what is already inside the
 * boundary; this one draws it, and runs first so nothing downstream has to hold the line -- one
 * advert or "powered by" footer is enough for an unbounded crawler to wander off for good.
 *
 * <p>
 * Sibling subdomains are inside it: the registrable domain is compared, so
 * {@code books.toscrape.com} and {@code quotes.toscrape.com} are one site. Narrowing further is
 * {@link StartUrlPrefixUrlPathAcceptor} and the path patterns.
 * 
 * @Description: DomainScopeUrlPathAcceptor
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public class DomainScopeUrlPathAcceptor implements UrlPathAcceptor {

    @Override
    public String getName() {
        return "domainScope";
    }

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }

    @Override
    public boolean accept(CatalogDetails catalogDetails, String referUrl, String url,
            CrawlTask task) {
        String boundary = StringUtils.isNotBlank(catalogDetails.getUrl()) ? catalogDetails.getUrl()
                : referUrl;
        if (StringUtils.isBlank(boundary) || StringUtils.isBlank(url)) {
            return false;
        }
        String expected = UrlUtils.getDomainName(boundary);
        String actual = UrlUtils.getDomainName(url);
        return StringUtils.isNotBlank(expected) && expected.equalsIgnoreCase(actual);
    }

}
