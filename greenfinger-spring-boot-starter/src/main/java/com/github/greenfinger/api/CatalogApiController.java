/*
 * Copyright 2017-2025 Fred Feng (paganini.fy@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * 
 * @Description: CatalogApiController
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@Api(tags = "Catalog Management API")
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

    @ApiOperation(value = "Retrieve all categories", notes = "Retrieve all categories")
    @GetMapping("/cats")
    public ApiResult<List<String>> selectAllCats() {
        return ApiResult.ok(resourceManager.selectAllCats());
    }

    @ApiOperation(value = "Retrieve catalog by id", notes = "Retrieve catalog by id")
    @GetMapping("/{id}")
    public ApiResult<Catalog> getCatalog(@PathVariable("id") Long catalogId) {
        Catalog catalog = resourceManager.getCatalog(catalogId);
        return ApiResult.ok(catalog);
    }

    @ApiOperation(value = "Delete catalog and its data by id",
            notes = "Delete catalog and its data by id")
    @DeleteMapping("/{id}")
    public ApiResult<String> deleteCatalog(@PathVariable("id") Long catalogId) {
        catalogAdminService.deleteCatalog(catalogId, false);
        return ApiResult.ok("Waiting for delete operation completion.");
    }

    @ApiOperation(value = "Clean catalog data by id", notes = "Clean catalog data by id")
    @DeleteMapping("/{id}/clean")
    public ApiResult<String> cleanCatalog(@PathVariable("id") Long catalogId) {
        catalogAdminService.cleanCatalog(catalogId, false);
        return ApiResult.ok("Waiting for clean operation completion.");
    }

    @ApiOperation(value = "Restart catalog webcrawler by id",
            notes = "Restart catalog webcrawler by id")
    @PostMapping("/{id}/rebuild")
    public ApiResult<String> rebuild(@PathVariable("id") Long catalogId) throws Exception {
        webCrawlerJobService.rebuild(catalogId);
        return ApiResult.ok("Crawling Task will be triggered to rebuild soon.");
    }

    @ApiOperation(value = "Start catalog webcrawler by id",
            notes = "Start catalog webcrawler by id")
    @PostMapping("/{id}/crawl")
    public ApiResult<String> crawl(@PathVariable("id") Long catalogId) throws Exception {
        webCrawlerJobService.crawl(catalogId);
        return ApiResult.ok("Crawling Task will be triggered soon.");
    }

    @ApiOperation(value = "Interrupt catalog webcrawler by id",
            notes = "Interrupt catalog webcrawler by id")
    @PostMapping("/{id}/interrupt")
    public ApiResult<String> interrupt(@PathVariable("id") Long catalogId) throws Exception {
        WebCrawlerExecutionContext executionContext =
                WebCrawlerExecutionContextUtils.get(catalogId, false);
        if (executionContext != null) {
            executionContext.getGlobalStateManager().setCompleted(true);
        }
        return ApiResult.ok("Crawling Task will be interrupted soon.");
    }

    @ApiOperation(value = "Save catalog detail", notes = "Save catalog detail")
    @PostMapping("/save")
    public ApiResult<String> saveCatalog(@RequestBody Catalog catalog) {
        resourceManager.saveCatalog(catalog);
        return ApiResult.ok("Save Catalog Successfully.");
    }

    @ApiOperation(value = "Retrieve webcrawler summary", notes = "Retrieve webcrawler summary")
    @GetMapping("/{id}/summary")
    public ApiResult<CatalogSummary> getSummary(@PathVariable("id") Long catalogId)
            throws Exception {
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

    @ApiOperation(value = "Check if the web crawler is running or not",
            notes = "Check if the web crawler is running or not")
    @GetMapping("/{id}/running")
    public ApiResult<Boolean> isRunning(@PathVariable("id") Long catalogId) {
        WebCrawlerExecutionContext context = WebCrawlerExecutionContextUtils.get(catalogId);
        return context != null ? ApiResult.ok(!context.getGlobalStateManager().isCompleted())
                : ApiResult.ok(false);
    }

    @ApiOperation(value = "Paginated query for catalog detail",
            notes = "Paginated query for catalog detail")
    @PostMapping("/list")
    public ApiResult<PageVo<CatalogInfo>> pageForCatalog(@RequestBody Catalog example,
            @RequestParam(value = "page", defaultValue = "1", required = false) int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize)
            throws Exception {
        PageVo<CatalogInfo> pageVo = resourceManager.pageForCatalog(example, page, pageSize);
        return ApiResult.ok(pageVo);
    }

}
