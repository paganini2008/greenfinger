package com.github.greenfinger;

/**
 * 
 * @Description: WebCrawlerInterruptEvent
 * @Author: Fred Feng
 * @Date: 24/01/2025
 * @Version 1.0.0
 */
public class WebCrawlerInterruptEvent extends WebCrawlerEvent {

    private static final long serialVersionUID = -6175992178693806318L;

    public WebCrawlerInterruptEvent(Object source, CatalogDetails catalogDetails) {
        super(source, catalogDetails);
    }

}
