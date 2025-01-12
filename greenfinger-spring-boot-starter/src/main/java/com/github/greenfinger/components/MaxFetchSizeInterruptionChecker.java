package com.github.greenfinger.components;

import com.github.greenfinger.CatalogDetails;
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

    @Override
    public boolean shouldInterrupt(CatalogDetails catalogDetails, Dashboard dashboardData) {
        int maxFetchSize = catalogDetails.getMaxFetchSize();
        CountingType countingType = catalogDetails.getCountingType();
        return countingType.compare(dashboardData, maxFetchSize);
    }


}
