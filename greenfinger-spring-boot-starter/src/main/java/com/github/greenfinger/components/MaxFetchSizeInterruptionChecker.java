package com.github.greenfinger.components;

import com.github.greenfinger.WebCrawlerExtractorProperties;
import com.github.greenfinger.model.Catalog;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @Description: MaxFetchSizeInterruptionChecker
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@RequiredArgsConstructor
public class MaxFetchSizeInterruptionChecker implements InterruptionChecker {

    private final WebCrawlerExtractorProperties config;
    private final ConditionalCountingType countingType;

    @Override
    public boolean shouldInterrupt(OneTimeDashboardData dashboardData, Catalog catalog) {
        int maxFetchSize = catalog.getMaxFetchSize() != null ? catalog.getMaxFetchSize().intValue()
                : config.getDefaultMaxFetchSize();
        return countingType.compare(dashboardData, maxFetchSize);
    }



}
