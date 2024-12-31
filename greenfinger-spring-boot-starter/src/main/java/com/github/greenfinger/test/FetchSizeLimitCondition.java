package com.github.greenfinger.test;

import com.github.greenfinger.WebCrawlerProperties;
import com.github.greenfinger.model.Catalog;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @Description: FetchSizeLimitCondition
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@RequiredArgsConstructor
public class FetchSizeLimitCondition implements InterruptibleCondition {

    private final WebCrawlerProperties config;
    private final ConditionalCountingType countingType;

    @Override
    public boolean shouldInterrupt(OneTimeDashboardData dashboardData, Catalog catalog) {
        int maxFetchSize = catalog.getMaxFetchSize() != null ? catalog.getMaxFetchSize().intValue()
                : config.getDefaultMaxFetchSize();
        return countingType.compare(dashboardData, maxFetchSize);
    }



}
