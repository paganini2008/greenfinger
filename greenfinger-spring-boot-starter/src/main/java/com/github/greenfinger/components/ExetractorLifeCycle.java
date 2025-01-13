package com.github.greenfinger.components;

import com.github.greenfinger.CatalogDetails;

/**
 * 
 * @Description: ExetractorLifeCycle
 * @Author: Fred Feng
 * @Date: 11/01/2025
 * @Version 1.0.0
 */
public interface ExetractorLifeCycle {

    default boolean hasLogged(CatalogDetails catalogDetails) {
        return true;
    }

    default void login(CatalogDetails catalogDetails) {}

    default void logout(CatalogDetails catalogDetails) {}

}
