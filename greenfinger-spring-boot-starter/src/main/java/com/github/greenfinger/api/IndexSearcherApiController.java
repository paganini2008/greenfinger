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
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.github.doodler.common.ApiResult;
import com.github.doodler.common.page.PageResponse;
import com.github.doodler.common.page.PageVo;
import com.github.greenfinger.searcher.ResourceIndexManager;
import com.github.greenfinger.searcher.SearchResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

/**
 * 
 * @Description: IndexSearcherApiController
 * @Author: Fred Feng
 * @Date: 18/01/2025
 * @Version 1.0.0
 */
@Api(tags = "Index Searcher API")
@RequestMapping("/v1/query")
@RestController
public class IndexSearcherApiController {

    @Autowired
    private ResourceIndexManager resourceIndexManager;

    @ApiOperation(value = "Index search by keyword", notes = "Index search by keyword")
    @GetMapping("/")
    public ApiResult<PageVo<SearchResult>> search(
            @ApiParam(name = "keyword", required = false) @RequestParam("q") String keyword,
            @ApiParam(name = "category", required = false) @RequestParam(name = "c",
                    required = false) String cat,
            @ApiParam(name = "version", required = false) @RequestParam(name = "v",
                    required = false, defaultValue = "-1") int version,
            @RequestParam(value = "page", defaultValue = "1", required = false) int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") int size,
            Model ui) throws Exception {
        PageResponse<SearchResult> pageResponse =
                resourceIndexManager.search(cat, keyword, version, page, size);
        PageVo<SearchResult> pageBean = PageVo.wrap(pageResponse);
        return ApiResult.ok(pageBean);
    }
}
