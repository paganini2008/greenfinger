package com.github.greenfinger.components;

import com.github.greenfinger.model.Catalog;

/**
 * 
 * @Description: InterruptionChecker
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
public interface InterruptionChecker {

    boolean shouldInterrupt(OneTimeDashboardData dashboardData, Catalog catalog);

}
