package com.github.greenfinger;

/**
 * 
 * @Description: CatalogDetailsNotFoundException
 * @Author: Fred Feng
 * @Date: 12/01/2025
 * @Version 1.0.0
 */
public class CatalogDetailsNotFoundException extends WebCrawlerException {

    private static final long serialVersionUID = 4685869357445148543L;

    public CatalogDetailsNotFoundException(String msg) {
        super(msg);
    }

    public CatalogDetailsNotFoundException(String msg, Throwable e) {
        super(msg, e);
    }

    public CatalogDetailsNotFoundException(Throwable e) {
        super(e);
    }

}
