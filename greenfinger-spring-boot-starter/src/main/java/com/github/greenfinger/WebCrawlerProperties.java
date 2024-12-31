package com.github.greenfinger;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

/**
 * 
 * @Description: WebCrawlerProperties
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@ConfigurationProperties("greenfinger.pagesource")
@Data
public class WebCrawlerProperties {

    private int defaultMaxFetchSize = 10000;
    private int defaultFetchDepth = -1;
    private long defaultDuration = 5;

    private HtmlUnitPageSource htmlunit = new HtmlUnitPageSource();

    @Data
    public static class HtmlUnitPageSource {

        private String proxyHost;
        private int proxyPort;
        private int timeout = 60 * 1000;
        private int javaScriptTimeout = 60 * 1000;
    }

}
