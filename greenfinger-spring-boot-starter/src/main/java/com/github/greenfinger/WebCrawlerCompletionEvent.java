package com.github.greenfinger;

/**
 * 
 * @Description: WebCrawlerCompletionEvent
 * @Author: Fred Feng
 * @Date: 23/01/2025
 * @Version 1.0.0
 */
public class WebCrawlerCompletionEvent extends WebCrawlerEvent {

    private static final long serialVersionUID = 6154762604256361444L;

    public WebCrawlerCompletionEvent(Object source, CatalogDetails catalogDetails) {
        super(source, catalogDetails);
    }

}
