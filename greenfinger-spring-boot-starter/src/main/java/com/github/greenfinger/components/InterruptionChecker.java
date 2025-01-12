package com.github.greenfinger.components;

import com.github.greenfinger.CatalogDetails;

/**
 * 
 * @Description: InterruptionChecker
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
public interface InterruptionChecker extends WebCrawlerComponent {

    boolean shouldInterrupt(CatalogDetails catalogDetails, Dashboard dashboard);

}
