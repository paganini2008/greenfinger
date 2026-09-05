/*
 * Copyright 2017-2026 Fred Feng (paganini.fy@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.greenfinger.api.web;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsService;
import com.github.greenfinger.core.engine.CrawlRegistry;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.service.CatalogAdminService;
import lombok.RequiredArgsConstructor;

/**
 * Managing crawl definitions, the same operations the command line offers.
 * 
 * @Description: CatalogApiController
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@RestController
@RequestMapping("${greenfinger.api.prefix:/v2}/catalog")
@RequiredArgsConstructor
public class CatalogApiController {

    private final CatalogAdminService catalogAdminService;
    private final CatalogDetailsService catalogDetailsService;
    private final CrawlRegistry crawlRegistry;

    @GetMapping
    public ApiResult<List<Catalog>> list() {
        return ApiResult.ok(catalogAdminService.findAll());
    }

    @GetMapping("/cats")
    public ApiResult<List<String>> categories() {
        return ApiResult.ok(catalogAdminService.findAllCategories());
    }

    @GetMapping("/running")
    public ApiResult<List<Catalog>> running() {
        return ApiResult.ok(catalogAdminService.findRunning());
    }

    @GetMapping("/{idOrName}")
    public ApiResult<Catalog> get(@PathVariable("idOrName") String idOrName) {
        return ApiResult.ok(catalogAdminService.require(idOrName));
    }

    /**
     * Everything the runtime will actually use, with the defaults already applied -- which is not
     * the same as the stored row, and is what a form should show.
     */
    @GetMapping("/{idOrName}/details")
    public ApiResult<CatalogDetails> details(@PathVariable("idOrName") String idOrName) {
        Catalog catalog = catalogAdminService.require(idOrName);
        return ApiResult.ok(catalogDetailsService.loadCatalogDetails(catalog.getId()));
    }

    /**
     * The counters the Monitor page draws: the live ones while a crawl is going, and the last
     * run's otherwise. Same shape either way, so the page does not go blank the moment a crawl
     * finishes under it.
     */
    @GetMapping("/{idOrName}/summary")
    public ApiResult<CatalogSummary> summary(@PathVariable("idOrName") String idOrName) {
        Catalog catalog = catalogAdminService.require(idOrName);
        CatalogDetails details = catalogDetailsService.loadCatalogDetails(catalog.getId());
        Optional<CatalogSummary> live = crawlRegistry.getDashboard(catalog.getId())
                .map(dashboard -> new CatalogSummary(dashboard, details));
        if (live.isPresent()) {
            return ApiResult.ok(live.get());
        }
        Map<String, Object> settings = catalogAdminService.readLastRun(details).orElse(Map.of());
        return ApiResult.ok(new CatalogSummary(details, settings));
    }

    @GetMapping("/{idOrName}/running")
    public ApiResult<Boolean> running(@PathVariable("idOrName") String idOrName) {
        Catalog catalog = catalogAdminService.require(idOrName);
        return ApiResult.ok(crawlRegistry.isRunning(catalog.getId()));
    }

    @PostMapping
    public ApiResult<Catalog> save(@RequestBody Catalog catalog) {
        return ApiResult.ok(catalogAdminService.save(catalog));
    }

    /**
     * Removes the definition only. What it crawled is removed through the delete endpoint, which
     * says which stores to touch.
     */
    @DeleteMapping("/{idOrName}")
    public ApiResult<Boolean> delete(@PathVariable("idOrName") String idOrName) {
        Catalog catalog = catalogAdminService.require(idOrName);
        return ApiResult.ok(catalogAdminService.delete(catalog.getId()));
    }

}
