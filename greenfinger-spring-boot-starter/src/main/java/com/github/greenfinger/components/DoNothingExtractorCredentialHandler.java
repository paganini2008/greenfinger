package com.github.greenfinger.components;

import com.github.greenfinger.CatalogDetails;

/**
 * 
 * @Description: DoNothingExtractorCredentialHandler
 * @Author: Fred Feng
 * @Date: 11/01/2025
 * @Version 1.0.0
 */
public class DoNothingExtractorCredentialHandler<T> implements ExtractorCredentialHandler<T> {

    DoNothingExtractorCredentialHandler() {}

    @Override
    public void login(CatalogDetails catalogDetails, WebClientHolder<T> supplier) {}

}
