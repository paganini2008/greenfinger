package com.github.greenfinger;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

/**
 * 
 * @Description: WebCrawlerProperties
 * @Author: Fred Feng
 * @Date: 11/01/2025
 * @Version 1.0.0
 */
@ConfigurationProperties("greenfinger")
@Data
public class WebCrawlerProperties {

    private int defaultMaxFetchSize = 10000;
    private int defaultFetchDepth = -1;
    private long defaultDuration = 5;
    private int defaultRetryCount = 3;
    private long defaultIntervalTime = 1000L;
}
