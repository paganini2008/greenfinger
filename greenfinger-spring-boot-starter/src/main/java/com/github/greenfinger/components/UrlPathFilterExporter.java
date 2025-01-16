package com.github.greenfinger.components;

/**
 * 
 * @Description: UrlPathFilterExporter
 * @Author: Fred Feng
 * @Date: 16/01/2025
 * @Version 1.0.0
 */
@FunctionalInterface
public interface UrlPathFilterExporter {

    boolean doExport(int index, String item);

}
