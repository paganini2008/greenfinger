package com.github.greenfinger.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.github.doodler.common.ApiResult;
import com.github.doodler.common.jdbc.page.PageBean;
import com.github.doodler.common.jdbc.page.PageResponse;
import com.github.doodler.common.utils.ThreadUtils;
import com.github.greenfinger.es.ResourceIndexService;
import com.github.greenfinger.es.SearchResult;

/**
 * 
 * @Description: IndexApiController
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@RequestMapping("/api/index/")
@RestController
public class IndexApiController {

    @Autowired
    private ResourceIndexService resourceIndexService;

    @PostMapping("/all")
    public ApiResult<String> indexAllCatalogs() {
        ThreadUtils.runAsThread(() -> {
            resourceIndexService.indexCatalogIndex();
        });
        return ApiResult.ok("Submit Successfully.");
    }

    @PostMapping("/{id}")
    public ApiResult<String> indexCatalog(@PathVariable("id") Long catalogId) {
        ThreadUtils.runAsThread(() -> {
            resourceIndexService.indexCatalogIndex(catalogId);
        });
        return ApiResult.ok("Submit Successfully.");
    }

    @PostMapping("/upgrade/all")
    public ApiResult<String> upgradeAllCatalogs() {
        ThreadUtils.runAsThread(() -> {
            resourceIndexService.upgradeCatalogIndex();
        });
        return ApiResult.ok("Submit Successfully.");
    }

    @PostMapping("/upgrade/{id}")
    public ApiResult<String> upgradeCatalog(@PathVariable("id") Long catalogId) {
        ThreadUtils.runAsThread(() -> {
            resourceIndexService.upgradeCatalogIndex(catalogId);
        });
        return ApiResult.ok("Submit Successfully.");
    }

    @PostMapping("/{id}/delete")
    public ApiResult<String> deleteResource(@PathVariable("id") Long catalogId,
            @RequestParam(name = "version", defaultValue = "0", required = false) int version) {
        resourceIndexService.deleteResource(catalogId, version);
        return ApiResult.ok("Submit Successfully.");
    }

    @PostMapping("/search")
    public ApiResult<PageBean<SearchResult>> search(@RequestParam("q") String keyword,
            @RequestParam(name = "cat", required = false) String cat,
            @RequestParam(name = "version", required = false) Integer version,
            @RequestParam(value = "page", defaultValue = "1", required = false) int page,
            @CookieValue(value = "DATA_LIST_SIZE", required = false, defaultValue = "10") int size,
            Model ui) throws Exception {
        PageResponse<SearchResult> pageResponse =
                resourceIndexService.search(cat, keyword, version, page, size);
        PageBean<SearchResult> pageBean = PageBean.wrap(pageResponse);
        return ApiResult.ok(pageBean);
    }

}
