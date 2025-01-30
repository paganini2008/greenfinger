package com.github.greenfinger.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.github.doodler.common.ApiResult;
import com.github.greenfinger.WebCrawling;
import com.github.greenfinger.searcher.ResourceIndexManager;

/**
 * 
 * @Description: IndexApiController
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@RequestMapping("/v1/index")
@RestController
public class IndexApiController {

    @Autowired
    private ResourceIndexManager resourceIndexManager;

    @WebCrawling
    @PostMapping("/sync")
    public ApiResult<String> recreateCatalogIndex() throws Exception {
        resourceIndexManager.recreateCatalogIndex();
        return ApiResult.ok("Recreate Catalog Index Successfully.");
    }

    @WebCrawling
    @PostMapping("/{id}/sync")
    public ApiResult<String> recreateCatalogIndex(@PathVariable("id") Long catalogId)
            throws Exception {
        resourceIndexManager.recreateCatalogIndex(catalogId);
        return ApiResult.ok("Recreate Catalog Index Successfully.");
    }

    @WebCrawling
    @PutMapping("/upgrade")
    public ApiResult<String> upgradeCatalogIndex() throws Exception {
        resourceIndexManager.upgradeCatalogIndex();
        return ApiResult.ok("Upgrade Catalog Index Successfully.");
    }

    @WebCrawling
    @PutMapping("/{id}/upgrade")
    public ApiResult<String> upgradeCatalogIndex(@PathVariable("id") Long catalogId)
            throws Exception {
        resourceIndexManager.upgradeCatalogIndex(catalogId);
        return ApiResult.ok("Upgrade Catalog Index Successfully.");
    }

    @WebCrawling
    @DeleteMapping("/{id}")
    public ApiResult<String> deleteResource(@PathVariable("id") Long catalogId,
            @RequestParam(name = "version", defaultValue = "-1", required = false) int version) {
        resourceIndexManager.deleteResource(catalogId, version);
        return ApiResult.ok("Delete Catalog Index Successfully.");
    }

}
