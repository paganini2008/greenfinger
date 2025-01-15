package com.github.greenfinger.jdbc;

import java.io.Serializable;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import com.github.greenfinger.CatalogDetails;
import com.github.greenfinger.CatalogDetailsImpl;
import com.github.greenfinger.CatalogDetailsNotFoundException;
import com.github.greenfinger.CatalogDetailsService;
import com.github.greenfinger.ResourceManager;
import com.github.greenfinger.TooManyWebCrawlerException;
import com.github.greenfinger.WebCrawlerException;
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
    public CatalogDetails loadRunningCatalogDetails() throws WebCrawlerException {
        List<Catalog> catalogs = resourceManager.selectRunningCatalogs();
        if (CollectionUtils.isEmpty(catalogs)) {
            return null;
        }
        if (catalogs.size() == 1) {
            Catalog catalog = catalogs.get(0);
            CatalogIndex catalogIndex = resourceManager.getCatalogIndex(catalog.getId());
            if (catalog == null || catalogIndex == null) {
                throw new CatalogDetailsNotFoundException(
                        "Catalog not found by: " + catalog.getId());
            }
            return new CatalogDetailsImpl(catalog, catalogIndex, null, webCrawlerProperties);
        }
        throw new TooManyWebCrawlerException("Running catalog's count is: " + catalogs.size());
    }

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
