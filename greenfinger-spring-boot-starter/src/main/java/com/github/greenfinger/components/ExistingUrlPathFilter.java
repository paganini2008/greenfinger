package com.github.greenfinger.components;

/**
 * 
 * @Description: ExistingUrlPathFilter
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public interface ExistingUrlPathFilter {

    String NAMESPACE_PATTERN = "greenfinger:url-path-filter:%s:%s";

    boolean mightExist(String path);

    default void clean() {}

    default long size() {
        return -1;
    }

}
