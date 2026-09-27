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

package com.github.greenfinger.service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.core.model.Catalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The catalogs an installation starts with, defined once and then left alone.
 *
 * <p>
 * An empty page is a bad first impression, and choosing a site to crawl is a decision nobody
 * should have to make in their first five minutes. The shipped file describes six that are worth
 * crawling: each publishes a permissive robots.txt, each finishes in a minute or two, and each
 * shows a different part of the crawler doing something -- one for the image pipeline, one for the
 * readability extraction, one whose pages are written by javascript, one that links .txt files
 * beside its html.
 *
 * <p>
 * <b>Idempotent by name.</b> A catalog that is already there is left exactly as it is, edits
 * included, so this runs on every start and changes nothing after the first. Deleting one keeps
 * it deleted for that reason too -- it comes back only if its name is free again.
 *
 * <p>
 * An installation replaces the file rather than editing this: point
 * {@code greenfinger.initial-catalogs} at one of your own, or leave it blank to define nothing.
 *
 * @Description: InitialCatalogs
 * @Author: Fred Feng
 * @Date: 27/09/2026
 * @Version 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class InitialCatalogs implements SmartInitializingSingleton {

    public static final String DEFAULT_LOCATION = "classpath:initial_catalogs.json";

    private final CatalogAdminService catalogAdminService;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final String location;

    @Override
    public void afterSingletonsInstantiated() {
        if (StringUtils.isBlank(location)) {
            return;
        }
        List<Catalog> defined;
        try {
            defined = read();
        } catch (Exception e) {
            // a file somebody wrote by hand: say what is wrong with it and start anyway, because
            // a node that will not come up over its example data is worse than one with none
            log.warn("Could not read {}: {}", location, e.getMessage());
            return;
        }
        int added = 0;
        for (Catalog catalog : defined) {
            try {
                if (catalogAdminService.find(catalog.getName()).isPresent()) {
                    continue;
                }
                catalogAdminService.save(catalog);
                added++;
            } catch (Exception e) {
                // two nodes starting together both find the name free; the unique constraint
                // decides, and the one that lost has nothing to do
                log.debug("Did not define '{}': {}", catalog.getName(), e.getMessage());
            }
        }
        if (added > 0) {
            log.info("Defined {} catalog(s) from {}. They are definitions only: nothing crawls"
                    + " until asked.", added, location);
        }
    }

    private List<Catalog> read() throws Exception {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            return List.of();
        }
        try (InputStream in = resource.getInputStream()) {
            List<Map<String, Object>> rows = objectMapper.readValue(in, List.class);
            // description is for whoever opens the file; the model has no such field and Jackson
            // would refuse the whole list over it
            rows.forEach(row -> row.remove("description"));
            return rows.stream().map(row -> objectMapper.convertValue(row, Catalog.class)).toList();
        }
    }

}
