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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.htmlunit.BrowserVersion;
import org.htmlunit.Page;
import org.htmlunit.ProxyConfig;
import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlPage;
import org.springframework.http.HttpStatusCode;
import com.github.greenfinger.core.WebCrawlerConstants;
import com.github.greenfinger.core.WebCrawlerExtractorProperties;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawlTask;
import com.github.greenfinger.core.utils.ThreadUtils;

/**
 * A lightweight headless browser: runs the page's scripts without the cost of a real browser
 * process. The middle ground between a plain http fetch and driving Chromium.
 *
 * <p>
 * Note the package move -- HtmlUnit lived under {@code com.gargoylesoftware} in the 2.x releases
 * 1.x depended on, and is {@code org.htmlunit} from 3.0 onward.
 * 
 * @Description: HtmlUnitPooledExtractor
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class HtmlUnitPooledExtractor extends PooledExtractor<WebClient> {

    private final WebCrawlerExtractorProperties extractorProperties;

    public HtmlUnitPooledExtractor(WebCrawlerExtractorProperties extractorProperties) {
        this.extractorProperties = extractorProperties;
        applyPoolConfig(extractorProperties);
    }

    private void applyPoolConfig(WebCrawlerExtractorProperties properties) {
        WebCrawlerExtractorProperties.ObjectPool config = properties.getObjectPool();
        objectPoolConfig.setMinIdle(config.getMinIdle());
        objectPoolConfig.setMaxIdle(config.getMaxIdle());
        objectPoolConfig.setMaxTotal(config.getMaxTotal());
    }

    @Override
    public String getName() {
        return WebCrawlerConstants.EXTRACTOR_HTMLUNIT;
    }

    @Override
    protected BasePooledObjectFactory<WebClient> createObjectFactory() {
        return new WebClientFactory(extractorProperties.getHtmlunit());
    }

    @Override
    protected String requestUrl(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, CrawlTask task) throws Exception {
        WebCrawlerExtractorProperties.HtmlUnit config = extractorProperties.getHtmlunit();
        WebClient webClient = objectPool.borrowObject();
        try {
            Page page = webClient.getPage(url);
            // Same reason as the other engines: a browser renders a 404 as readily as a page, and
            // stored, that error page becomes a row nothing marks as one. HtmlUnit follows
            // redirects itself, so this is the status the chain ended on.
            int status = page.getWebResponse().getStatusCode();
            if (!HttpStatusCode.valueOf(status).is2xxSuccessful()) {
                throw new ExtractorException(url, HttpStatusCode.valueOf(status));
            }
            if (config.isJavaScriptEnabled()) {
                webClient.waitForBackgroundJavaScript(config.getJavaScriptTimeout());
            }
            if (config.getLoadingTimeout() > 0) {
                ThreadUtils.sleep(config.getLoadingTimeout());
            }
            if (page instanceof HtmlPage) {
                return ((HtmlPage) page).asXml();
            }
            return page.getWebResponse().getContentAsString(pageEncoding);
        } finally {
            objectPool.returnObject(webClient);
        }
    }

    /**
     * 
     * @Description: WebClientFactory
     * @Author: Fred Feng
     * @Date: 29/08/2026
     * @Version 2.0.0
     */
    static class WebClientFactory extends BasePooledObjectFactory<WebClient> {

        private final WebCrawlerExtractorProperties.HtmlUnit config;

        WebClientFactory(WebCrawlerExtractorProperties.HtmlUnit config) {
            this.config = config;
        }

        @Override
        public WebClient create() {
            WebClient webClient = new WebClient(BrowserVersion.CHROME);
            if (StringUtils.isNotBlank(config.getProxyHost()) && config.getProxyPort() > 0) {
                webClient.getOptions().setProxyConfig(
                        new ProxyConfig(config.getProxyHost(), config.getProxyPort(), null));
            }
            webClient.getOptions().setJavaScriptEnabled(config.isJavaScriptEnabled());
            webClient.getOptions().setCssEnabled(config.isCssEnabled());
            webClient.getOptions().setTimeout(config.getTimeout());
            // a crawler cares about the html it got, not about the page's own script errors
            webClient.getOptions().setThrowExceptionOnScriptError(false);
            webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
            webClient.getOptions().setPrintContentOnFailingStatusCode(false);
            webClient.setCssErrorHandler(new org.htmlunit.SilentCssErrorHandler());
            return webClient;
        }

        @Override
        public PooledObject<WebClient> wrap(WebClient webClient) {
            return new DefaultPooledObject<>(webClient);
        }

        @Override
        public void destroyObject(PooledObject<WebClient> pooledObject) {
            pooledObject.getObject().close();
        }

    }

}
