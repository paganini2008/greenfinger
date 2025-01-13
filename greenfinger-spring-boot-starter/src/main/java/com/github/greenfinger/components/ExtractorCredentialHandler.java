package com.github.greenfinger.components;

import com.github.greenfinger.CatalogDetails;

/**
 * 
 * @Description: ExtractorCredentialHandler
 * @Author: Fred Feng
 * @Date: 10/01/2025
 * @Version 1.0.0
 */
public interface ExtractorCredentialHandler<T> {

    void login(CatalogDetails catalogDetails, WebClientHolder<T> holder);

    default void logout(CatalogDetails catalogDetails, WebClientHolder<T> holder) {}

}
