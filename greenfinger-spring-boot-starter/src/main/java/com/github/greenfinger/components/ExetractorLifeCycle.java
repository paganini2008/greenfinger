package com.github.greenfinger.components;

import com.github.greenfinger.model.Catalog;

/**
 * 
 * @Description: ExetractorLifeCycle
 * @Author: Fred Feng
 * @Date: 11/01/2025
 * @Version 1.0.0
 */
public interface ExetractorLifeCycle {

    default boolean hasLogged(Catalog catalog) {
        return true;
    }

    default void login(Catalog catalog) {}

    default void logout(Catalog catalog) {}

}
