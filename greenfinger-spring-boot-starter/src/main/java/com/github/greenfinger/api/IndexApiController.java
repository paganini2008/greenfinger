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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * 
 * @Description: IndexApiController
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@Api(tags = "Index Management API")
@RequestMapping("/v1/index")
@RestController
public class IndexApiController {

    @Autowired
    private ResourceIndexManager resourceIndexManager;

    @ApiOperation(value = "Recreate all catalog indexes", notes = "Recreate all catalog indexes")
    @WebCrawling
    @PostMapping("/sync")
    public ApiResult<String> recreateCatalogIndex() throws Exception {
        resourceIndexManager.recreateCatalogIndex();
        return ApiResult.ok("Recreate Catalog Index Successfully.");
    }

    @ApiOperation(value = "Recreate catalog indexes by id",
            notes = "Recreate catalog indexes by id")
    @WebCrawling
    @PostMapping("/{id}/sync")
    public ApiResult<String> recreateCatalogIndex(@PathVariable("id") Long catalogId)
            throws Exception {
        resourceIndexManager.recreateCatalogIndex(catalogId);
        return ApiResult.ok("Recreate Catalog Index Successfully.");
    }

    @ApiOperation(value = "Upgrade all catalog indexes", notes = "Upgrade all catalog indexes")
    @WebCrawling
    @PutMapping("/upgrade")
    public ApiResult<String> upgradeCatalogIndex() throws Exception {
        resourceIndexManager.upgradeCatalogIndex();
        return ApiResult.ok("Upgrade Catalog Index Successfully.");
    }

    @ApiOperation(value = "Upgrade catalog indexes by catalogId",
            notes = "Upgrade catalog indexes by catalogId")
    @WebCrawling
    @PutMapping("/{id}/upgrade")
    public ApiResult<String> upgradeCatalogIndex(@PathVariable("id") Long catalogId)
            throws Exception {
        resourceIndexManager.upgradeCatalogIndex(catalogId);
        return ApiResult.ok("Upgrade Catalog Index Successfully.");
    }

    @ApiOperation(value = "Delete catalog indexes by id", notes = "Delete catalog indexes by id")
    @WebCrawling
    @DeleteMapping("/{id}")
    public ApiResult<String> deleteResource(@PathVariable("id") Long catalogId,
            @RequestParam(name = "version", defaultValue = "-1", required = false) int version) {
        resourceIndexManager.deleteResource(catalogId, version);
        return ApiResult.ok("Delete Catalog Index Successfully.");
    }

}
