package com.github.greenfinger;

import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import com.github.greenfinger.es.ResourceIndexService;
import com.github.greenfinger.model.CatalogIndex;

/**
 * 
 * @Description: CatalogAdminService
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
public class CatalogAdminService {

    @Autowired
    private ResourceManager resourceManager;

    @Autowired
    private ResourceIndexService resourceIndexService;

    public void cleanCatalog(long catalogId, boolean retainIndex) {
        resourceManager.deleteResourceByCatalogId(catalogId);
        if (!retainIndex) {
            resourceIndexService.deleteResource(catalogId, 0);
            resourceManager.updateCatalogIndex(new CatalogIndex(catalogId, 0, new Date()));
        }
    }

    public void deleteCatalog(long catalogId, boolean retainIndex) {
        resourceManager.deleteResourceByCatalogId(catalogId);
        resourceManager.deleteCatalog(catalogId);
        if (!retainIndex) {
            resourceIndexService.deleteResource(catalogId, 0);
        }
    }

}
