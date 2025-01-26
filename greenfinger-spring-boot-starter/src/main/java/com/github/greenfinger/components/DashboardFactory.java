package com.github.greenfinger.components;

import com.github.greenfinger.CatalogDetails;

/**
 * 
 * @Description: DashboardFactory
 * @Author: Fred Feng
 * @Date: 26/01/2025
 * @Version 1.0.0
 */
public interface DashboardFactory {

    Dashboard getDashboard(CatalogDetails catalogDetails);

    Dashboard getReadyonlyDashboard(CatalogDetails catalogDetails);

}
