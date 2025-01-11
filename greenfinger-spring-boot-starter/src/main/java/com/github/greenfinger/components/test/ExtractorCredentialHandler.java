package com.github.greenfinger.components.test;

/**
 * 
 * @Description: ExtractorCredentialHandler
 * @Author: Fred Feng
 * @Date: 10/01/2025
 * @Version 1.0.0
 */
public interface ExtractorCredentialHandler<T> {

    void login(WebClientHolder<T> holder);

    default void logout(WebClientHolder<T> holder) {}

}
