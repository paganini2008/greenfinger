package com.github.greenfinger.components;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import com.github.doodler.common.context.ManagedBeanLifeCycle;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.utils.RandomIpUtils;
import com.github.doodler.common.utils.RandomUtils;
import com.github.doodler.common.utils.ThreadUtils;
import com.github.greenfinger.CatalogDetails;
import com.github.greenfinger.WebCrawlerConstants;
import com.github.greenfinger.WebCrawlerExtractorProperties;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Proxy;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @Description: PlaywrightPooledExtractor
 * @Author: Fred Feng
 * @Date: 10/01/2025
 * @Version 1.0.0
 */
@RequiredArgsConstructor
public class PlaywrightPooledExtractor extends PooledExtractor<BrowserContext>
        implements NamedExetractor, ManagedBeanLifeCycle {

    private final WebCrawlerExtractorProperties extractorProperties;

    private Playwright playwright;
    private final Lock lock = new ReentrantLock();

    @Override
    public void afterPropertiesSet() throws Exception {
        WebCrawlerExtractorProperties.ObjectPool poolConfig = extractorProperties.getObjectPool();
        getObjectPoolConfig().setMinIdle(poolConfig.getMinIdle());
        getObjectPoolConfig().setMaxIdle(poolConfig.getMaxIdle());
        getObjectPoolConfig().setMaxTotal(poolConfig.getMaxTotal());

        playwright = Playwright.create();
        super.afterPropertiesSet();
    }

    @Override
    protected BasePooledObjectFactory<BrowserContext> createObjectFactory() {
        final WebCrawlerExtractorProperties.Playwright config = extractorProperties.getPlaywright();
        return new BasePooledObjectFactory<BrowserContext>() {

            @Override
            public PooledObject<BrowserContext> wrap(BrowserContext object) {
                return new DefaultPooledObject<BrowserContext>(object);
            }

            @Override
            public BrowserContext create() throws Exception {
                Browser browser = playwright.chromium()
                        .launch(new BrowserType.LaunchOptions().setHeadless(true));
                Browser.NewContextOptions newContextOptions = new Browser.NewContextOptions()
                        .setIsMobile(false).setAcceptDownloads(false).setBypassCSP(true)
                        .setJavaScriptEnabled(config.isJavaScriptEnabled())
                        .setExtraHTTPHeaders(mergeHttpHeaders());
                if (StringUtils.isNotBlank(config.getProxyServer())) {
                    newContextOptions.setProxy(new Proxy(config.getProxyServer()));
                }
                return browser.newContext(newContextOptions);
            }

            @Override
            public void destroyObject(PooledObject<BrowserContext> po) throws Exception {
                po.getObject().close();
            }
        };
    }

    private Map<String, String> mergeHttpHeaders() {
        WebCrawlerExtractorProperties.Playwright config = extractorProperties.getPlaywright();
        Map<String, String> defaultHeaders = new HashMap<>(this.defaultHttpHeaders);
        defaultHeaders.put("X-Forwarded-For", RandomIpUtils.randomIp());
        defaultHeaders.put("User-Agent", RandomUtils.randomChoice(WebCrawlerConstants.userAgents));
        defaultHeaders.putAll(config.getDefaultHttpHeaders());
        return defaultHeaders;
    }

    @Override
    protected String requestUrl(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, Packet packet) throws Exception {
        WebCrawlerExtractorProperties.Playwright config = extractorProperties.getPlaywright();
        WebCrawlerExtractorProperties.ObjectPool poolConfig = extractorProperties.getObjectPool();
        BrowserContext browserContext = null;
        lock.lock();
        try {
            browserContext = poolConfig.getBorrowTimeout() > 0
                    ? objectPool.borrowObject(Duration.ofMillis(poolConfig.getBorrowTimeout()))
                    : objectPool.borrowObject();
            if (browserContext == null) {
                throw new NoSuchElementException("No available BrowserContext now");
            }

            try (Page page = browserContext.newPage()) {
                page.navigate(url, new Page.NavigateOptions().setTimeout(config.getTimeout())
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                if (config.getLoadingTimeout() > 0) {
                    ThreadUtils.sleep(config.getLoadingTimeout());
                } else {
                    ThreadUtils.randomSleep(1000L);
                }
                return page.content();
            }

        } finally {
            lock.unlock();
            if (browserContext != null) {
                objectPool.returnObject(browserContext);
            }
        }
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
        PlaywrightPooledExtractor extractor =
                new PlaywrightPooledExtractor(new WebCrawlerExtractorProperties());
        extractor.afterPropertiesSet();
        String html = extractor.extractHtml(null, "",
                "https://www.javaoneworld.com/p/tech-products.html", StandardCharsets.UTF_8, null);
        System.out.println(html);
        System.in.read();
        extractor.destroy();
    }



}
