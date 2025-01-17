package com.github.greenfinger;

import java.util.List;
import com.github.doodler.common.page.PageResponse;
import com.github.doodler.common.page.PageVo;
import com.github.greenfinger.api.pojo.CatalogInfo;
import com.github.greenfinger.model.Catalog;
import com.github.greenfinger.model.CatalogIndex;
import com.github.greenfinger.model.Resource;

/**
 * 
 * @Description: ResourceManager
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public interface ResourceManager {

    long saveCatalog(Catalog catalog);

    int deleteCatalog(long id);

    Catalog getCatalog(long id);

    List<Catalog> selectRunningCatalogs();

    int setRunningState(long id, String state);

    PageVo<CatalogInfo> pageForCatalog(Catalog example, int page, int size);

    PageResponse<CatalogInfo> pageForCatalog2(Catalog example, int page, int size);

    List<String> selectAllCats();

    int updateCatalogIndex(CatalogIndex catalogIndex);

    int maximumVersionOfCatalogIndex(String cat);

    CatalogIndex getCatalogIndex(long catalogId);

    int getCatalogIndexVersion(long catalogId);

    int saveResource(Resource resource);

    Resource getResource(long id);

    int deleteResourceByCatalogId(long catalogId);

    PageResponse<Resource> pageForResourceForIndex(long catalogId, int page, int size);

    int updateResourceVersion(long catalogId, int version);

    int incrementCatalogIndexVersion(long catalogId);

    String getLatestReferencePath(long catalogId);

}
