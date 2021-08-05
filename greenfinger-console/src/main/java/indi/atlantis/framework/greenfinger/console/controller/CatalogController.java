/**
* Copyright 2017-2021 Fred Feng (paganini.fy@gmail.com)

* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package indi.atlantis.framework.greenfinger.console.controller;

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

import com.github.paganini2008.devtools.jdbc.PageResponse;

import indi.atlantis.framework.greenfinger.CatalogAdminService;
import indi.atlantis.framework.greenfinger.CrawlerLauncher;
import indi.atlantis.framework.greenfinger.CrawlerStatistics;
import indi.atlantis.framework.greenfinger.CrawlerStatistics.Summary;
import indi.atlantis.framework.greenfinger.api.CatalogInfo;
import indi.atlantis.framework.greenfinger.api.CatalogSummary;
import indi.atlantis.framework.greenfinger.api.PageBean;
import indi.atlantis.framework.greenfinger.ResourceManager;
import indi.atlantis.framework.greenfinger.model.Catalog;

/**
 * 
 * CatalogController
 *
 * @author Fred Feng
 *
 * @since 2.0.2
 */
@RequestMapping("/catalog")
@Controller
public class CatalogController {

	@Autowired
	private CrawlerLauncher crawlerLauncher;

	@Autowired
	private ResourceManager resourceManager;

	@Autowired
	private CatalogAdminService catalogAdminService;

	@Autowired
	private CrawlerStatistics crawlerStatistics;

	@GetMapping("/")
	public String index(Model ui) {
		return "catalog";
	}

	@GetMapping(value = { "/{id}/edit", "/edit" })
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
			@CookieValue(value = "DATA_LIST_SIZE", required = false, defaultValue = "10") int size, Model ui) {
		PageResponse<CatalogInfo> pageResponse = resourceManager.selectForCatalog(example, page, size);
		PageBean<CatalogInfo> pageBean = PageBean.wrap(pageResponse);
		List<CatalogSummary> dataList = new ArrayList<CatalogSummary>(size);
		for (CatalogInfo catalogInfo : pageBean.getResults()) {
			dataList.add(new CatalogSummary(catalogInfo, crawlerStatistics.getSummary(catalogInfo.getId())));
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
	public String rebuild(@PathVariable("id") Long catalogId) {
		crawlerLauncher.rebuild(catalogId, null);
		return "redirect:/catalog/";
	}

	@PostMapping("/{id}/crawl")
	public String crawl(@PathVariable("id") Long catalogId) {
		crawlerLauncher.submit(catalogId, null);
		return "redirect:/catalog/";
	}

	@PostMapping("/{id}/update")
	public String update(@PathVariable("id") Long catalogId) {
		crawlerLauncher.update(catalogId, null);
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
		Summary summary = crawlerStatistics.getSummary(catalogId);
		ui.addAttribute("summary", new CatalogSummary(catalog, summary));
		return "catalog_index_summary";
	}

	@PostMapping("/{id}/stop")
	public String stop(@PathVariable("id") Long catalogId, Model ui) {
		Summary summary = crawlerStatistics.getSummary(catalogId);
		summary.setCompleted(true);
		return "redirect:/catalog/";
	}

}
