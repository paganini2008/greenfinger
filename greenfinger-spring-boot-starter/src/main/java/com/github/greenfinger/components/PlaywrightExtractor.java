package com.github.greenfinger.components;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.PooledObject;
import com.github.doodler.common.context.ManagedBeanLifeCycle;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.utils.RandomIpUtils;
import com.github.doodler.common.utils.RandomUtils;
import com.github.doodler.common.utils.ThreadUtils;
import com.github.greenfinger.WebCrawlerConstants;
import com.github.greenfinger.WebCrawlerExtractorProperties;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.Proxy;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @Description: PlaywrightExtractor
 * @Author: Fred Feng
 * @Date: 10/01/2025
 * @Version 1.0.0
 */
@RequiredArgsConstructor
public class PlaywrightExtractor extends PooledExtractor<Browser> implements ManagedBeanLifeCycle {

    private Map<String, String> defaultHeaders = new HashMap<>() {

        private static final long serialVersionUID = 1L;
        {
            put("Accept", "*/*");
            put("Accept-Language", "en-US,en;q=0.9");
        }
    };

    private final WebCrawlerExtractorProperties webCrawlerProperties;

    public void setDefaultHeaders(Map<String, String> defaultHeaders) {
        this.defaultHeaders = defaultHeaders;
    }

    private Playwright playwright;

    @Override
    public void afterPropertiesSet() throws Exception {
        playwright = Playwright.create();
        super.afterPropertiesSet();
    }

    @Override
    public Browser createObject() throws Exception {
        Browser browser =
                playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        return browser;
    }

    private Map<String, String> getDefaultHttpHeaders() {
        WebCrawlerExtractorProperties.Playwright config = webCrawlerProperties.getPlaywright();
        Map<String, String> defaultHeaders = new HashMap<>(this.defaultHeaders);
        defaultHeaders.putAll(config.getDefaultHeaders());
        defaultHeaders.put("X-Forwarded-For", RandomIpUtils.randomIp());
        defaultHeaders.put("User-Agent", RandomUtils.randomChoice(WebCrawlerConstants.userAgents));
        return defaultHeaders;
    }

    @Override
    public String requestUrl(String referUrl, String url, Charset pageEncoding, Packet packet)
            throws Exception {
        Browser browser = objectPool.borrowObject();
        WebCrawlerExtractorProperties.Playwright config = webCrawlerProperties.getPlaywright();
        Browser.NewContextOptions newContextOptions =
                new Browser.NewContextOptions().setIsMobile(false).setAcceptDownloads(false)
                        .setJavaScriptEnabled(config.isJavaScriptEnabled())
                        .setExtraHTTPHeaders(getDefaultHttpHeaders());
        if (StringUtils.isNotBlank(config.getProxyServer())) {
            newContextOptions.setProxy(new Proxy(config.getProxyServer()));
        }
        BrowserContext context = browser.newContext(newContextOptions);
        Page page = context.newPage();
        page.navigate(url);
        page.setDefaultNavigationTimeout(config.getTimeout());
        page.setDefaultTimeout(config.getTimeout());
        page.waitForLoadState(LoadState.NETWORKIDLE);
        if (config.getLoadingTimeout() > 0) {
            ThreadUtils.sleep(config.getLoadingTimeout());
        } else {
            ThreadUtils.randomSleep(1000L);
        }
        return page.content();
    }

    @Override
    public void destroyObject(PooledObject<Browser> object) throws Exception {
        object.getObject().close();
    }

    @Override
    public void destroy() throws Exception {
        super.destroy();
        if (playwright != null) {
            playwright.close();
        }
    }

    @Override
    public String getName() {
        return WebCrawlerConstants.EXTRACTOR_PLAYWRIGHT;
    }

    public static void main(String[] args) throws Exception {
        PlaywrightExtractor extractor =
                new PlaywrightExtractor(new WebCrawlerExtractorProperties());
        extractor.afterPropertiesSet();
        String html = extractor.extractHtml("", "https://goldenmatrix.com/company",
                StandardCharsets.UTF_8, null);
        System.out.println(html);
        System.in.read();
        extractor.destroy();
    }



}
