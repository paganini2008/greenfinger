package com.github.greenfinger;

import org.springframework.context.ApplicationEvent;

/**
 * 
 * @Description: WebCrawlerCompletionEvent
 * @Author: Fred Feng
 * @Date: 23/01/2025
 * @Version 1.0.0
 */
public class WebCrawlerCompletionEvent extends ApplicationEvent {

    public WebCrawlerCompletionEvent(Object source, long catalogId, int version) {
        super(source);
        this.catalogId = catalogId;
        this.version = version;
    }

    private static final long serialVersionUID = -6327834323049708436L;

    private final long catalogId;
    private final int version;

    public long getCatalogId() {
        return catalogId;
    }

    public int getVersion() {
        return version;
    }
}
