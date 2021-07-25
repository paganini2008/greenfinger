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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.paganini2008.devtools.jdbc.PageResponse;

import indi.atlantis.framework.greenfinger.CrawlerLauncher;
import indi.atlantis.framework.greenfinger.CrawlerSummary;
import indi.atlantis.framework.greenfinger.ResourceManager;
import indi.atlantis.framework.greenfinger.CrawlerSummary.Summary;
import indi.atlantis.framework.greenfinger.console.utils.PageBean;
import indi.atlantis.framework.greenfinger.console.utils.Response;
import indi.atlantis.framework.greenfinger.es.IndexedResourceService;
import indi.atlantis.framework.greenfinger.model.Catalog;

/**
 * 
 * CrawlerController
 *
 * @author Fred Feng
 * @since 1.0
 */
@RequestMapping("/catalog")
@RestController
public class CatalogController {

	@Autowired
	private CrawlerLauncher crawlerLauncher;

	@Autowired
	private ResourceManager resourceManager;

	@Autowired
	private IndexedResourceService indexedResourceService;

	@Autowired
	private CrawlerSummary crawlerSummary;

	@GetMapping("/{id}/delete")
	public Response deleteCatalog(@PathVariable("id") Long catalogId,
			@RequestParam(name = "cascade", required = false, defaultValue = "false") boolean cascade) {
		if (cascade) {
			indexedResourceService.deleteResource(catalogId, 0);
			resourceManager.deleteResourceByCatalogId(catalogId);
		}
		resourceManager.deleteCatalog(catalogId);
		return Response.success("Delete OK.");
	}

	@PostMapping("/{id}/clean")
	public Response cleanCatalog(@PathVariable("id") Long catalogId) {
		indexedResourceService.deleteResource(catalogId, 0);
		resourceManager.deleteResourceByCatalogId(catalogId);
		return Response.success("Clean OK.");
	}

	@PostMapping("/{id}/rebuild")
	public Response rebuild(@PathVariable("id") Long catalogId) {
		crawlerLauncher.rebuild(catalogId);
		return Response.success("Crawling Job will be triggered soon.");
	}

	@PostMapping("/{id}/crawl")
	public Response crawl(@PathVariable("id") Long catalogId) {
		crawlerLauncher.submit(catalogId);
		return Response.success("Crawling Job will be triggered soon.");
	}

	@PostMapping("/{id}/update")
	public Response update(@PathVariable("id") Long catalogId) {
		crawlerLauncher.update(catalogId);
		return Response.success("Crawling Job will be triggered soon.");
	}

	@PostMapping("/save")
	public Response saveCatalog(@RequestBody Catalog catalog) {
		resourceManager.saveCatalog(catalog);
		return Response.success("Save OK.");
	}

	@GetMapping("/{id}/summary")
	public Response summary(@PathVariable("id") Long catalogId) {
		Summary summary = crawlerSummary.getSummary(catalogId);
		return Response.success(summary);
	}

	@GetMapping("/query")
	public Response queryForCatalog(@RequestParam(value = "page", defaultValue = "1", required = false) int page,
			@CookieValue(value = "DATA_LIST_SIZE", required = false, defaultValue = "10") int size) {
		PageResponse<Catalog> pageResponse = resourceManager.queryForCatalog(page, size);
		return Response.success(PageBean.wrap(pageResponse));
	}

}
