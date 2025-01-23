package com.github.greenfinger.components;

import com.github.greenfinger.CatalogDetails;

/**
 * 
 * @Description: Dashboard
 * @Author: Fred Feng
 * @Date: 11/01/2025
 * @Version 1.0.0
 */
public interface Dashboard extends DashboardManager, WebCrawlerComponent {

    String NAMESPACE_PATTERN = "greenfinger:dashboard:%s:%s:%s";

    long getTotalUrlCount();

    long getInvalidUrlCount();

    long getExistingUrlCount();

    long getFilteredUrlCount();

    long getSavedResourceCount();

    long getIndexedResourceCount();

    long getStartTime();

    long getEndTime();

    long getElapsedTime();

    long getTimestamp();

    CatalogDetails getCatalogDetails();

}
