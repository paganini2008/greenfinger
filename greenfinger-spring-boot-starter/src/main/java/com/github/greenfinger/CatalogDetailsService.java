package com.github.greenfinger;

import java.io.Serializable;

/**
 * 
 * @Description: CatalogDetailsService
 * @Author: Fred Feng
 * @Date: 12/01/2025
 * @Version 1.0.0
 */
public interface CatalogDetailsService {

    CatalogDetails loadCatalogDetails(Serializable id) throws CatalogDetailsNotFoundException;

}
