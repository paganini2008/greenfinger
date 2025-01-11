package com.github.greenfinger.components;

import com.github.greenfinger.components.test.WebClientHolder;
import com.github.greenfinger.model.Catalog;
import com.github.greenfinger.model.CatalogCredential;

/**
 * 
 * @Description: ExtractorCredentialHandler
 * @Author: Fred Feng
 * @Date: 11/01/2025
 * @Version 1.0.0
 */
public interface ExtractorCredentialHandler<T> {

    void login(WebClientHolder<T> supplier, Catalog catalog, CatalogCredential catalogCredential);

    default void logout(WebClientHolder<T> supplier, Catalog catalog) {}

    String getName();
}
