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
package indi.atlantis.framework.greenfinger.api;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.paganini2008.devtools.jdbc.PageResponse;

import indi.atlantis.framework.greenfinger.CatalogAdminService;
import indi.atlantis.framework.greenfinger.CrawlerLauncher;
import indi.atlantis.framework.greenfinger.CrawlerStatistics;
import indi.atlantis.framework.greenfinger.CrawlerStatistics.Summary;
import indi.atlantis.framework.greenfinger.job.CatalogIndexJobInfo;
import indi.atlantis.framework.greenfinger.job.CatalogIndexJobService;
import indi.atlantis.framework.greenfinger.ResourceManager;
import indi.atlantis.framework.greenfinger.model.Catalog;

/**
 * 
 * CatalogApiController
 *
 * @author Fred Feng
 * @since 2.0.1
 */
@RequestMapping("/api/catalog")
@RestController
public class CatalogApiController {

	@Autowired
	private CrawlerLauncher crawlerLauncher;

	@Autowired
	private ResourceManager resourceManager;

	@Autowired
	private CrawlerStatistics crawlerStatistics;

	@Autowired
	private CatalogAdminService catalogAdminService;

	@Autowired(required = false)
	private CatalogIndexJobService catalogIndexJobService;

	@GetMapping("/all/cats")
	public Result<List<String>> getCatList() {
		return Result.success(resourceManager.selectAllCats());
	}

	@PostMapping("/{id}/delete")
	public Result<String> deleteCatalog(@PathVariable("id") Long catalogId) {
		catalogAdminService.deleteCatalog(catalogId, false);
		return Result.success("Waiting for delete operation completion.");
	}

	@PostMapping("/{id}/clean")
	public Result<String> cleanCatalog(@PathVariable("id") Long catalogId) {
		catalogAdminService.cleanCatalog(catalogId, false);
		return Result.success("Waiting for clean operation completion.");
	}

	@PostMapping("/{id}/rebuild")
	public Result<String> rebuild(@PathVariable("id") Long catalogId) {
		crawlerLauncher.rebuild(catalogId, null);
		return Result.success("Crawling Task will be triggered soon.");
	}

	@PostMapping("/{id}/crawl")
	public Result<String> crawl(@PathVariable("id") Long catalogId) {
		crawlerLauncher.submit(catalogId, null);
		return Result.success("Crawling Task will be triggered soon.");
	}

	@PostMapping("/{id}/update")
	public Result<String> update(@PathVariable("id") Long catalogId) {
		crawlerLauncher.update(catalogId, null);
		return Result.success("Crawling Task will be triggered soon.");
	}

	@PostMapping("/save")
	public Result<String> saveCatalog(@RequestBody Catalog catalog) {
		resourceManager.saveCatalog(catalog);
		return Result.success("Save Successfully.");
	}

	@PostMapping("/{id}/summary")
	public Result<CatalogSummary> summary(@PathVariable("id") Long catalogId) {
		Catalog catalog = resourceManager.getCatalog(catalogId);
		Summary summary = crawlerStatistics.getSummary(catalogId);
		return Result.success(new CatalogSummary(catalog, summary));
	}

	@PostMapping("/{id}/run")
	public Result<Boolean> isRunning(@PathVariable("id") Long catalogId) {
		Summary summary = crawlerStatistics.getSummary(catalogId);
		return Result.success(!summary.isCompleted());
	}

	@PostMapping("/{id}/stop")
	public Result<String> stop(@PathVariable("id") Long catalogId, Model ui) {
		Summary summary = crawlerStatistics.getSummary(catalogId);
		summary.setCompleted(true);
		return Result.success("Stop Successfully");
	}

	@PostMapping("/list")
	public Result<PageBean<CatalogInfo>> selectForCatalog(@RequestBody Catalog example,
			@RequestParam(value = "page", defaultValue = "1", required = false) int page,
			@CookieValue(value = "DATA_LIST_SIZE", required = false, defaultValue = "10") int size) {
		PageResponse<CatalogInfo> pageResponse = resourceManager.selectForCatalog(example, page, size);
		return Result.success(PageBean.wrap(pageResponse));
	}

	@PostMapping("/createIndexJob")
	public Result<Integer> createIndexJob(CatalogIndexJobInfo jobInfo) throws Exception {
		if (catalogIndexJobService == null) {
			throw new UnsupportedOperationException("createIndexJob");
		}
		return Result.success(catalogIndexJobService.createIndexJob(jobInfo));
	}

	@PostMapping("/createIndexUpgradeJob")
	public Result<Integer> createIndexUpgradeJob(CatalogIndexJobInfo jobInfo) throws Exception {
		if (catalogIndexJobService == null) {
			throw new UnsupportedOperationException("createIndexUpgradeJob");
		}
		return Result.success(catalogIndexJobService.createIndexUpgradeJob(jobInfo));
	}

}
