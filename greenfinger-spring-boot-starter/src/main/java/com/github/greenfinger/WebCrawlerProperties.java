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

    private String defaultPageEncoding = "UTF-8";
    private int defaultMaxFetchSize = 10000;
    private int defaultMaxFetchDepth = -1;
    private long defaultFetchDuration = 5;
    private int defaultMaxRetryCount = 0;
    private long defaultFetchInterval = 1000L;
    private String defaultUrlPathFilter = "redission-bloomfilter";
    private String defaultExtractor = "resttemplate";
    private int estimatedCompletionDelayDuration = 1;
}
