/**
* Copyright 2017-2022 Fred Feng (paganini.fy@gmail.com)

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
package io.atlantisframework.greenfinger.console.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.github.paganini2008.devtools.jdbc.PageBean;
import com.github.paganini2008.devtools.jdbc.PageResponse;

import io.atlantisframework.greenfinger.es.ResourceIndexService;
import io.atlantisframework.greenfinger.es.SearchResult;

/**
 * 
 * IndexSearcherController
 *
 * @author Fred Feng
 * @since 2.0.1
 */
@RequestMapping("/index/searcher")
@Controller
public class IndexSearcherController {

	@Autowired
	private ResourceIndexService resourceIndexService;

	@GetMapping("/")
	public String index(Model ui) {
		return "searcher/search";
	}

	@PostMapping("/search")
	public String search(@RequestParam("q") String keyword, @RequestParam(name = "cat", required = false) String cat,
			@RequestParam(name = "version", required = false) Integer version,
			@RequestParam(value = "page", defaultValue = "1", required = false) int page,
			@CookieValue(value = "DATA_LIST_SIZE", required = false, defaultValue = "10") int size, Model ui) {
		PageResponse<SearchResult> pageResponse = resourceIndexService.search(cat, keyword, version, page, size);
		PageBean<SearchResult> pageBean = PageBean.wrap(pageResponse);
		ui.addAttribute("page", pageBean);
		return "searcher/search_result";
	}

}
