/*
 * Copyright 2017-2026 Fred Feng (paganini.fy@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.greenfinger.api.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

/**
 * What is running here.
 *
 * <p>
 * Open without signing in, on purpose: the login page shows it, and a version number is not a
 * secret -- being told which version refused your password is how you find out you are pointed at
 * the wrong server.
 *
 * <p>
 * The number comes from the build rather than from a constant, so a jar built at 2.1 cannot
 * announce itself as 2.0. When the module is embedded in somebody else's application, which may
 * not generate build info, the jar manifest answers instead.
 *
 * @Description: MetaApiController
 * @Author: Fred Feng
 * @Date: 31/08/2026
 * @Version 2.0.0
 */
@RestController
@RequestMapping("${greenfinger.api.prefix:/v2}")
@RequiredArgsConstructor
public class MetaApiController {

    private final ObjectProvider<BuildProperties> buildProperties;

    @GetMapping("/version")
    public ApiResult<Map<String, Object>> version() {
        Map<String, Object> version = new LinkedHashMap<>();
        version.put("name", "Greenfinger");
        BuildProperties build = buildProperties.getIfAvailable();
        version.put("version", build != null ? build.getVersion() : fromManifest());
        if (build != null) {
            version.put("builtAt", build.getTime());
        }
        return ApiResult.ok(version);
    }

    private String fromManifest() {
        return Optional.ofNullable(getClass().getPackage().getImplementationVersion())
                .orElse("unknown");
    }

}
