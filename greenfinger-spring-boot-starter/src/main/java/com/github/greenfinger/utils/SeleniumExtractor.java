package com.github.greenfinger.utils;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.PooledObject;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.utils.MapUtils;
import com.github.doodler.common.utils.RandomUtils;
import com.github.greenfinger.WebCrawlerConstants;

/**
 * 
 * @Description: SeleniumExtractor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public class SeleniumExtractor extends PooledExtractor<WebDriver> implements Extractor {

    public SeleniumExtractor(String webdriverExecutionPath) {
        System.setProperty("webdriver.chrome.driver", webdriverExecutionPath);
        System.setProperty("webdriver.chrome.args", "--disable-logging");
        System.setProperty("webdriver.chrome.silentOutput", "true");
    }

    private String proxyAddress;

    public void setProxyAddress(String proxyAddress) {
        this.proxyAddress = proxyAddress;
    }

    public String extractHtml(String refer, String url, Charset pageEncoding, Packet packet)
            throws Exception {
        WebDriver webdriver = objectPool.borrowObject();
        try {
            webdriver.get(url);
            return webdriver.getPageSource();
        } finally {
            if (webdriver != null) {
                objectPool.returnObject(webdriver);
            }
        }
    }

    public WebDriver createObject() throws Exception {
        ChromeOptions options = new ChromeOptions();
        if (StringUtils.isNotBlank(proxyAddress)) {
            Proxy proxy = new Proxy();
            proxy.setHttpProxy(proxyAddress);
            options.setProxy(proxy);
        }
        options.addArguments("lang=en_US.UTF-8");
        options.addArguments(
                "user-agent=" + RandomUtils.randomChoice(WebCrawlerConstants.userAgents));
        options.addArguments("--test-type", "--ignore-certificate-errors", "--start-maximized",
                "no-default-browser-check");
        options.addArguments("--silent", "--headless", "--disable-gpu");
        setDefaultHeaders(options);
        ChromeDriver driver = new ChromeDriver(options);
        driver.setLogLevel(Level.ALL);
        driver.manage().timeouts().pageLoadTimeout(60, TimeUnit.SECONDS)
                .setScriptTimeout(60, TimeUnit.SECONDS).implicitlyWait(5, TimeUnit.SECONDS);
        return driver;
    }

    private void setDefaultHeaders(ChromeOptions options) {
        Map<String, String> defaultHeaders = getDefaultHeaders();
        if (MapUtils.isNotEmpty(defaultHeaders)) {
            List<String> arguments = new ArrayList<String>();
            for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
                arguments.add(entry.toString());
            }
            options.addArguments(arguments);
        }
    }

    private Map<String, String> getDefaultHeaders() {
        return null;
    }

    public void destroyObject(PooledObject<WebDriver> object) throws Exception {
        object.getObject().quit();
    }

    public static void main(String[] args) throws Exception {
        // String webdriverExecutionPath = "D:\\software\\chromedriver_win32\\chromedriver.exe";
        String webdriverExecutionPath = "D:\\software\\chromedriver_win32\\chromedriver.exe";
        SeleniumExtractor pageExtractor = new SeleniumExtractor(webdriverExecutionPath);
        pageExtractor.afterPropertiesSet();

        System.out.println(pageExtractor.extractHtml("http://www.ttmeishi.com",
                "https://www.google.com", null, null));
        System.in.read();
        pageExtractor.destroy();
    }

}
