package com.github.greenfinger;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * 
 * @Description: WebCrawlerExtractorProperties
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@ConfigurationProperties("greenfinger.extractor")
@Data
public class WebCrawlerExtractorProperties {

    private int defaultMaxFetchSize = 10000;
    private int defaultFetchDepth = -1;
    private long defaultDuration = 5;
    private int defaultRetries = 3;
    private long defaultIntervalTime = 1000L;

    private Default restTemplate = new Default();
    private Playwright playwright = new Playwright();
    private Selenium selenium = new Selenium();
    private HtmlUnit htmlunit = new HtmlUnit();

    @Getter
    @Setter
    public static class Base {

        private Map<String, String> defaultHttpHeaders = new HashMap<>();
        private String proxyHost;
        private int proxyPort;
        private long loadingTimeout;

        public String getProxyServer() {
            if (StringUtils.isNotBlank(proxyHost) && proxyPort > 0) {
                return proxyHost + ":" + proxyPort;
            }
            return "";
        }

    }

    @Getter
    @Setter
    public static class Default extends Base {

        private int connectionTime = 10000;
        private int readTimeout = 60000;
    }

    @Getter
    @Setter
    public static class Playwright extends Base {

        private boolean javaScriptEnabled = true;
        private int timeout = 60 * 1000;
    }

    @Getter
    @Setter
    public static class Selenium extends Base {

        private String webDriverExecutionPath =
                "G:\\selfEmployed\\chromedriver-win64\\chromedriver-win64\\chromedriver.exe";
    }

    @Getter
    @Setter
    public static class HtmlUnit extends Base {

        private int timeout = 60 * 1000;
        private boolean javaScriptEnabled = true;
        private long javaScriptTimeout = 60L * 1000;
    }

}
