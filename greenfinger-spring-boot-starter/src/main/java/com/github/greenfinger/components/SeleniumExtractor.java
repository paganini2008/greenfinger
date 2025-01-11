package com.github.greenfinger.components;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import com.github.doodler.common.context.ManagedBeanLifeCycle;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.utils.MapUtils;
import com.github.doodler.common.utils.RandomUtils;
import com.github.doodler.common.utils.ThreadUtils;
import com.github.greenfinger.WebCrawlerConstants;
import com.github.greenfinger.WebCrawlerExtractorProperties;
import com.github.greenfinger.components.test.AbstractExtractor;
import com.github.greenfinger.model.Catalog;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @Description: SeleniumExtractor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@RequiredArgsConstructor
public class SeleniumExtractor extends AbstractExtractor
        implements NamedExetractor, ManagedBeanLifeCycle {

    private final WebCrawlerExtractorProperties extractorProperties;
    private Map<String, String> defaultHttpHeaders = new HashMap<>() {

        private static final long serialVersionUID = 1L;
        {
            put("Accept", "*/*");
            put("Accept-Language", "en-US,en;q=0.9");
        }
    };

    public Map<String, String> getDefaultHttpHeaders() {
        return defaultHttpHeaders;
    }

    public void setDefaultHttpHeaders(Map<String, String> defaultHttpHeaders) {
        this.defaultHttpHeaders = defaultHttpHeaders;
    }

    private WebDriver driver;

    @Override
    public void afterPropertiesSet() throws Exception {
        System.setProperty("webdriver.chrome.driver",
                extractorProperties.getSelenium().getWebDriverExecutionPath());

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
        setDefaultHeaders(options);
        driver = new ChromeDriver(options);
    }

    public synchronized String requestUrl(Catalog catalog, String referUrl, String url,
            Charset pageEncoding, Packet packet) throws Exception {
        driver.get(url);
        WebCrawlerExtractorProperties.Selenium selenium = extractorProperties.getSelenium();
        if (selenium.getLoadingTimeout() > 0) {
            ThreadUtils.sleep(selenium.getLoadingTimeout());
        } else {
            ThreadUtils.randomSleep(1000L);
        }
        return driver.getPageSource();
    }

    private void setDefaultHeaders(ChromeOptions options) {
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
    public void destroy() throws Exception {
        if (driver != null) {
            driver.close();
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

        System.out.println(pageExtractor.extractHtml(null, "http://www.ttmeishi.com",
                "https://goldenmatrix.com/", null, null));
        System.in.read();
        pageExtractor.destroy();
    }



}
