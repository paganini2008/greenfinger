/**
 * Copyright 2017-2022 Fred Feng (paganini.fy@gmail.com)
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.github.greenfinger.utils.Extractor;

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
    private Extractor pageExtractor;

    // @Autowired
    // private PathFilterFactory pathFilterFactory;

    @GetMapping("/echo")
    public String echo(@RequestParam("url") String url) throws Exception {
        return pageExtractor.extractHtml("", url,
                com.github.doodler.common.utils.CharsetUtils.UTF_8, null);
    }
    //
    // @GetMapping("/testExists")
    // public Map<String, Object> testExists(@RequestParam("url") String url) {
    // ExistingUrlPathFilter pathFilter = pathFilterFactory.getPathFilter(Long.MAX_VALUE);
    // Map<String, Object> data = new HashMap<String, Object>();
    // data.put("exists", pathFilter.mightExist(url));
    // return data;
    // }

}
