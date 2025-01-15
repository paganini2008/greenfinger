package com.github.greenfinger;

/**
 * 
 * @Description: TooManyWebCrawlerException
 * @Author: Fred Feng
 * @Date: 15/01/2025
 * @Version 1.0.0
 */
public class TooManyWebCrawlerException extends WebCrawlerException {

    private static final long serialVersionUID = -3087975182887458732L;

    public TooManyWebCrawlerException(String msg) {
        super(msg);
    }

    public TooManyWebCrawlerException(String msg, Throwable e) {
        super(msg, e);
    }

    public TooManyWebCrawlerException(Throwable e) {
        super(e);
    }



}
