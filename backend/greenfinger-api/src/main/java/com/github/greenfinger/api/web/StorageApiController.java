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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.service.CatalogAdminService;
import com.github.greenfinger.service.RocksDbUsageService;
import com.github.greenfinger.service.RocksDbUsageService.RocksDbUsage;
import com.github.greenfinger.service.StorageUsageService;
import com.github.greenfinger.service.StorageUsageService.StorageUsage;
import lombok.RequiredArgsConstructor;

/**
 * How much of the blob store the crawls have taken, and in how many files.
 *
 * <p>
 * Not polled by the front end and it must not be: the answer is a directory walk on local disk
 * and a paged list on MinIO. It is asked when somebody presses refresh.
 *
 * @Description: StorageApiController
 * @Author: Fred Feng
 * @Date: 05/09/2026
 * @Version 2.0.0
 */
@RestController
@RequestMapping("${greenfinger.api.prefix:/v2}/storage")
@RequiredArgsConstructor
public class StorageApiController {

    private final StorageUsageService storageUsageService;
    private final RocksDbUsageService rocksDbUsageService;
    private final CatalogAdminService catalogAdminService;

    @GetMapping
    public ApiResult<StorageUsage> usage(
            @RequestParam(value = "catalogId", required = false) String catalogId) {
        return ApiResult.ok(storageUsageService.usage(catalogId));
    }

    /**
     * The crawl's own state rather than its output: the frontier and the two dedup filters.
     *
     * <p>
     * Per catalog because that is how they are laid out and how they are deleted. The key counts
     * are missing while a crawl of this catalog is running -- RocksDB lets one process hold a
     * store -- and the response says so rather than reporting a zero somebody would believe.
     */
    @GetMapping("/rocksdb")
    public ApiResult<RocksDbUsage> rocksDb(@RequestParam("catalogId") String idOrName,
            @RequestParam(value = "version", required = false) Integer version) {
        Catalog catalog = catalogAdminService.require(idOrName);
        return ApiResult.ok(rocksDbUsageService.usage(catalog.getId(), version));
    }

}
