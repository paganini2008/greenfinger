package com.github.greenfinger.components;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.PooledObject;
import org.springframework.http.HttpStatus;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.CookieManager;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.utils.MapUtils;
import com.github.doodler.common.utils.RandomIpUtils;
import com.github.doodler.common.utils.RandomUtils;
import com.github.doodler.common.utils.ThreadUtils;
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
public class HtmlUnitPooledExtractor extends PooledExtractor<WebClient> implements NamedExetractor {

    private final WebCrawlerExtractorProperties extractorProperties;

    @Override
    public WebClient createObject() throws Exception {
        WebCrawlerExtractorProperties.HtmlUnit config = extractorProperties.getHtmlunit();
        WebClient webClient;
        if (StringUtils.isNotBlank(config.getProxyHost()) && config.getProxyPort() > 0) {
            webClient = new WebClient(BrowserVersion.BEST_SUPPORTED, config.getProxyHost(),
                    config.getProxyPort());
        } else {
            webClient = new WebClient(BrowserVersion.BEST_SUPPORTED);
        }
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

        setDefaultHttpHeaders(webClient);
        return webClient;
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
    public void destroyObject(PooledObject<WebClient> object) throws Exception {
        object.getObject().close();
    }

    @Override
    protected String requestUrl(String refer, String url, Charset pageEncoding, Packet packet)
            throws Exception {
        WebClient webClient = objectPool.borrowObject();
        try {
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
        } finally {
            if (webClient != null) {
                objectPool.returnObject(webClient);
            }
        }
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
        System.out.println(pageSource.extractHtml("https://www.tuniu.com",
                "https://goldenmatrix.com/", StandardCharsets.UTF_8, null));
        System.in.read();
        pageSource.destroy();
    }



}
