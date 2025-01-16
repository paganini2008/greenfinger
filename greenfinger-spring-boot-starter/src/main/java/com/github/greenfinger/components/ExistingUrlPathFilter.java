package com.github.greenfinger.components;

/**
 * 
 * @Description: ExistingUrlPathFilter
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public interface ExistingUrlPathFilter extends WebCrawlerComponent {

    boolean mightExist(String path);

    default void clean() throws Exception {}

    default long size() throws Exception {
        return -1;
    }

    default int export(UrlPathFilterExporter exporter, boolean deleted) throws Exception {
        throw new UnsupportedOperationException("export");
    }

}
