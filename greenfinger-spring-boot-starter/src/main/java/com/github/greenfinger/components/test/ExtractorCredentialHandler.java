package com.github.greenfinger.components.test;

import com.github.greenfinger.model.Catalog;

/**
 * 
 * @Description: ExtractorCredentialHandler
 * @Author: Fred Feng
 * @Date: 10/01/2025
 * @Version 1.0.0
 */
public interface ExtractorCredentialHandler<T> {

    void login(Catalog catalog, WebClientHolder<T> holder);

    default void logout(Catalog catalog, WebClientHolder<T> holder) {}

}
