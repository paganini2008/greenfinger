/**
* Copyright 2018-2021 Fred Feng (paganini.fy@gmail.com)

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.github.paganini2008.devtools.multithreads.ThreadUtils;

import indi.atlantis.framework.greenfinger.console.utils.Response;
import indi.atlantis.framework.greenfinger.es.IndexedResourceService;

/**
 * 
 * IndexController
 *
 * @author Fred Feng
 * 
 * @since 1.0
 */
@RequestMapping("/catalog/index")
@RestController
public class IndexController {

	@Autowired
	private IndexedResourceService indexedResourceService;

	@GetMapping("/{id}")
	public Response indexAll(@PathVariable("id") Long catalogId) {
		ThreadUtils.runAsThread(() -> {
			indexedResourceService.indexAll(catalogId, false);
		});
		return Response.success("Submit OK.");
	}

	@GetMapping("/{id}/delete")
	public Response deleteResource(@PathVariable("id") Long catalogId) {
		indexedResourceService.deleteResource(catalogId, 0);
		return Response.success("Submit OK.");
	}

}
