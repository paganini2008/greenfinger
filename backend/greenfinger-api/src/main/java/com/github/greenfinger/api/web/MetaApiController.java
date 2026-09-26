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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import org.springframework.core.env.Environment;
import com.sun.management.OperatingSystemMXBean;
import com.github.greenfinger.output.OutputProperties;
import lombok.RequiredArgsConstructor;

/**
 * What is running here. {@code /version} is deliberately open -- the login page shows it, and a
 * version number is not a secret. The number comes from build info, falling back to the jar
 * manifest when the host application generates none.
 *
 * @Description: MetaApiController
 * @Author: Fred Feng
 * @Date: 31/08/2026
 * @Version 2.0.0
 */
@ApiEndpoint
@RequestMapping("${greenfinger.api.prefix:/v2}")
@RequiredArgsConstructor
public class MetaApiController {

    private final ObjectProvider<BuildProperties> buildProperties;
    private final ObjectProvider<OutputProperties> outputProperties;
    private final Environment environment;

    @GetMapping("/version")
    public ApiResult<Map<String, Object>> version() {
        Map<String, Object> version = new LinkedHashMap<>();
        version.put("name", "Greenfinger");
        BuildProperties build = buildProperties.getIfAvailable();
        version.put("version", build != null ? build.getVersion() : fromManifest());
        if (build != null) {
            version.put("builtAt", build.getTime());
        }
        // Which profile is in force, so the page can say it out loud. Somebody with two tabs open
        // is one careless click away from running dev's delete against prod, and nothing else on
        // the page distinguishes them. Spring reports no active profile when none was named, and
        // what runs then is the default one.
        String[] active = environment.getActiveProfiles();
        version.put("profiles",
                List.of(active.length > 0 ? active : environment.getDefaultProfiles()));
        return ApiResult.ok(version);
    }

    /**
     * Which store is behind each of the three outputs, so the page can name them instead of
     * hard-coding names. Authenticated, unlike {@code /version}: which stores an installation runs
     * is not for anybody who can reach the port.
     */
    @GetMapping("/outputs")
    public ApiResult<Map<String, String>> outputs() {
        OutputProperties outputs = outputProperties.getObject();
        Map<String, String> where = new LinkedHashMap<>();
        where.put("file", outputs.getFile().getTarget());
        where.put("index", outputs.getIndex().getProvider());
        where.put("vector", outputs.getVector().getStore());
        return ApiResult.ok(where);
    }

    /**
     * Heap and cpu for this node. Not the actuator: {@code metrics} stays unexposed in production
     * because listing it hands out every url pattern served. Read straight from the jvm so it works
     * without a metrics registry. Answers for whichever node received the request ({@code ?__node=}).
     */
    @GetMapping("/node")
    public ApiResult<Map<String, Object>> node() {
        Map<String, Object> reading = new LinkedHashMap<>();
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        reading.put("heapUsed", heap.getUsed());
        // -1 when the jvm was not given a maximum, which the page shows as "--" rather than as a
        // bar that is always empty
        reading.put("heapMax", heap.getMax());
        reading.put("cpu", processCpu());
        return ApiResult.ok(reading);
    }

    /**
     * How much of one core this process is using, 0 to 1, or -1 where the platform will not say.
     * The portable bean does not expose it; the Sun one does and is what Micrometer reads too.
     */
    private double processCpu() {
        // `var`, because the two OperatingSystemMXBean interfaces have the same simple name: the
        // portable one is what the factory returns and the imported one is the hotspot extension
        // that actually carries the reading
        var os = ManagementFactory.getOperatingSystemMXBean();
        return os instanceof OperatingSystemMXBean sun ? sun.getProcessCpuLoad() : -1d;
    }

    private String fromManifest() {
        return Optional.ofNullable(getClass().getPackage().getImplementationVersion())
                .orElse("unknown");
    }

}
