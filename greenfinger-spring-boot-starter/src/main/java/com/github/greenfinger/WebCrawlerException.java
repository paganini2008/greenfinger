package com.github.greenfinger;

/**
 * 
 * @Description: WebCrawlerException
 * @Author: Fred Feng
 * @Date: 12/01/2025
 * @Version 1.0.0
 */
public class WebCrawlerException extends Exception {

    private static final long serialVersionUID = -7487328253495738652L;

    public WebCrawlerException(String msg) {
        super(msg);
    }

    public WebCrawlerException(String msg, Throwable e) {
        super(msg, e);
    }

    public WebCrawlerException(Throwable e) {
        super(e);
    }

}
