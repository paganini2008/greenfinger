/**
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
package com.github.greenfinger.ui.page;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.github.doodler.common.page.PageBean;
import com.github.doodler.common.page.PageResponse;
import com.github.greenfinger.CatalogAdminService;
import com.github.greenfinger.ResourceManager;
import com.github.greenfinger.WebCrawlerExecutionContext;
import com.github.greenfinger.WebCrawlerExecutionContextUtils;
import com.github.greenfinger.WebCrawlerService;
import com.github.greenfinger.api.CatalogInfo;
import com.github.greenfinger.api.CatalogSummary;
import com.github.greenfinger.model.Catalog;

/**
 * 
 * @Description: CatalogController
 * @Author: Fred Feng
 * @Date: 12/01/2025
 * @Version 1.0.0
 */
@RequestMapping("/catalog")
@Controller
public class CatalogController {

    @Autowired
    private WebCrawlerService webCrawlerService;

    @Autowired
    private ResourceManager resourceManager;

    @Autowired
    private CatalogAdminService catalogAdminService;

    @GetMapping("/")
    public String index(Model ui) {
        return "catalog";
    }

    @GetMapping(value = {"/{id}/edit", "/edit"})
    public String edit(@PathVariable(name = "id", required = false) Long catalogId, Model ui) {
        if (catalogId != null) {
            Catalog catalog = resourceManager.getCatalog(catalogId);
            ui.addAttribute("catalog", catalog);
        }
        return "catalog_edit";
    }

    @PostMapping("/save")
    public String stop(@ModelAttribute Catalog catalog) {
        resourceManager.saveCatalog(catalog);
        return "redirect:/catalog/";
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/list")
    public String queryForCatalog(@ModelAttribute Catalog example,
            @RequestParam(value = "page", defaultValue = "1", required = false) int page,
            @CookieValue(value = "DATA_LIST_SIZE", required = false, defaultValue = "10") int size,
            Model ui) throws Exception {
        PageResponse<CatalogInfo> pageResponse =
                resourceManager.pageForCatalog(example, page, size);
        PageBean<CatalogInfo> pageBean = PageBean.wrap(pageResponse);
        List<CatalogSummary> dataList = new ArrayList<CatalogSummary>(size);
        for (CatalogInfo catalogInfo : pageBean.getResults()) {
            WebCrawlerExecutionContext context =
                    WebCrawlerExecutionContextUtils.get(catalogInfo.getId());
            dataList.add(new CatalogSummary(catalogInfo, context.getDashboard()));
        }
        PageBean<CatalogSummary> newPageBean = (PageBean<CatalogSummary>) pageBean.clone();
        newPageBean.setResults(dataList);
        ui.addAttribute("page", newPageBean);
        return "catalog_list";
    }

    @PostMapping("/{id}/delete")
    public String deleteCatalog(@PathVariable("id") Long catalogId) {
        catalogAdminService.deleteCatalog(catalogId, false);
        return "redirect:/catalog/";
    }

    @PostMapping("/{id}/clean")
    public String cleanCatalog(@PathVariable("id") Long catalogId) {
        catalogAdminService.cleanCatalog(catalogId, false);
        return "redirect:/catalog/";
    }

    @PostMapping("/{id}/rebuild")
    public String rebuild(@PathVariable("id") Long catalogId) throws Exception {
        webCrawlerService.rebuild(catalogId);
        return "redirect:/catalog/";
    }

    @PostMapping("/{id}/crawl")
    public String crawl(@PathVariable("id") Long catalogId) throws Exception {
        webCrawlerService.crawl(catalogId, true);
        return "redirect:/catalog/";
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable("id") Long catalogId) throws Exception {
        webCrawlerService.update(catalogId, true);
        return "redirect:/catalog/";
    }

    @GetMapping("/{id}/summary")
    public String summary(@PathVariable("id") Long catalogId, Model ui) {
        ui.addAttribute("catalogId", catalogId);
        return "catalog_index";
    }

    @PostMapping("/{id}/summary/content")
    public String summaryContent(@PathVariable("id") Long catalogId, Model ui) {
        Catalog catalog = resourceManager.getCatalog(catalogId);
        WebCrawlerExecutionContext context = WebCrawlerExecutionContextUtils.get(catalogId);
        ui.addAttribute("summary", new CatalogSummary(catalog, context.getDashboard()));
        return "catalog_index_summary";
    }

    @PostMapping("/{id}/stop")
    public String stop(@PathVariable("id") Long catalogId, Model ui) {
        WebCrawlerExecutionContext context = WebCrawlerExecutionContextUtils.get(catalogId);
        context.getDashboard().setCompleted(true);
        return "redirect:/catalog/";
    }

}
