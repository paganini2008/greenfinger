package com.github.greenfinger.components;

/**
 * 
 * @Description: InterruptionChecker
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
public interface InterruptionChecker extends WebCrawlerComponent {

    boolean shouldInterrupt(Dashboard dashboard);

}
