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
import com.github.greenfinger.searcher.ResourceIndexService;

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
    private ResourceIndexService resourceIndexService;

    @PostMapping("/create")
    public ApiResult<String> indexAllCatalogs() throws Exception {
        resourceIndexService.indexCatalogIndex();
        return ApiResult.ok("Submit Successfully.");
    }

    @PostMapping("/{id}/create")
    public ApiResult<String> indexCatalog(@PathVariable("id") Long catalogId) throws Exception {
        resourceIndexService.indexCatalogIndex(catalogId);
        return ApiResult.ok("Submit Successfully.");
    }

    @PutMapping("/upgrade")
    public ApiResult<String> upgradeAllCatalogs() throws Exception {
        resourceIndexService.upgradeCatalogIndex();
        return ApiResult.ok("Submit Successfully.");
    }

    @PutMapping("/{id}/upgrade")
    public ApiResult<String> upgradeCatalog(@PathVariable("id") Long catalogId) throws Exception {
        resourceIndexService.upgradeCatalogIndex(catalogId);
        return ApiResult.ok("Submit Successfully.");
    }

    @DeleteMapping("/{id}")
    public ApiResult<String> deleteResource(@PathVariable("id") Long catalogId,
            @RequestParam(name = "version", defaultValue = "0", required = false) int version) {
        resourceIndexService.deleteResource(catalogId, version);
        return ApiResult.ok("Submit Successfully.");
    }

}
