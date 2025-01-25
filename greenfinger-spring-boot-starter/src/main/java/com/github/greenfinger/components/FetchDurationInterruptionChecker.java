package com.github.greenfinger.components;

import java.util.concurrent.TimeUnit;
import com.github.doodler.common.utils.DateUtils;
import com.github.greenfinger.CatalogDetails;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @Description: FetchDurationInterruptionChecker
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@RequiredArgsConstructor
public class FetchDurationInterruptionChecker implements InterruptionChecker {

    @Override
    public boolean shouldInterrupt(CatalogDetails catalogDetails, Dashboard dashboard) {
        long duration = catalogDetails.getFetchDuration();
        long durationInMs = DateUtils.convertToMillis(duration, TimeUnit.MINUTES);
        return dashboard.getElapsedTime() > durationInMs;
    }

}
