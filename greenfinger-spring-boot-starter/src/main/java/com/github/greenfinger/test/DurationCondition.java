package com.github.greenfinger.test;

import java.util.concurrent.TimeUnit;
import com.github.doodler.common.utils.DateUtils;
import com.github.greenfinger.WebCrawlerProperties;
import com.github.greenfinger.model.Catalog;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @Description: DurationCondition
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@RequiredArgsConstructor
public class DurationCondition implements InterruptibleCondition {

    private final WebCrawlerProperties config;

    @Override
    public boolean shouldInterrupt(OneTimeDashboardData dashboardData, Catalog catalog) {
        long duration = catalog.getDuration() != null ? catalog.getDuration().longValue()
                : config.getDefaultDuration();
        long durationInMs = DateUtils.convertToMillis(duration, TimeUnit.MINUTES);
        return dashboardData.getElapsedTime() > durationInMs;
    }

}
