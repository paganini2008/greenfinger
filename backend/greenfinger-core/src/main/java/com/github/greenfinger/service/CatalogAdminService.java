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
                    com.github.greenfinger.core.WebCrawlerConstants.RUNNING_STATE_NONE);
        }
        catalog.setUpdatedAt(new Date());
        return catalogStore.save(catalog);
    }

    /**
     * Writes the defaults down rather than leaving the columns empty.
     *
     * <p>
     * The runtime would fill a null in anyway, but a row full of nulls tells a person nothing about
     * how their crawl is actually configured, and a form has nothing to show. So the settled values
     * are stored, and they are the ones a new user should want: polite pacing, no depth limit,
     * images on, and the fastest extractor -- the three browser engines exist for sites that need
     * javascript, and are several times slower.
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
     * By id and only by id.
     *
     * <p>
     * The name is unique, so looking a catalog up by it works -- which is exactly the problem. A
     * name is editable, and a command that took one would keep working right up until somebody
     * renamed a catalog, at which point a script that had been correct for a year would start
     * either failing or, worse, finding a different catalog that had since taken the name. The
     * command line therefore addresses catalogs by id, and the id is what {@code catalog-list}
     * prints.
     */
    public Catalog requireById(String id) {
        if (StringUtils.isBlank(id)) {
            throw new CatalogDetailsNotFoundException(
                    "Give a catalog id. Run 'catalog-list' to see them.");
        }
        return catalogStore.findById(id.trim()).orElseThrow(
                () -> new CatalogDetailsNotFoundException("No catalog has the id '" + id + "'."
                        + " Run 'catalog-list' to see the ids."));
    }

    public List<Catalog> findAll() {
        return catalogStore.findAll();
    }

    /**
     * The categories that exist, which is now a fixed list rather than whatever has been typed.
     *
     * <p>
     * It used to be {@code select distinct cat}, which answered a different question: what has
     * been used so far. That is the wrong answer for the thing asking -- a picker offering only
     * the values already in the table can never be used to choose a new one.
     */
    public List<String> findAllCategories() {
        return Arrays.stream(Category.values()).map(Category::getRepr).toList();
    }

    public List<Catalog> findRunning() {
        return catalogStore.findRunning();
    }

    /**
     * Removes a definition, stopping its crawl first if one is running.
     *
     * <p>
     * The order matters, and getting it wrong is worse than it sounds. A crawl holds this
     * process's one permit for as long as it runs, and it finds out what to fetch next from the
     * frontier rather than from the catalog table -- so deleting the row underneath a running
     * crawl does not stop it. It keeps fetching, for a catalog that no longer exists, while
     * disappearing from the running list (the row it was listed by is gone). The permit is never
     * given back, and every later crawl on this node is refused by a crawl nobody can see or name.
     * Observed for real: an hour of cpu spent on a deleted catalog, and every crawl afterwards
     * turned away.
     *
     * <p>
     * Asking rather than killing: the crawl winds down at its next check, so what is in flight
     * still reaches the output channel. In a cluster the other nodes hear about it the usual way,
     * from the control channel once this node's run ends.
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
