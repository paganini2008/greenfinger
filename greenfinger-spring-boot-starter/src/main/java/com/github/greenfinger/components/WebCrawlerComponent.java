package com.github.greenfinger.components;

/**
 * 
 * @Description: WebCrawlerComponent
 * @Author: Fred Feng
 * @Date: 11/01/2025
 * @Version 1.0.0
 */
public interface WebCrawlerComponent {

    default String getDescription() {
        return getClass().getSimpleName();
    }

}
