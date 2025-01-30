package com.github.greenfinger;

import org.springframework.stereotype.Service;
import com.github.greenfinger.searcher.ResourceIndexManager;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @Description: CatalogAdminService
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@Service
@RequiredArgsConstructor
public class CatalogAdminService {

    private final ResourceManager resourceManager;
    private final ResourceIndexManager resourceIndexManager;

    @WebCrawling
    public void cleanCatalog(long catalogId, boolean retainIndex) {
        resourceManager.deleteResourceByCatalogId(catalogId);
        if (!retainIndex) {
            resourceIndexManager.deleteResource(catalogId, -1);
            // resourceManager.updateCatalogIndex(new CatalogIndex(catalogId, 0, new Date()));
        }
    }

    @WebCrawling
    public void deleteCatalog(long catalogId, boolean retainIndex) {
        resourceManager.deleteResourceByCatalogId(catalogId);
        resourceManager.deleteCatalog(catalogId);
        if (!retainIndex) {
            resourceIndexManager.deleteResource(catalogId, -1);
        }
    }

}
