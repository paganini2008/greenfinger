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

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsService;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.output.SearchRequest;
import com.github.greenfinger.core.output.SearchResponse;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.core.output.Searcher;
import com.github.greenfinger.output.vector.VectorHit;
import com.github.greenfinger.service.CatalogAdminService;
import lombok.RequiredArgsConstructor;

/**
 * Searching what was crawled: by words, by meaning, and by describing a picture.
 *
 * <p>
 * Reads the index and the vector store only. The database is never consulted here, which is what
 * lets the catalog tables be emptied without search noticing. Which versions are visible comes
 * from each catalog's published {@code search_version}, so a rebuild in progress stays invisible
 * until it finishes.
 *
 * @Description: SearchApiController
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@RestController
@RequestMapping("${greenfinger.api.prefix:/v2}/search")
@RequiredArgsConstructor
public class SearchApiController {

    /** Past this, a better query beats a deeper page. */
    private static final int MAX_OFFSET = 1000;

    private final CatalogAdminService catalogAdminService;
    private final CatalogDetailsService catalogDetailsService;
    private final OutputProperties outputProperties;
    private final OutputFactory outputFactory;
    private final VectorSearchSupport vectorSearchSupport;

    /**
     * Keyword search.
     *
     * <p>
     * {@code cursor} is what a front end pages with beyond the ten thousandth result: it comes
     * back on every response, and feeding it to the next call resumes exactly where the last page
     * stopped. Plain {@code page} works too, up to the point Elasticsearch refuses it.
     */
    @GetMapping
    public ApiResult<SearchResponse> search(@RequestParam("q") String keyword,
            @RequestParam(value = "catalog", required = false) String catalogRef,
            @RequestParam(value = "cat", required = false) String cat,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "cursor", required = false) List<Object> cursor)
            throws Exception {
        List<String> versions = searchableVersions(catalogRef);
        if (versions.isEmpty()) {
            return ApiResult.failed("Nothing has finished crawling yet");
        }
        Searcher searcher = outputFactory.getSearcher();
        return ApiResult.ok(searcher.search(SearchRequest.builder().keyword(keyword).cat(cat)
                .catalogVersions(versions).page(page).pageSize(size).cursor(cursor).build()));
    }

    /**
     * Meaning rather than words. Finds the page that answers the question even when it never uses
     * the words the question was asked in.
     */
    @GetMapping("/semantic")
    public ApiResult<List<VectorHit>> semantic(@RequestParam("q") String keyword,
            @RequestParam(value = "catalog", required = false) String catalogRef,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "offset", defaultValue = "0") int offset) throws Exception {
        List<String> versions = searchableVersions(catalogRef);
        if (versions.isEmpty()) {
            return ApiResult.failed("Nothing has finished crawling yet");
        }
        return ApiResult.ok(vectorSearchSupport.getVectorSearcher().searchText(keyword, versions,
                size, capped(offset), true));
    }

    /**
     * Pictures found by describing them. Needs an embedding provider that does images; one that
     * does not says so rather than returning something meaningless.
     */
    @GetMapping("/images")
    public ApiResult<List<VectorHit>> images(@RequestParam("q") String keyword,
            @RequestParam(value = "catalog", required = false) String catalogRef,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "offset", defaultValue = "0") int offset) throws Exception {
        List<String> versions = searchableVersions(catalogRef);
        if (versions.isEmpty()) {
            return ApiResult.failed("Nothing has finished crawling yet");
        }
        try {
            return ApiResult
                    .ok(vectorSearchSupport.getVectorSearcher().searchImages(keyword, versions,
                            size, capped(offset)));
        } catch (UnsupportedOperationException e) {
            return ApiResult.failed(e.getMessage());
        }
    }

    /**
     * The published version of each catalog, as the {@code <catalogId>:<version>} pairs both
     * stores filter on.
     */
    /**
     * How deep a vector page may go.
     *
     * <p>
     * A vector store has to walk the whole offset to answer, so an unbounded one turns a url into a
     * way to make the server work arbitrarily hard. A thousand results is far past the point where
     * a different query is the better move, which is what the front end says when it stops offering
     * another page.
     */
    private static int capped(int offset) {
        return Math.max(0, Math.min(offset, MAX_OFFSET));
    }

    private List<String> searchableVersions(String catalogRef) {
        List<Catalog> catalogs = StringUtils.isNotBlank(catalogRef)
                ? List.of(catalogAdminService.require(catalogRef))
                : catalogAdminService.findAll();
        List<String> versions = new ArrayList<>();
        for (Catalog catalog : catalogs) {
            CatalogDetails details = catalogDetailsService.loadCatalogDetails(catalog.getId());
            if (details.getSearchVersion() >= 0) {
                versions.add(details.getId() + ":" + details.getSearchVersion());
            }
        }
        return versions;
    }

}
