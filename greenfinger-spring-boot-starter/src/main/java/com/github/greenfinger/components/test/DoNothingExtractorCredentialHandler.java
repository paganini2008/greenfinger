package com.github.greenfinger.components.test;

import com.github.greenfinger.model.Catalog;

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
    public void login(Catalog catalog, WebClientHolder<T> supplier) {}

}
