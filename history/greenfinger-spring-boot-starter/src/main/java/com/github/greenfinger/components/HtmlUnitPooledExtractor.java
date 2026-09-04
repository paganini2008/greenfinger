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

package com.github.greenfinger.components;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.springframework.http.HttpStatus;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.CookieManager;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.github.doodler.common.context.ManagedBeanLifeCycle;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.utils.MapUtils;
import com.github.doodler.common.utils.RandomIpUtils;
import com.github.doodler.common.utils.RandomUtils;
import com.github.doodler.common.utils.ThreadUtils;
import com.github.greenfinger.CatalogDetails;
import com.github.greenfinger.WebCrawlerConstants;
import com.github.greenfinger.WebCrawlerExtractorProperties;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @Description: HtmlUnitPooledExtractor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@RequiredArgsConstructor
public class HtmlUnitPooledExtractor extends PooledExtractor<WebClient>
        implements NamedExetractor, ManagedBeanLifeCycle {

    private final WebCrawlerExtractorProperties extractorProperties;

    @Override
    protected BasePooledObjectFactory<WebClient> createObjectFactory() {

        return new BasePooledObjectFactory<WebClient>() {

            @Override
            public PooledObject<WebClient> wrap(WebClient object) {
                return new DefaultPooledObject<WebClient>(object);
            }

            @Override
            public WebClient create() throws Exception {
                WebCrawlerExtractorProperties.HtmlUnit config = extractorProperties.getHtmlunit();
                WebClient webClient;
                if (StringUtils.isNotBlank(config.getProxyHost()) && config.getProxyPort() > 0) {
                    webClient = new WebClient(BrowserVersion.BEST_SUPPORTED, config.getProxyHost(),
                            config.getProxyPort());
                } else {
                    webClient = new WebClient(BrowserVersion.BEST_SUPPORTED);
                }
                webClient.getOptions().setThrowExceptionOnScriptError(false);
                webClient.getOptions().setThrowExceptionOnFailingStatusCode(true);
                webClient.getOptions().setActiveXNative(false);
                webClient.getOptions().setCssEnabled(false);
                webClient.getOptions().setJavaScriptEnabled(config.isJavaScriptEnabled());
                webClient.getOptions().setRedirectEnabled(false);
                webClient.getOptions().setDownloadImages(false);
                webClient.getOptions().setUseInsecureSSL(true);
                webClient.getOptions().setTimeout(config.getTimeout());
                webClient.setCookieManager(new CookieManager());
                webClient.setAjaxController(new NicelyResynchronizingAjaxController());
                webClient.setJavaScriptTimeout(config.getJavaScriptTimeout());
                setDefaultHttpHeaders(webClient);
                return webClient;
            }

            @Override
            public void destroyObject(PooledObject<WebClient> po) throws Exception {
                po.getObject().close();
            }
        };
    }

    private void setDefaultHttpHeaders(WebClient webClient) {
        WebCrawlerExtractorProperties.HtmlUnit config = extractorProperties.getHtmlunit();
        Map<String, String> defaultHeaders = new HashMap<>(this.defaultHttpHeaders);
        defaultHeaders.put("X-Forwarded-For", RandomIpUtils.randomIp());
        defaultHeaders.put("User-Agent", RandomUtils.randomChoice(WebCrawlerConstants.userAgents));
        defaultHeaders.putAll(config.getDefaultHttpHeaders());
        if (MapUtils.isNotEmpty(defaultHeaders)) {
            for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
                webClient.addRequestHeader(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    protected String requestUrl(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, Packet packet) throws Exception {
        WebCrawlerExtractorProperties.ObjectPool poolConfig = extractorProperties.getObjectPool();
        WebClient webClient = null;
        try {
            webClient = poolConfig.getBorrowTimeout() > 0
                    ? objectPool.borrowObject(Duration.ofMillis(poolConfig.getBorrowTimeout()))
                    : objectPool.borrowObject();
            if (webClient == null) {
                throw new NoSuchElementException("No available WebClient now");
            }
            WebCrawlerExtractorProperties.HtmlUnit config = extractorProperties.getHtmlunit();
            Page page = webClient.getPage(url);
            if (config.getLoadingTimeout() > 0) {
                ThreadUtils.sleep(config.getLoadingTimeout());
            } else {
                ThreadUtils.randomSleep(1000L);
            }
            WebResponse webResponse = page.getWebResponse();
            int responseStatusCode = webResponse.getStatusCode();
            if (HttpStatus.valueOf(responseStatusCode).is2xxSuccessful()) {
                return pageEncoding != null ? webResponse.getContentAsString(pageEncoding)
                        : webResponse.getContentAsString();
            }
            throw new ExtractorException(url, HttpStatus.valueOf(responseStatusCode));
        } finally {
            if (webClient != null) {
                objectPool.returnObject(webClient);
            }
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        WebCrawlerExtractorProperties.ObjectPool poolConfig = extractorProperties.getObjectPool();
        getObjectPoolConfig().setMinIdle(poolConfig.getMinIdle());
        getObjectPoolConfig().setMaxIdle(poolConfig.getMaxIdle());
        getObjectPoolConfig().setMaxTotal(poolConfig.getMaxTotal());
        super.afterPropertiesSet();
    }

    @Override
    public String getName() {
        return WebCrawlerConstants.EXTRACTOR_HTMLUNIT;
    }

    public static void main(String[] args) throws Exception {
        HtmlUnitPooledExtractor pageSource =
                new HtmlUnitPooledExtractor(new WebCrawlerExtractorProperties());
        pageSource.afterPropertiesSet();
        // System.out.println(pageSource.getHtml("https://blog.csdn.net/u010814849/article/details/52526705"));
        System.out.println(pageSource.extractHtml(null, "https://www.tuniu.com",
                "https://greatist.com/", StandardCharsets.UTF_8, null));
        System.in.read();
        pageSource.destroy();
    }



}
