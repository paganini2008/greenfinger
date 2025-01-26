package com.github.greenfinger.api;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.github.doodler.common.ApiResult;
import com.github.doodler.common.page.PageVo;
import com.github.greenfinger.CatalogAdminService;
import com.github.greenfinger.CatalogDetails;
import com.github.greenfinger.CatalogDetailsService;
import com.github.greenfinger.ResourceManager;
import com.github.greenfinger.WebCrawlerExecutionContext;
import com.github.greenfinger.WebCrawlerExecutionContextUtils;
import com.github.greenfinger.WebCrawlerJobService;
import com.github.greenfinger.api.pojo.CatalogInfo;
import com.github.greenfinger.api.pojo.CatalogSummary;
import com.github.greenfinger.components.Dashboard;
import com.github.greenfinger.components.DashboardFactory;
import com.github.greenfinger.model.Catalog;

/**
 * 
 * @Description: CatalogApiController
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@RequestMapping("/v1/catalog")
@RestController
public class CatalogApiController {

    @Autowired
    private WebCrawlerJobService webCrawlerJobService;

    @Autowired
    private ResourceManager resourceManager;

    @Autowired
    private CatalogAdminService catalogAdminService;

    @Autowired
    private CatalogDetailsService catalogDetailsService;

    @Autowired
    private DashboardFactory dashboardFactory;

    @GetMapping("/all/cats")
    public ApiResult<List<String>> getCatList() {
        return ApiResult.ok(resourceManager.selectAllCats());
    }

    @GetMapping("/{id}")
    public ApiResult<Catalog> getCatalog(@PathVariable("id") Long catalogId) {
        Catalog catalog = resourceManager.getCatalog(catalogId);
        return ApiResult.ok(catalog);
    }

    @DeleteMapping("/{id}")
    public ApiResult<String> deleteCatalog(@PathVariable("id") Long catalogId) {
        catalogAdminService.deleteCatalog(catalogId, false);
        return ApiResult.ok("Waiting for delete operation completion.");
    }

    @DeleteMapping("/{id}/clean")
    public ApiResult<String> cleanCatalog(@PathVariable("id") Long catalogId) {
        catalogAdminService.cleanCatalog(catalogId, false);
        return ApiResult.ok("Waiting for clean operation completion.");
    }

    @PostMapping("/{id}/rebuild")
    public ApiResult<String> rebuild(@PathVariable("id") Long catalogId) throws Exception {
        webCrawlerJobService.rebuild(catalogId);
        return ApiResult.ok("Crawling Task will be triggered soon.");
    }

    @PostMapping("/{id}/crawl")
    public ApiResult<String> crawl(@PathVariable("id") Long catalogId) throws Exception {
        webCrawlerJobService.crawl(catalogId);
        return ApiResult.ok("Crawling Task will be triggered soon.");
    }

    @PostMapping("/{id}/interupt")
    public ApiResult<String> interupt(@PathVariable("id") Long catalogId) throws Exception {
        WebCrawlerExecutionContext executionContext =
                WebCrawlerExecutionContextUtils.get(catalogId, false);
        if (executionContext != null) {
            executionContext.getGlobalStateManager().setCompleted(true);
        }
        return ApiResult.ok("Crawling Task will be triggered soon.");
    }

    @PostMapping("/{id}/finish")
    public ApiResult<String> finish(@PathVariable("id") Long catalogId) throws Exception {
        webCrawlerJobService.finish(catalogId);
        return ApiResult.ok("Crawling Task will be finish soon.");
    }

    @PostMapping("/save")
    public ApiResult<String> saveCatalog(@RequestBody Catalog catalog) {
        resourceManager.saveCatalog(catalog);
        return ApiResult.ok("Save Successfully.");
    }

    @GetMapping("/{id}/summary")
    public ApiResult<CatalogSummary> summary(@PathVariable("id") Long catalogId) throws Exception {
        CatalogDetails catalogDetails = catalogDetailsService.loadCatalogDetails(catalogId);
        WebCrawlerExecutionContext executionContext =
                WebCrawlerExecutionContextUtils.get(catalogDetails.getId(), false);
        if (executionContext != null) {
            return ApiResult.ok(
                    new CatalogSummary(executionContext.getGlobalStateManager().getDashboard()));
        }
        Dashboard snapshot = dashboardFactory.getReadyonlyDashboard(catalogDetails);
        return ApiResult.ok(new CatalogSummary(snapshot));
    }

    @GetMapping("/{id}/running")
    public ApiResult<Boolean> isRunning(@PathVariable("id") Long catalogId) {
        WebCrawlerExecutionContext context = WebCrawlerExecutionContextUtils.get(catalogId);
        return context != null ? ApiResult.ok(!context.getGlobalStateManager().isCompleted())
                : ApiResult.ok(false);
    }

    @PostMapping("/{id}/stop")
    public ApiResult<Boolean> stop(@PathVariable("id") Long catalogId) {
        WebCrawlerExecutionContext context = WebCrawlerExecutionContextUtils.get(catalogId, false);
        if (context != null) {
            context.getGlobalStateManager().setCompleted(true);
            return ApiResult.ok(context.getGlobalStateManager().isCompleted());
        }
        return ApiResult.ok(false);
    }

    @PostMapping("/list")
    public ApiResult<PageVo<CatalogInfo>> selectForCatalog(@RequestBody Catalog example,
            @RequestParam(value = "page", defaultValue = "1", required = false) int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize)
            throws Exception {
        PageVo<CatalogInfo> pageVo = resourceManager.pageForCatalog(example, page, pageSize);
        return ApiResult.ok(pageVo);
    }

}
