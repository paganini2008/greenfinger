package com.github.greenfinger.components;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import com.github.doodler.common.utils.DateUtils;
import com.github.greenfinger.WebCrawlerProperties;
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

    private final Catalog catalog;
    private final WebCrawlerProperties config;

    @Override
    public boolean shouldInterrupt(Dashboard dashboardData) {
        long duration = Optional.ofNullable(catalog.getDuration())
                .orElse(config.getDefaultDuration()).longValue();
        long durationInMs = DateUtils.convertToMillis(duration, TimeUnit.MINUTES);
        return dashboardData.getElapsedTime() > durationInMs;
    }

}
