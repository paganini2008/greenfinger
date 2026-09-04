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
import java.time.Duration;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import com.github.greenfinger.core.WebCrawlerConstants;
import com.github.greenfinger.core.WebCrawlerExtractorProperties;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawlTask;
import com.github.greenfinger.core.utils.ThreadUtils;
import io.github.bonigarcia.wdm.WebDriverManager;

/**
 * Drives a browser through WebDriver. Overlaps with Playwright in capability, and is the right
 * choice when a site is already exercised by an existing Selenium suite.
 *
 * <p>
 * The driver binary is resolved by WebDriverManager against whatever browser is installed. 1.x
 * hard-coded an absolute Windows path to chromedriver.exe, so the engine only ever worked on the
 * one machine it was written on.
 * 
 * @Description: SeleniumExtractor
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class SeleniumExtractor extends PooledExtractor<WebDriver> {

    private final WebCrawlerExtractorProperties extractorProperties;

    public SeleniumExtractor(WebCrawlerExtractorProperties extractorProperties) {
        this.extractorProperties = extractorProperties;
        WebCrawlerExtractorProperties.ObjectPool config = extractorProperties.getObjectPool();
        objectPoolConfig.setMinIdle(config.getMinIdle());
        objectPoolConfig.setMaxIdle(config.getMaxIdle());
        objectPoolConfig.setMaxTotal(config.getMaxTotal());
    }

    @Override
    public String getName() {
        return WebCrawlerConstants.EXTRACTOR_SELENIUM;
    }

    @Override
    protected BasePooledObjectFactory<WebDriver> createObjectFactory() {
        return new WebDriverFactory(extractorProperties.getSelenium());
    }

    @Override
    protected String requestUrl(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, CrawlTask task) throws Exception {
        WebCrawlerExtractorProperties.Selenium config = extractorProperties.getSelenium();
        WebDriver webDriver = objectPool.borrowObject();
        try {
            webDriver.get(url);
            if (config.getLoadingTimeout() > 0) {
                ThreadUtils.sleep(config.getLoadingTimeout());
            }
            return webDriver.getPageSource();
        } finally {
            objectPool.returnObject(webDriver);
        }
    }

    /**
     * 
     * @Description: WebDriverFactory
     * @Author: Fred Feng
     * @Date: 29/08/2026
     * @Version 2.0.0
     */
    static class WebDriverFactory extends BasePooledObjectFactory<WebDriver> {

        private final WebCrawlerExtractorProperties.Selenium config;

        WebDriverFactory(WebCrawlerExtractorProperties.Selenium config) {
            this.config = config;
        }

        @Override
        public WebDriver create() {
            boolean firefox = "firefox".equalsIgnoreCase(config.getBrowser());
            if (StringUtils.isNotBlank(config.getWebDriverExecutionPath())) {
                System.setProperty(firefox ? "webdriver.gecko.driver" : "webdriver.chrome.driver",
                        config.getWebDriverExecutionPath());
            } else if (firefox) {
                WebDriverManager.firefoxdriver().setup();
            } else {
                WebDriverManager.chromedriver().setup();
            }

            Proxy proxy = null;
            if (StringUtils.isNotBlank(config.getProxyServer())) {
                proxy = new Proxy();
                proxy.setHttpProxy(config.getProxyServer());
                proxy.setSslProxy(config.getProxyServer());
            }

            WebDriver webDriver;
            if (firefox) {
                FirefoxOptions options = new FirefoxOptions();
                if (config.isHeadless()) {
                    options.addArguments("-headless");
                }
                if (proxy != null) {
                    options.setProxy(proxy);
                }
                webDriver = new FirefoxDriver(options);
            } else {
                ChromeOptions options = new ChromeOptions();
                if (config.isHeadless()) {
                    options.addArguments("--headless=new");
                }
                options.addArguments("--disable-gpu", "--no-sandbox",
                        "--disable-dev-shm-usage");
                if (proxy != null) {
                    options.setProxy(proxy);
                }
                webDriver = new ChromeDriver(options);
            }
            webDriver.manage().timeouts()
                    .pageLoadTimeout(Duration.ofMillis(config.getPageLoadTimeout()));
            return webDriver;
        }

        @Override
        public PooledObject<WebDriver> wrap(WebDriver webDriver) {
            return new DefaultPooledObject<>(webDriver);
        }

        @Override
        public void destroyObject(PooledObject<WebDriver> pooledObject) {
            pooledObject.getObject().quit();
        }

    }

}
