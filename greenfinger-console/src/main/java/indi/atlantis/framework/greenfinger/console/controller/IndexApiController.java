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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.paganini2008.devtools.multithreads.ThreadUtils;

import indi.atlantis.framework.greenfinger.console.utils.Result;
import indi.atlantis.framework.greenfinger.es.IndexedResourceService;

/**
 * 
 * IndexApiController
 *
 * @author Fred Feng
 *
 * @since 2.0.2
 */
@RequestMapping("/api/index/")
@RestController
public class IndexApiController {

	@Autowired
	private IndexedResourceService indexedResourceService;

	@PostMapping("/all")
	public Result<String> indexAllCatalogs() {
		ThreadUtils.runAsThread(() -> {
			indexedResourceService.indexAllCatalogs();
		});
		return Result.success("Submit OK.");
	}

	@PostMapping("/{id}")
	public Result<String> indexCatalog(@PathVariable("id") Long catalogId) {
		ThreadUtils.runAsThread(() -> {
			indexedResourceService.indexCatalog(catalogId);
		});
		return Result.success("Submit OK.");
	}

	@PostMapping("/upgrade/all")
	public Result<String> upgradeAllCatalogs() {
		ThreadUtils.runAsThread(() -> {
			indexedResourceService.upgradeAllCatalogs();
		});
		return Result.success("Submit OK.");
	}

	@PostMapping("/upgrade/{id}")
	public Result<String> upgradeCatalog(@PathVariable("id") Long catalogId) {
		ThreadUtils.runAsThread(() -> {
			indexedResourceService.upgradeCatalog(catalogId);
		});
		return Result.success("Submit OK.");
	}

	@GetMapping("/{id}/delete")
	public Result<String> deleteResource(@PathVariable("id") Long catalogId,
			@RequestParam(name = "version", defaultValue = "0", required = false) int version) {
		indexedResourceService.deleteResource(catalogId, version);
		return Result.success("Submit OK.");
	}

}
