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

import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import lombok.extern.slf4j.Slf4j;

/**
 * Serves the front end, when there is one beside the jar.
 *
 * <p>
 * The Angular build is a single page application: /catalogs and /search are routes it handles in
 * the browser, not files on disk. A reload on either would be a 404 without this, so anything that
 * is not a real file and not an api call is answered with index.html and the router takes it from
 * there.
 *
 * <p>
 * The build is looked for in {@code ./static} beside the launcher as well as on the classpath, so
 * the ui can be replaced without rebuilding the jar -- the same reason the configuration lives in
 * {@code deploy/config} rather than inside it.
 *
 * @Description: SinglePageAppConfiguration
 * @Author: Fred Feng
 * @Date: 31/08/2026
 * @Version 2.0.0
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class SinglePageAppConfiguration implements WebMvcConfigurer {

    private static final List<String> LOCATIONS =
            List.of("classpath:/static/", "file:./static/", "file:./ui/");

    @Value("${greenfinger.api.prefix:/v2}")
    private String apiPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String prefix = apiPrefix.startsWith("/") ? apiPrefix : "/" + apiPrefix;
        registry.addResourceHandler("/**").addResourceLocations(LOCATIONS.toArray(String[]::new))
                .resourceChain(true).addResolver(new PathResourceResolver() {

                    @Override
                    protected Resource getResource(String resourcePath, Resource location)
                            throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // an api path that reached here is a genuine 404, not a route: answering
                        // it with a page would turn a typo in a url into an unparseable response
                        if (("/" + resourcePath).startsWith(prefix)
                                || resourcePath.startsWith("actuator")) {
                            return null;
                        }
                        Resource index = location.createRelative("index.html");
                        return index.exists() && index.isReadable() ? index : null;
                    }
                });
    }

}
