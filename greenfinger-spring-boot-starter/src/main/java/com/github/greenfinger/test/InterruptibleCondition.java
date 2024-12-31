package com.github.greenfinger.test;

import com.github.greenfinger.model.Catalog;

/**
 * 
 * @Description: InterruptibleCondition
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
public interface InterruptibleCondition {

    boolean shouldInterrupt(OneTimeDashboardData dashboardData, Catalog catalog);

}
