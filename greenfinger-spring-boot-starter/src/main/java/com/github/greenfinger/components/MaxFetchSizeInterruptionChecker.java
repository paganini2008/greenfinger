package com.github.greenfinger.components;

import java.util.Optional;
import com.github.greenfinger.WebCrawlerProperties;
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

    private final Catalog catalog;
    private final WebCrawlerProperties config;

    @Override
    public boolean shouldInterrupt(Dashboard dashboardData) {
        int maxFetchSize = Optional.ofNullable(catalog.getMaxFetchSize())
                .orElse(config.getDefaultMaxFetchSize()).intValue();
        CountingType countingType = Optional.ofNullable(catalog.getCountingType())
                .orElse(CountingType.INDEXED_RESOURCE_COUNT);
        return countingType.compare(dashboardData, maxFetchSize);
    }


}
