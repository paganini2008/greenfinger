package com.github.greenfinger.api;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.github.doodler.common.ApiResult;
import com.github.doodler.common.page.PageBean;
import com.github.doodler.common.page.PageResponse;
import com.github.greenfinger.CatalogAdminService;
import com.github.greenfinger.ResourceManager;
import com.github.greenfinger.WebCrawlerExecutionContext;
import com.github.greenfinger.model.Catalog;
import com.github.greenfinger.test.WebCrawlerService;

/**
 * 
 * @Description: CatalogApiController
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@RequestMapping("/api/catalog")
@RestController
public class CatalogApiController {

    @Autowired
    private WebCrawlerService webCrawlerService;

    @Autowired
    private ResourceManager resourceManager;

    @Autowired
    private CatalogAdminService catalogAdminService;

    @GetMapping("/all/cats")
    public ApiResult<List<String>> getCatList() {
        return ApiResult.ok(resourceManager.selectAllCats());
    }

    @PostMapping("/{id}/delete")
    public ApiResult<String> deleteCatalog(@PathVariable("id") Long catalogId) {
        catalogAdminService.deleteCatalog(catalogId, false);
        return ApiResult.ok("Waiting for delete operation completion.");
    }

    @PostMapping("/{id}/clean")
    public ApiResult<String> cleanCatalog(@PathVariable("id") Long catalogId) {
        catalogAdminService.cleanCatalog(catalogId, false);
        return ApiResult.ok("Waiting for clean operation completion.");
    }

    @PostMapping("/{id}/rebuild")
    public ApiResult<String> rebuild(@PathVariable("id") Long catalogId) {
        webCrawlerService.rebuild(catalogId);
        return ApiResult.ok("Crawling Task will be triggered soon.");
    }

    @PostMapping("/{id}/crawl")
    public ApiResult<String> crawl(@PathVariable("id") Long catalogId) {
        webCrawlerService.crawl(catalogId, true);
        return ApiResult.ok("Crawling Task will be triggered soon.");
    }

    @PostMapping("/{id}/update")
    public ApiResult<String> update(@PathVariable("id") Long catalogId) {
        webCrawlerService.update(catalogId, true);
        return ApiResult.ok("Crawling Task will be triggered soon.");
    }

    @PostMapping("/save")
    public ApiResult<String> saveCatalog(@RequestBody Catalog catalog) {
        resourceManager.saveCatalog(catalog);
        return ApiResult.ok("Save Successfully.");
    }

    @PostMapping("/{id}/summary")
    public ApiResult<CatalogSummary> summary(@PathVariable("id") Long catalogId) {
        Catalog catalog = resourceManager.getCatalog(catalogId);
        WebCrawlerExecutionContext executionContext = WebCrawlerExecutionContext.get(catalogId);
        return ApiResult.ok(new CatalogSummary(catalog, executionContext.getDashboardData()));
    }

    @PostMapping("/{id}/run")
    public ApiResult<Boolean> isRunning(@PathVariable("id") Long catalogId) {
        WebCrawlerExecutionContext context = WebCrawlerExecutionContext.get(catalogId);
        return ApiResult.ok(!context.getDashboardData().isCompleted());
    }

    @PostMapping("/{id}/stop")
    public ApiResult<String> stop(@PathVariable("id") Long catalogId, Model ui) {
        WebCrawlerExecutionContext context = WebCrawlerExecutionContext.get(catalogId);
        context.getDashboardData().setCompleted(true);
        return ApiResult.ok("Stop Successfully");
    }

    @PostMapping("/list")
    public ApiResult<PageBean<CatalogInfo>> selectForCatalog(@RequestBody Catalog example,
            @RequestParam(value = "page", defaultValue = "1", required = false) int page,
            @CookieValue(value = "DATA_LIST_SIZE", required = false, defaultValue = "10") int size)
            throws Exception {
        PageResponse<CatalogInfo> pageResponse =
                resourceManager.pageForCatalog(example, page, size);
        return ApiResult.ok(PageBean.wrap(pageResponse));
    }

}
