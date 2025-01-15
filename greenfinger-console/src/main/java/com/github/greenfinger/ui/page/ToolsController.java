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

import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.github.doodler.common.ApiResult;
import com.github.greenfinger.components.RedissionBloomUrlPathFilter;

/**
 * 
 * @Description: ToolsController
 * @Author: Fred Feng
 * @Date: 12/01/2025
 * @Version 1.0.0
 */
@RequestMapping("/test")
@RestController
public class ToolsController {

    @Autowired
    private RedissonClient redissonClient;

    @GetMapping("/exists")
    public ApiResult<String> testExists(@RequestParam("url") String url) throws Exception {
        RedissionBloomUrlPathFilter redissionBloomUrlPathFilter =
                new RedissionBloomUrlPathFilter("test:bloomfilter", redissonClient);
        redissionBloomUrlPathFilter.afterPropertiesSet();
        return ApiResult
                .ok(redissionBloomUrlPathFilter.mightExist(url) ? "existed" : "not existed");
    }

}
