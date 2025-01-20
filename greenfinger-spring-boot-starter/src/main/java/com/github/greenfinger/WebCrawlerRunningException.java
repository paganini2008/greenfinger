package com.github.greenfinger;

/**
 * 
 * @Description: WebCrawlerRunningException
 * @Author: Fred Feng
 * @Date: 20/01/2025
 * @Version 1.0.0
 */
public class WebCrawlerRunningException extends WebCrawlerException {

    private static final long serialVersionUID = -3946759450484990969L;

    public WebCrawlerRunningException(String msg) {
        super(msg);
    }

    public WebCrawlerRunningException(String msg, Throwable e) {
        super(msg, e);
    }

    public WebCrawlerRunningException(Throwable e) {
        super(e);
    }

}
