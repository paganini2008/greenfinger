package com.github.greenfinger.components;

import java.util.concurrent.TimeUnit;
import com.github.doodler.common.utils.DateUtils;
import com.github.greenfinger.WebCrawlerExtractorProperties;
import com.github.greenfinger.model.Catalog;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @Description: DurationInterruptionChecker
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@RequiredArgsConstructor
public class DurationInterruptionChecker implements InterruptionChecker {

    private final WebCrawlerExtractorProperties config;

    @Override
    public boolean shouldInterrupt(OneTimeDashboardData dashboardData, Catalog catalog) {
        long duration = catalog.getDuration() != null ? catalog.getDuration().longValue()
                : config.getDefaultDuration();
        long durationInMs = DateUtils.convertToMillis(duration, TimeUnit.MINUTES);
        return dashboardData.getElapsedTime() > durationInMs;
    }

}
