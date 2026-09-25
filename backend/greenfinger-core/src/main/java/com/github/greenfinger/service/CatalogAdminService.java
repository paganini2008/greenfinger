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

import java.util.Date;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsNotFoundException;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.engine.CrawlRegistry;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.Category;
import com.github.greenfinger.core.model.ContentMode;
import com.github.greenfinger.core.model.ExtractorType;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.output.BlobStore;
import com.github.greenfinger.core.output.FileLayout;
import com.github.greenfinger.core.utils.BeanLifeCycleUtils;
import com.github.greenfinger.core.utils.UrlPathPatterns;
import com.github.greenfinger.core.utils.UrlUtils;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.output.OutputProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.github.greenfinger.core.WebCrawlerConstants;

/**
 * Creating, finding and describing catalogs. Everything that runs a crawl saves its definition
 * through here first, so the command line and a web front end share one path.
 * 
 * @Description: CatalogAdminService
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class CatalogAdminService {

    private final CatalogStore catalogStore;
    private final WebCrawlerProperties webCrawlerProperties;
    private final OutputProperties outputProperties;
    private final OutputFactory outputFactory;
    private final CrawlRegistry crawlRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Fills in whatever the caller left out, so a url alone is a complete definition.
     */
    public Catalog save(Catalog catalog) {
        if (StringUtils.isBlank(catalog.getUrl())) {
            throw new WebCrawlerException("A catalog needs a url");
        }
        keepWhatWasNotSent(catalog);
        if (StringUtils.isBlank(catalog.getName())) {
            // the registrable domain: short, recognisable, and stable across subdomains
            catalog.setName(UrlUtils.getDomainName(catalog.getUrl()));
        }
        if (StringUtils.isBlank(catalog.getStartUrl())) {
            // start url is a prefix as well as a seed; defaulting it to the site keeps the whole
            // site in scope rather than narrowing to a section
            catalog.setStartUrl(catalog.getUrl());
        }
        if (StringUtils.isBlank(catalog.getCat())) {
            catalog.setCat(Category.OTHER.getRepr());
        }
        if (StringUtils.isBlank(catalog.getPathPattern())) {
            catalog.setPathPattern(UrlPathPatterns.defaultPathPattern(catalog.getUrl()));
        }
        if (StringUtils.isBlank(catalog.getOutputTypesValue())) {
            catalog.setOutputTypes(OutputType.parse(outputProperties.getTypes()));
        }
        if (StringUtils.isBlank(catalog.getDownstreamContentValue())) {
            catalog.setContentMode(ContentMode.TEXT_IMAGE);
        }
        if (catalog.getImageEnabled() == null) {
            catalog.setImageEnabled(webCrawlerProperties.getImage().isEnabled());
        }
        if (catalog.getMaxVersions() == null) {
            catalog.setMaxVersions(webCrawlerProperties.getDefaultMaxVersions());
        }
        applyCrawlDefaults(catalog);
        if (StringUtils.isBlank(catalog.getRunningState())) {
            catalog.setRunningState(
                    WebCrawlerConstants.RUNNING_STATE_NONE);
        }
        catalog.setUpdatedAt(new Date());
        return catalogStore.save(catalog);
    }

    /**
     * An edit changes what it names and nothing else: a field a form did not send arrives as null
     * and is left alone. Taking null as "clear this" unpublishes a catalog on any edit, because the
     * fields most likely to be missing are the ones no form should set -- the version being
     * written, the one search serves. Only on this path; replication writes complete rows.
     */
    private void keepWhatWasNotSent(Catalog catalog) {
        if (catalog.getId() == null) {
            return;
        }
        Catalog stored = catalogStore.findById(catalog.getId()).orElse(null);
        if (stored == null) {
            return;
        }
        if (catalog.getIndexVersion() == null) {
            catalog.setIndexVersion(stored.getIndexVersion());
        }
        if (catalog.getSearchVersion() == null) {
            catalog.setSearchVersion(stored.getSearchVersion());
        }
        if (catalog.getLastIndexed() == null) {
            catalog.setLastIndexed(stored.getLastIndexed());
        }
        if (catalog.getCreatedAt() == null) {
            catalog.setCreatedAt(stored.getCreatedAt());
        }
        if (StringUtils.isBlank(catalog.getRunningState())) {
            catalog.setRunningState(stored.getRunningState());
        }
    }

    /**
     * Writes the defaults down rather than leaving the columns empty: the runtime would fill a
     * null anyway, but a row of nulls tells a person nothing and gives a form nothing to show.
     */
    private void applyCrawlDefaults(Catalog catalog) {
        if (StringUtils.isBlank(catalog.getPageEncoding())) {
            // detected per page from the response and the meta tag; this is only the fallback
            catalog.setPageEncoding(webCrawlerProperties.getDefaultPageEncoding());
        }
        if (catalog.getMaxFetchSize() == null) {
            catalog.setMaxFetchSize(webCrawlerProperties.getDefaultMaxFetchSize());
        }
        if (catalog.getDepth() == null) {
            // -1: follow the site as far as it goes, and let maxFetchSize and duration be the
            // limits that actually stop a crawl. A depth cap tends to cut off exactly the deep
            // pages that are worth having.
            catalog.setDepth(webCrawlerProperties.getDefaultMaxFetchDepth());
        }
        if (catalog.getFetchInterval() == null) {
            // a second between fetches: unremarkable to the site being crawled
            catalog.setFetchInterval(webCrawlerProperties.getDefaultFetchInterval());
        }
        if (catalog.getDuration() == null) {
            catalog.setDuration(webCrawlerProperties.getDefaultFetchDuration());
        }
        if (catalog.getCountingType() == null) {
            // count what was kept, not what was seen: it is the number a person means by "how
            // many pages", and the one maxFetchSize should measure
            catalog.setCountingType(CountingType.SAVED_RESOURCE_COUNT);
        }
        if (catalog.getMaxRetryCount() == null) {
            catalog.setMaxRetryCount(webCrawlerProperties.getDefaultMaxRetryCount());
        }
        if (StringUtils.isBlank(catalog.getUrlPathFilter())) {
            catalog.setUrlPathFilter(webCrawlerProperties.getDefaultUrlPathFilter());
        }
        if (catalog.getExtractorType() == null) {
            catalog.setExtractorType(ExtractorType.of(webCrawlerProperties.getDefaultExtractor()));
        }
        if (catalog.getIndexVersion() == null) {
            catalog.setIndexVersion(0);
        }
        if (catalog.getSearchVersion() == null) {
            catalog.setSearchVersion(-1);
        }
    }

    /**
     * Accepts either a name or an id, since a name is what a person types.
     */
    public Optional<Catalog> find(String idOrName) {
        if (StringUtils.isBlank(idOrName)) {
            return Optional.empty();
        }
        Optional<Catalog> byName = catalogStore.findByName(idOrName);
        return byName.isPresent() ? byName : catalogStore.findById(idOrName);
    }

    public Catalog require(String idOrName) {
        return find(idOrName).orElseThrow(
                () -> new CatalogDetailsNotFoundException("No such catalog: " + idOrName));
    }

    /**
     * By id and only by id. A name is unique, so looking up by it works -- until somebody
     * renames a catalog and a year-old script either fails or finds whatever took the name.
     */
    public Catalog requireById(String id) {
        // No "run catalog-list" here. Core is asked this by the http api, by the prompt and by
        // the one-line form, and only one of the three has that command -- a message naming it
        // told two callers out of three to type something they cannot. Whoever caught it knows
        // which face they are; the hint belongs there.
        if (StringUtils.isBlank(id)) {
            throw new CatalogDetailsNotFoundException("Give a catalog id.");
        }
        return catalogStore.findById(id.trim()).orElseThrow(
                () -> new CatalogDetailsNotFoundException("No catalog has the id '" + id + "'."));
    }

    public List<Catalog> findAll() {
        return catalogStore.findAll();
    }

    /**
     * The categories that exist, as a fixed list: {@code select distinct cat} answers what has
     * been used so far, and a picker fed by that can never choose a value nobody has used.
     */
    public List<String> findAllCategories() {
        return Arrays.stream(Category.values()).map(Category::getRepr).toList();
    }

    public List<Catalog> findRunning() {
        return catalogStore.findRunning();
    }

    /**
     * Removes a definition, stopping its crawl first. A crawl reads the frontier, not the catalog
     * table, so deleting the row underneath one leaves it fetching for a catalog that no longer
     * exists and never returning the permit. Asked rather than killed, so what is in flight still
     * reaches the output channel.
     */
    public boolean delete(String idOrName) {
        String id = require(idOrName).getId();
        if (crawlRegistry != null && crawlRegistry.interrupt(id)) {
            log.info("Stopped the crawl of catalog {} before deleting its definition", id);
        }
        return catalogStore.deleteById(id);
    }

    /**
     * The settings file a version wrote when it finished, which is where the counters of the last
     * run live.
     */
    public Optional<Map<String, Object>> readLastRun(CatalogDetails catalogDetails) {
        BlobStore blobStore = null;
        try {
            blobStore = outputFactory.getBlobStore();
            BeanLifeCycleUtils.afterPropertiesSet(blobStore);
            FileLayout layout = outputFactory.getFileLayout(catalogDetails);
            Optional<String> json = blobStore.readText(layout.settings());
            if (json.isEmpty()) {
                return Optional.empty();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(json.get(), Map.class);
            return Optional.of(parsed);
        } catch (Exception e) {
            return Optional.empty();
        } finally {
            BeanLifeCycleUtils.destroyQuietly(blobStore);
        }
    }

}
