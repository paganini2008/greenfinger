package com.github.greenfinger.utils;

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
import com.github.greenfinger.WebCrawlerConstants;
import com.github.greenfinger.WebCrawlerProperties;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @Description: HtmlUnitExtractor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@RequiredArgsConstructor
public class HtmlUnitExtractor extends PooledExtractor<WebClient> implements Extractor {

    private Map<String, String> defaultHeaders = new HashMap<>() {

        private static final long serialVersionUID = 1L;

        {
            put("Accept", "*/*");
            put("X-Forwarded-For", RandomIpUtils.randomIp());
            put("User-Agent", RandomUtils.randomChoice(WebCrawlerConstants.userAgents));
        }
    };

    private final WebCrawlerProperties webCrawlerProperties;

    public void setDefaultHeaders(Map<String, String> defaultHeaders) {
        this.defaultHeaders = defaultHeaders;
    }

    public Map<String, String> getDefaultHeaders() {
        return defaultHeaders;
    }

    @Override
    public WebClient createObject() throws Exception {
        WebCrawlerProperties.HtmlUnitPageSource config = webCrawlerProperties.getHtmlunit();
        WebClient webClient;
        if (StringUtils.isNotBlank(config.getProxyHost()) && config.getProxyPort() > 0) {
            webClient = new WebClient(BrowserVersion.BEST_SUPPORTED, config.getProxyHost(),
                    config.getProxyPort());
        } else {
            webClient = new WebClient(BrowserVersion.BEST_SUPPORTED);
        }
        Map<String, String> defaultHeaders = getDefaultHeaders();
        if (MapUtils.isNotEmpty(defaultHeaders)) {
            for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
                webClient.addRequestHeader(entry.getKey(), entry.getValue());
            }
        }
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        webClient.getOptions().setActiveXNative(false);
        webClient.getOptions().setCssEnabled(false);
        webClient.getOptions().setJavaScriptEnabled(false);
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
    public void destroyObject(PooledObject<WebClient> object) throws Exception {
        object.getObject().close();
    }

    @Override
    public String extractHtml(String refer, String url, Charset pageEncoding, Packet packet)
            throws Exception {
        WebClient webClient = objectPool.borrowObject();
        try {
            Page page = webClient.getPage(url);
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

    public static void main(String[] args) throws Exception {
        HtmlUnitExtractor pageSource = new HtmlUnitExtractor(new WebCrawlerProperties());
        pageSource.afterPropertiesSet();
        // System.out.println(pageSource.getHtml("https://blog.csdn.net/u010814849/article/details/52526705"));
        System.out.println(pageSource.extractHtml("https://www.tuniu.com",
                "https://www.delish.com/cooking/menus/g63274018/best-recipes-for-in-between-christmas-nye/",
                StandardCharsets.UTF_8, null));
        System.in.read();
        pageSource.destroy();
    }

}
