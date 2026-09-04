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
import com.github.greenfinger.core.WebCrawlerConstants;
import com.github.greenfinger.core.WebCrawlerExtractorProperties;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawlTask;
import com.github.greenfinger.core.utils.ThreadUtils;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Proxy;

/**
 * Drives a real browser engine. The most faithful renderer of the four, and the right choice for a
 * site whose content only exists after its scripts have run.
 *
 * <p>
 * Playwright objects belong to the thread that created them, so the pool holds a complete
 * {@code Playwright}/{@code Browser}/{@code BrowserContext} triple rather than sharing one browser
 * across threads.
 * 
 * @Description: PlaywrightPooledExtractor
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class PlaywrightPooledExtractor extends PooledExtractor<PlaywrightPooledExtractor.Session> {

    private final WebCrawlerExtractorProperties extractorProperties;

    public PlaywrightPooledExtractor(WebCrawlerExtractorProperties extractorProperties) {
        this.extractorProperties = extractorProperties;
        WebCrawlerExtractorProperties.ObjectPool config = extractorProperties.getObjectPool();
        objectPoolConfig.setMinIdle(config.getMinIdle());
        objectPoolConfig.setMaxIdle(config.getMaxIdle());
        objectPoolConfig.setMaxTotal(config.getMaxTotal());
    }

    @Override
    public String getName() {
        return WebCrawlerConstants.EXTRACTOR_PLAYWRIGHT;
    }

    @Override
    protected BasePooledObjectFactory<Session> createObjectFactory() {
        return new SessionFactory(extractorProperties.getPlaywright());
    }

    @Override
    protected String requestUrl(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, CrawlTask task) throws Exception {
        WebCrawlerExtractorProperties.Playwright config = extractorProperties.getPlaywright();
        Session session = objectPool.borrowObject();
        Page page = null;
        try {
            page = session.context.newPage();
            page.navigate(url, new Page.NavigateOptions().setTimeout(config.getTimeout()));
            if (config.getLoadingTimeout() > 0) {
                ThreadUtils.sleep(config.getLoadingTimeout());
            }
            return page.content();
        } finally {
            if (page != null) {
                page.close();
            }
            objectPool.returnObject(session);
        }
    }

    /**
     * 
     * @Description: Session
     * @Author: Fred Feng
     * @Date: 29/08/2026
     * @Version 2.0.0
     */
    public static class Session {

        final Playwright playwright;
        final Browser browser;
        final BrowserContext context;

        Session(Playwright playwright, Browser browser, BrowserContext context) {
            this.playwright = playwright;
            this.browser = browser;
            this.context = context;
        }

        void close() {
            try {
                context.close();
                browser.close();
            } finally {
                playwright.close();
            }
        }

    }

    /**
     * 
     * @Description: SessionFactory
     * @Author: Fred Feng
     * @Date: 29/08/2026
     * @Version 2.0.0
     */
    static class SessionFactory extends BasePooledObjectFactory<Session> {

        private final WebCrawlerExtractorProperties.Playwright config;

        SessionFactory(WebCrawlerExtractorProperties.Playwright config) {
            this.config = config;
        }

        @Override
        public Session create() {
            Playwright playwright = Playwright.create();
            BrowserType.LaunchOptions options =
                    new BrowserType.LaunchOptions().setHeadless(config.isHeadless());
            if (StringUtils.isNotBlank(config.getProxyServer())) {
                options.setProxy(new Proxy(config.getProxyServer()));
            }
            BrowserType browserType = switch (config.getBrowser().toLowerCase()) {
                case "firefox" -> playwright.firefox();
                case "webkit" -> playwright.webkit();
                default -> playwright.chromium();
            };
            Browser browser = browserType.launch(options);
            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                    .setJavaScriptEnabled(config.isJavaScriptEnabled());
            if (!config.getDefaultHttpHeaders().isEmpty()) {
                contextOptions.setExtraHTTPHeaders(config.getDefaultHttpHeaders());
            }
            return new Session(playwright, browser, browser.newContext(contextOptions));
        }

        @Override
        public PooledObject<Session> wrap(Session session) {
            return new DefaultPooledObject<>(session);
        }

        @Override
        public void destroyObject(PooledObject<Session> pooledObject) {
            pooledObject.getObject().close();
        }

    }

}
