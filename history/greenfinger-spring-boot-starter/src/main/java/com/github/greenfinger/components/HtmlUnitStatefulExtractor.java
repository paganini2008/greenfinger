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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.CookieManager;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.util.Cookie;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.utils.Coookie;
import com.github.doodler.common.utils.MapUtils;
import com.github.doodler.common.utils.RandomIpUtils;
import com.github.doodler.common.utils.RandomUtils;
import com.github.doodler.common.utils.ThreadUtils;
import com.github.greenfinger.CatalogDetails;
import com.github.greenfinger.WebCrawlerConstants;
import com.github.greenfinger.WebCrawlerExtractorProperties;

/**
 * 
 * @Description: HtmlUnitPooledExtractor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public class HtmlUnitStatefulExtractor extends StatefulExtractor<WebClient>
        implements DisposableBean {

    private final WebCrawlerExtractorProperties extractorProperties;

    public HtmlUnitStatefulExtractor(WebCrawlerExtractorProperties extractorProperties) {
        this(new DoNothingExtractorCredentialHandler<>(), extractorProperties);
    }

    public HtmlUnitStatefulExtractor(ExtractorCredentialHandler<WebClient> credentialHandler,
            WebCrawlerExtractorProperties extractorProperties) {
        super(credentialHandler);
        this.extractorProperties = extractorProperties;
    }

    private WebClient webClient;

    @Override
    public WebClient createNew() {
        WebCrawlerExtractorProperties.HtmlUnit config = extractorProperties.getHtmlunit();
        WebClient webClient;
        if (StringUtils.isNotBlank(config.getProxyHost()) && config.getProxyPort() > 0) {
            webClient = new WebClient(BrowserVersion.BEST_SUPPORTED, config.getProxyHost(),
                    config.getProxyPort());
        } else {
            webClient = new WebClient(BrowserVersion.BEST_SUPPORTED);
        }
        setDefaultHttpHeaders(webClient);
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
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
        return webClient;
    }

    @Override
    public void setExtraCookies(List<Coookie> coookies) {
        Assert.notNull(coookies, "Cookie list must not be null");
        coookies.forEach(co -> {
            webClient.getCookieManager().addCookie(new Cookie(co.getDomain(), co.getName(),
                    co.getValue(), co.getPath(), co.getExpires(), co.isSecure(), co.isHttpOnly()));
        });
    }

    private void setDefaultHttpHeaders(WebClient webClient) {
        WebCrawlerExtractorProperties.HtmlUnit config = extractorProperties.getHtmlunit();
        Map<String, String> defaultHeaders = new HashMap<>(this.defaultHttpHeaders);
        defaultHeaders.putAll(config.getDefaultHttpHeaders());
        defaultHeaders.put("X-Forwarded-For", RandomIpUtils.randomIp());
        defaultHeaders.put("User-Agent", RandomUtils.randomChoice(WebCrawlerConstants.userAgents));
        if (MapUtils.isNotEmpty(defaultHeaders)) {
            for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
                webClient.addRequestHeader(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public String test(String url, Charset pageEncoding) throws Exception {
        WebClient webClient = null;
        try {
            webClient = createNew();
            return doRequestUrl(webClient, url, url, pageEncoding, new Packet());
        } finally {
            if (webClient != null) {
                webClient.close();
            }
        }
    }

    @Override
    public WebClient get() {
        if (webClient == null) {
            webClient = createNew();
        }
        return webClient;
    }

    @Override
    protected synchronized String requestUrl(CatalogDetails catalogDetails, String referUrl,
            String url, Charset pageEncoding, Packet packet) throws Exception {
        WebClient webClient = get();
        return doRequestUrl(webClient, referUrl, url, pageEncoding, packet);
    }

    private String doRequestUrl(WebClient webClient, String referUrl, String url,
            Charset pageEncoding, Packet packet) throws Exception {
        WebCrawlerExtractorProperties.HtmlUnit config = extractorProperties.getHtmlunit();
        Page page = webClient.getPage(url);
        if (config.isJavaScriptEnabled()) {
            webClient.waitForBackgroundJavaScript(config.getJavaScriptTimeout());
        }
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
    }

    @Override
    public String getName() {
        return WebCrawlerConstants.EXTRACTOR_HTMLUNIT;
    }

    @Override
    public void destroy() throws Exception {
        if (webClient != null) {
            webClient.close();
        }
    }

    public static void main(String[] args) throws Exception {
        HtmlUnitStatefulExtractor pageSource =
                new HtmlUnitStatefulExtractor(new WebCrawlerExtractorProperties());
        // System.out.println(pageSource.getHtml("https://blog.csdn.net/u010814849/article/details/52526705"));
        System.out.println(pageSource.test("https://goldenmatrix.com/", StandardCharsets.UTF_8));
        System.in.read();
        pageSource.destroy();
    }



}
