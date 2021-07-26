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

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.paganini2008.devtools.CharsetUtils;

import indi.atlantis.framework.greenfinger.PageExtractor;
import indi.atlantis.framework.greenfinger.PathFilter;
import indi.atlantis.framework.greenfinger.PathFilterFactory;

/**
 * 
 * ToolsController
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
 */
@RestController
public class ToolsController {

	@Autowired
	private PageExtractor pageExtractor;

	@Autowired
	private PathFilterFactory pathFilterFactory;

	@GetMapping("/echo")
	public String echo(@RequestParam("url") String url) throws Exception {
		return pageExtractor.extractHtml("", url, CharsetUtils.UTF_8);
	}

	@GetMapping("/testExists")
	public Map<String, Object> testExists(@RequestParam("url") String url) {
		PathFilter pathFilter = pathFilterFactory.getPathFilter(Long.MAX_VALUE);
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("exists", pathFilter.mightExist(url));
		return data;
	}

}
