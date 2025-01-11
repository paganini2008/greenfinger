package com.github.greenfinger.components;

/**
 * 
 * @Description: ExetractorLifeCycle
 * @Author: Fred Feng
 * @Date: 11/01/2025
 * @Version 1.0.0
 */
public interface ExetractorLifeCycle {

    default boolean hasLogged() {
        return true;
    }

    default void login() {}

    default void logout() {}

}
