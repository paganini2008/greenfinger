package com.github.greenfinger.jdbc;

import java.io.Serializable;
import org.springframework.stereotype.Service;
import com.github.greenfinger.CatalogDetails;
import com.github.greenfinger.CatalogDetailsImpl;
import com.github.greenfinger.CatalogDetailsNotFoundException;
import com.github.greenfinger.CatalogDetailsService;
import com.github.greenfinger.ResourceManager;
import com.github.greenfinger.WebCrawlerProperties;
import com.github.greenfinger.model.Catalog;
import com.github.greenfinger.model.CatalogIndex;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @Description: JdbcCatalogDetailsService
 * @Author: Fred Feng
 * @Date: 12/01/2025
 * @Version 1.0.0
 */
@Service
@RequiredArgsConstructor
public class JdbcCatalogDetailsService implements CatalogDetailsService {

    private final ResourceManager resourceManager;
    private final WebCrawlerProperties webCrawlerProperties;

    @Override
    public CatalogDetails loadCatalogDetails(Serializable id)
            throws CatalogDetailsNotFoundException {
        Long catalogId = (Long) id;
        Catalog catalog = resourceManager.getCatalog(catalogId);
        CatalogIndex catalogIndex = resourceManager.getCatalogIndex(catalogId);
        if (catalog == null || catalogIndex == null) {
            throw new CatalogDetailsNotFoundException("Catalog not found by: " + catalogId);
        }
        return new CatalogDetailsImpl(catalog, catalogIndex, null, webCrawlerProperties);
    }

}
