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
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import com.github.doodler.common.context.ManagedBeanLifeCycle;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.utils.MapUtils;
import com.github.doodler.common.utils.RandomUtils;
import com.github.doodler.common.utils.ThreadUtils;
import com.github.greenfinger.CatalogDetails;
import com.github.greenfinger.WebCrawlerConstants;
import com.github.greenfinger.WebCrawlerExtractorProperties;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @Description: SeleniumExtractor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@RequiredArgsConstructor
public class SeleniumExtractor extends PooledExtractor<WebDriver>
        implements NamedExetractor, ManagedBeanLifeCycle {

    private final WebCrawlerExtractorProperties extractorProperties;

    @Override
    protected BasePooledObjectFactory<WebDriver> createObjectFactory() {
        return new BasePooledObjectFactory<WebDriver>() {

            @Override
            public PooledObject<WebDriver> wrap(WebDriver object) {
                return new DefaultPooledObject<WebDriver>(object);
            }

            @Override
            public WebDriver create() throws Exception {
                WebCrawlerExtractorProperties.Selenium selenium = extractorProperties.getSelenium();
                ChromeOptions options = new ChromeOptions();
                options.addArguments("--remote-allow-origins=*");
                options.addArguments("--headless");
                options.addArguments("--disable-gpu");
                options.addArguments("--window-size=1920,1080");
                options.addArguments("--disable-web-security");
                options.addArguments("--disable-blink-features=AutomationControlled");
                options.addArguments("--no-sandbox");
                options.addArguments("--disable-dev-shm-usage");
                options.addArguments(
                        "--user-agent=" + RandomUtils.randomChoice(WebCrawlerConstants.userAgents));
                if (StringUtils.isNotBlank(selenium.getProxyServer())) {
                    Proxy proxy = new Proxy();
                    proxy.setHttpProxy(selenium.getProxyServer());
                    options.setProxy(proxy);
                }
                setDefaultHttpHeaders(options);
                return new ChromeDriver(options);
            }

            @Override
            public void destroyObject(PooledObject<WebDriver> po) throws Exception {
                po.getObject().quit();
            }
        };
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        if (StringUtils.isNotBlank(extractorProperties.getSelenium().getWebDriverExecutionPath())) {
            System.setProperty("webdriver.chrome.driver",
                    extractorProperties.getSelenium().getWebDriverExecutionPath());
        } else {
            WebDriverManager.chromedriver().setup();
        }
        WebCrawlerExtractorProperties.ObjectPool poolConfig = extractorProperties.getObjectPool();
        getObjectPoolConfig().setMinIdle(poolConfig.getMinIdle());
        getObjectPoolConfig().setMaxIdle(poolConfig.getMaxIdle());
        getObjectPoolConfig().setMaxTotal(poolConfig.getMaxTotal());
        super.afterPropertiesSet();
    }

    protected String requestUrl(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, Packet packet) throws Exception {
        WebCrawlerExtractorProperties.Selenium config = extractorProperties.getSelenium();
        WebCrawlerExtractorProperties.ObjectPool poolConfig = extractorProperties.getObjectPool();
        WebDriver webDriver = null;

        try {
            webDriver = poolConfig.getBorrowTimeout() > 0
                    ? objectPool.borrowObject(Duration.ofMillis(poolConfig.getBorrowTimeout()))
                    : objectPool.borrowObject();
            if (webDriver == null) {
                throw new NoSuchElementException("No available WebDriver now");
            }
            webDriver.get(url);
            if (config.getLoadingTimeout() > 0) {
                ThreadUtils.sleep(config.getLoadingTimeout());
            } else {
                ThreadUtils.randomSleep(1000L);
            }
            return webDriver.getPageSource();
        } finally {
            if (webDriver != null) {
                objectPool.returnObject(webDriver);
            }
        }
    }

    private void setDefaultHttpHeaders(ChromeOptions options) {
        Map<String, String> defaultHeaders = new HashMap<>(this.defaultHttpHeaders);
        defaultHeaders.putAll(extractorProperties.getSelenium().getDefaultHttpHeaders());
        if (MapUtils.isNotEmpty(defaultHeaders)) {
            List<String> arguments = new ArrayList<String>();
            for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
                arguments.add(entry.toString());
            }
            options.addArguments(arguments);
        }
    }

    @Override
    public String getName() {
        return WebCrawlerConstants.EXTRACTOR_SELENIUM;
    }

    public static void main(String[] args) throws Exception {
        // String webdriverExecutionPath = "D:\\software\\chromedriver_win32\\chromedriver.exe";
        // String webdriverExecutionPath =
        // "G:\\selfEmployed\\chromedriver-win64\\chromedriver-win64\\chromedriver.exe";
        SeleniumExtractor pageExtractor =
                new SeleniumExtractor(new WebCrawlerExtractorProperties());
        pageExtractor.afterPropertiesSet();

        System.out.println(pageExtractor.extractHtml(null, "https://greatist.com/",
                "https://www.baidu.com/", null, null));
        System.in.read();
        pageExtractor.destroy();
    }



}
