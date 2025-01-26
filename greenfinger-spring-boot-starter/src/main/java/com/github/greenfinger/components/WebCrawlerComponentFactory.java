package com.github.greenfinger.components;

import java.util.List;
import com.github.greenfinger.CatalogDetails;

/**
 * 
 * @Description: WebCrawlerComponentFactory
 * @Author: Fred Feng
 * @Date: 11/01/2025
 * @Version 1.0.0
 */
public interface WebCrawlerComponentFactory {

    List<InterruptionChecker> getInterruptionCheckers(CatalogDetails catalogDetails);

    List<UrlPathAcceptor> getUrlPathAcceptors(CatalogDetails catalogDetails);

    Extractor getExtractor(CatalogDetails catalogDetails);

    ExistingUrlPathFilter getExistingUrlPathFilter(CatalogDetails catalogDetails);

    GlobalStateManager getGlobalStateManager(CatalogDetails catalogDetails);

    ProgressBarSupplier getProgressBarSupplier(CatalogDetails catalogDetails);
}
