package com.github.greenfinger;

import org.springframework.context.ApplicationEvent;

/**
 * 
 * @Description: WebCrawlerEvent
 * @Author: Fred Feng
 * @Date: 24/01/2025
 * @Version 1.0.0
 */
public class WebCrawlerEvent extends ApplicationEvent {

    private static final long serialVersionUID = -6385533273344685642L;

    public WebCrawlerEvent(Object source, CatalogDetails catalogDetails) {
        super(source);
        this.catalogDetails = catalogDetails;
    }

    private final CatalogDetails catalogDetails;

    public CatalogDetails getCatalogDetails() {
        return catalogDetails;
    }

}
