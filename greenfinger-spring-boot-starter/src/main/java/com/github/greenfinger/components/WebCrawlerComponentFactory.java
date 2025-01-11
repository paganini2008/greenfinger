package com.github.greenfinger.components;

import java.util.List;
import com.github.greenfinger.model.Catalog;
import com.github.greenfinger.model.CatalogIndex;

/**
 * 
 * @Description: WebCrawlerComponentFactory
 * @Author: Fred Feng
 * @Date: 11/01/2025
 * @Version 1.0.0
 */
public interface WebCrawlerComponentFactory {

    List<InterruptionChecker> getInterruptionCheckers(Catalog catalog, CatalogIndex catalogIndex);

    List<UrlPathAcceptor> getUrlPathAcceptors(Catalog catalog, CatalogIndex catalogIndex);

    Extractor getExtractor(Catalog catalog, CatalogIndex catalogIndex);

    ExistingUrlPathFilter getExistingUrlPathFilter(Catalog catalog, CatalogIndex catalogIndex);

    Dashboard getDashboard(Catalog catalog, CatalogIndex catalogIndex);

}
