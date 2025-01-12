package com.github.greenfinger.components;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.DisposableBean;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.utils.Coookie;
import com.github.doodler.common.utils.MapUtils;
import com.github.doodler.common.utils.RandomUtils;
import com.github.doodler.common.utils.ThreadUtils;
import com.github.greenfinger.CatalogDetails;
import com.github.greenfinger.WebCrawlerConstants;
import com.github.greenfinger.WebCrawlerExtractorProperties;

/**
 * 
 * @Description: SeleniumStatefulExtractor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public class SeleniumStatefulExtractor extends StatefulExtractor<WebDriver>
        implements DisposableBean {

    public SeleniumStatefulExtractor(WebCrawlerExtractorProperties extractorProperties) {
        this(new DoNothingExtractorCredentialHandler<>(), extractorProperties);
    }

    public SeleniumStatefulExtractor(ExtractorCredentialHandler<WebDriver> credentialHandler,
            WebCrawlerExtractorProperties extractorProperties) {
        super(credentialHandler);
        this.extractorProperties = extractorProperties;
    }

    private final WebCrawlerExtractorProperties extractorProperties;

    private WebDriver driver;

    @Override
    public WebDriver createNew() {
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
        setDefaultHttpHeaders(options);
        return new ChromeDriver(options);
    }

    @Override
    public WebDriver get() {
        if (driver == null) {
            driver = createNew();
        }
        return driver;
    }

    @Override
    public void setExtraCookies(List<Coookie> coookies) {
        coookies.forEach(co -> {
            driver.manage()
                    .addCookie(new Cookie.Builder(co.getName(), co.getValue())
                            .domain(co.getDomain()).path(co.getPath()).expiresOn(co.getExpires())
                            .isSecure(co.isSecure()).isHttpOnly(co.isHttpOnly()).build());
        });
    }

    @Override
    public String test(String url, Charset pageEncoding) throws Exception {
        WebDriver driver = null;
        try {
            driver = createNew();
            return doRequestUrl(driver, url, url, pageEncoding, new Packet());
        } finally {
            if (driver != null) {
                driver.close();
            }
        }
    }

    public synchronized String requestUrl(CatalogDetails catalogDetails, String referUrl,
            String url, Charset pageEncoding, Packet packet) throws Exception {
        WebDriver driver = get();
        return doRequestUrl(driver, referUrl, url, pageEncoding, packet);
    }

    private String doRequestUrl(WebDriver driver, String referUrl, String url, Charset pageEncoding,
            Packet packet) throws Exception {
        driver.get(url);
        WebCrawlerExtractorProperties.Selenium selenium = extractorProperties.getSelenium();
        if (selenium.getLoadingTimeout() > 0) {
            ThreadUtils.sleep(selenium.getLoadingTimeout());
        } else {
            ThreadUtils.randomSleep(1000L);
        }
        return driver.getPageSource();
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
        SeleniumStatefulExtractor pageExtractor =
                new SeleniumStatefulExtractor(new WebCrawlerExtractorProperties());

        System.out
                .println(pageExtractor.test("https://goldenmatrix.com/", Charset.defaultCharset()));
        System.in.read();
        pageExtractor.destroy();
    }



}
