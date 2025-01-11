package com.github.greenfinger.components.test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;
import com.github.doodler.common.context.ManagedBeanLifeCycle;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.utils.Coookie;
import com.github.doodler.common.utils.RandomIpUtils;
import com.github.doodler.common.utils.RandomUtils;
import com.github.doodler.common.utils.ThreadUtils;
import com.github.greenfinger.WebCrawlerConstants;
import com.github.greenfinger.WebCrawlerExtractorProperties;
import com.github.greenfinger.model.Catalog;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.Proxy;

/**
 * 
 * @Description: PlaywrighStatefulExtractor
 * @Author: Fred Feng
 * @Date: 10/01/2025
 * @Version 1.0.0
 */
public class PlaywrighStatefulExtractor extends StatefulExtractor<BrowserContext>
        implements ManagedBeanLifeCycle {

    public PlaywrighStatefulExtractor(WebCrawlerExtractorProperties extractorProperties) {
        this(new DoNothingExtractorCredentialHandler<>(), extractorProperties);
    }

    public PlaywrighStatefulExtractor(ExtractorCredentialHandler<BrowserContext> credentialHandler,
            WebCrawlerExtractorProperties extractorProperties) {
        super(credentialHandler);
        this.extractorProperties = extractorProperties;
    }

    private final WebCrawlerExtractorProperties extractorProperties;

    private Playwright playwright;
    private BrowserContext browserContext;

    @Override
    public void afterPropertiesSet() throws Exception {
        playwright = Playwright.create();
    }

    @Override
    public BrowserContext createNew() {
        Browser browser =
                playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        WebCrawlerExtractorProperties.Playwright config = extractorProperties.getPlaywright();
        Browser.NewContextOptions newContextOptions =
                new Browser.NewContextOptions().setIsMobile(false).setAcceptDownloads(false)
                        .setJavaScriptEnabled(config.isJavaScriptEnabled())
                        .setExtraHTTPHeaders(mergeHttpHeaders());
        if (StringUtils.isNotBlank(config.getProxyServer())) {
            newContextOptions.setProxy(new Proxy(config.getProxyServer()));
        }
        return browser.newContext(newContextOptions);
    }

    @Override
    public BrowserContext get() {
        if (browserContext == null) {
            browserContext = createNew();
        }
        return browserContext;
    }

    @Override
    public String test(String url, Charset pageEncoding) throws Exception {
        BrowserContext context = null;
        try {
            context = createNew();
            return doRequestUrl(context, url, url, pageEncoding, new Packet());
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    @Override
    public void setExtraCookies(List<Coookie> coookies) {
        Assert.notNull(coookies, "Cookie list must not be null");
        browserContext.addCookies(coookies.stream()
                .map(co -> new Cookie(co.getName(), co.getValue()).setDomain(co.getDomain())
                        .setPath(co.getPath()).setExpires(co.getMaxAge())
                        .setHttpOnly(co.isHttpOnly()).setSecure(co.isSecure()))
                .collect(Collectors.toList()));
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
    public synchronized String requestUrl(Catalog catalog, String referUrl, String url,
            Charset pageEncoding, Packet packet) throws Exception {
        BrowserContext context = get();
        return doRequestUrl(context, referUrl, url, pageEncoding, packet);
    }

    private String doRequestUrl(BrowserContext context, String referUrl, String url,
            Charset pageEncoding, Packet packet) {
        WebCrawlerExtractorProperties.Playwright config = extractorProperties.getPlaywright();
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
    public void destroy() throws Exception {
        if (browserContext != null) {
            browserContext.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @Override
    public String getName() {
        return WebCrawlerConstants.EXTRACTOR_PLAYWRIGHT;
    }

    public static void main(String[] args) throws Exception {
        PlaywrighStatefulExtractor extractor =
                new PlaywrighStatefulExtractor(new WebCrawlerExtractorProperties());
        extractor.afterPropertiesSet();
        String html = extractor.test("https://goldenmatrix.com/company", StandardCharsets.UTF_8);
        System.out.println(html);
        System.in.read();
        extractor.destroy();
    }



}
