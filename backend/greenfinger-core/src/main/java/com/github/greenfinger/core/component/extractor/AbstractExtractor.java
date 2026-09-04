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

package com.github.greenfinger.core.component.extractor;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawlTask;

/**
 * 
 * @Description: AbstractExtractor
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public abstract class AbstractExtractor implements Extractor {

    protected Map<String, String> defaultHttpHeaders = new HashMap<>(Map.of("Accept", "*/*",
            "Accept-Language", "en-US,en;q=0.9"));

    private List<ResponseBodyRewriter> responseBodyRewriters = new ArrayList<>();

    public Map<String, String> getDefaultHttpHeaders() {
        return defaultHttpHeaders;
    }

    public void setDefaultHttpHeaders(Map<String, String> defaultHttpHeaders) {
        this.defaultHttpHeaders = defaultHttpHeaders;
    }

    public void setResponseBodyRewriters(List<ResponseBodyRewriter> responseBodyRewriters) {
        this.responseBodyRewriters = responseBodyRewriters;
    }

    @Override
    public String extractHtml(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, CrawlTask task) throws Exception {
        String content = requestUrl(catalogDetails, referUrl, url, pageEncoding, task);
        return rewriteContent(catalogDetails, referUrl, url, pageEncoding, content);
    }

    protected abstract String requestUrl(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, CrawlTask task) throws Exception;

    protected String rewriteContent(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, String content) {
        if (StringUtils.isBlank(content)) {
            return "";
        }
        if (CollectionUtils.isEmpty(responseBodyRewriters)) {
            return content;
        }
        for (ResponseBodyRewriter bodyRewriter : responseBodyRewriters) {
            content = bodyRewriter.rewrite(catalogDetails, referUrl, url, pageEncoding, content);
        }
        return content;
    }

}
