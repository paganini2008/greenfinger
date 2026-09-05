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

package com.github.greenfinger.core.catalog;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.component.extractor.ThreadWait;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.ContentMode;
import com.github.greenfinger.core.model.ExtractorType;
import com.github.greenfinger.core.model.OutputType;
import lombok.ToString;

/**
 * Wraps a stored {@link Catalog} and fills every unset column from the configured defaults.
 * 
 * @Description: CatalogDetailsImpl
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@ToString(onlyExplicitlyIncluded = true)
public class CatalogDetailsImpl implements CatalogDetails {

    private final Catalog catalog;
    private final WebCrawlerProperties webCrawlerProperties;
    private final List<String> pathPatterns;
    private final List<String> excludedPathPatterns;
    private final List<String> urlPathAcceptors;

    public CatalogDetailsImpl(Catalog catalog, WebCrawlerProperties webCrawlerProperties) {
        this.catalog = catalog;
        this.webCrawlerProperties = webCrawlerProperties;
        this.pathPatterns = split(catalog.getPathPattern());
        this.excludedPathPatterns = split(catalog.getExcludedPathPattern());
        this.urlPathAcceptors = split(catalog.getUrlPathAcceptor());
    }

    private static List<String> split(String value) {
        return StringUtils.isNotBlank(value) ? List.of(value.split(","))
                : Collections.emptyList();
    }

    public Catalog getCatalog() {
        return catalog;
    }

    @ToString.Include
    @Override
    public String getId() {
        return catalog.getId();
    }

    @ToString.Include
    @Override
    public String getName() {
        return catalog.getName();
    }

    @ToString.Include
    @Override
    public String getUrl() {
        return catalog.getUrl();
    }

    @ToString.Include
    @Override
    public String getCategory() {
        return catalog.getCat();
    }

    @Override
    public String getStartUrl() {
        return catalog.getStartUrl();
    }

    @Override
    public String getSitemapUrl() {
        return catalog.getSitemapUrl();
    }

    @Override
    public List<String> getPathPatterns() {
        return pathPatterns;
    }

    @Override
    public List<String> getExcludedPathPatterns() {
        return excludedPathPatterns;
    }

    @Override
    public String getPageEncoding() {
        return StringUtils.isNotBlank(catalog.getPageEncoding()) ? catalog.getPageEncoding()
                : webCrawlerProperties.getDefaultPageEncoding();
    }

    @ToString.Include
    @Override
    public Integer getMaxFetchSize() {
        return catalog.getMaxFetchSize() != null ? catalog.getMaxFetchSize()
                : webCrawlerProperties.getDefaultMaxFetchSize();
    }

    @ToString.Include
    @Override
    public Integer getMaxFetchDepth() {
        return catalog.getDepth() != null ? catalog.getDepth()
                : webCrawlerProperties.getDefaultMaxFetchDepth();
    }

    @Override
    public ThreadWait getThreadWait() {
        return ThreadWait.SLEEP;
    }

    @Override
    public Long getFetchInterval() {
        return catalog.getFetchInterval() != null ? catalog.getFetchInterval()
                : webCrawlerProperties.getDefaultFetchInterval();
    }

    @ToString.Include
    @Override
    public Long getFetchDuration() {
        return catalog.getDuration() != null ? catalog.getDuration()
                : webCrawlerProperties.getDefaultFetchDuration();
    }

    @Override
    public CountingType getCountingType() {
        return catalog.getCountingType() != null ? catalog.getCountingType()
                : CountingType.SAVED_RESOURCE_COUNT;
    }

    @Override
    public Integer getMaxRetryCount() {
        return catalog.getMaxRetryCount() != null ? catalog.getMaxRetryCount()
                : webCrawlerProperties.getDefaultMaxRetryCount();
    }

    @Override
    public List<String> getUrlPathAcceptors() {
        return urlPathAcceptors;
    }

    @Override
    public String getUrlPathFilter() {
        return StringUtils.isNotBlank(catalog.getUrlPathFilter()) ? catalog.getUrlPathFilter()
                : webCrawlerProperties.getDefaultUrlPathFilter();
    }

    @ToString.Include
    @Override
    public ExtractorType getExtractor() {
        ExtractorType extractorType = catalog.getExtractorType();
        return extractorType != null ? extractorType
                : ExtractorType.of(webCrawlerProperties.getDefaultExtractor());
    }

    @ToString.Include
    @Override
    public Integer getVersion() {
        return catalog.getIndexVersion() != null ? catalog.getIndexVersion() : 0;
    }

    @ToString.Include
    @Override
    public Integer getSearchVersion() {
        return catalog.getSearchVersion() != null ? catalog.getSearchVersion() : -1;
    }

    @Override
    public Integer getMaxVersions() {
        return catalog.getMaxVersions() != null ? catalog.getMaxVersions()
                : webCrawlerProperties.getDefaultMaxVersions();
    }

    @ToString.Include
    @Override
    public String getRunningState() {
        return catalog.getRunningState();
    }

    @ToString.Include
    @Override
    public Set<OutputType> getOutputTypes() {
        String stored = catalog.getOutputTypesValue();
        return OutputType.parse(StringUtils.isNotBlank(stored) ? stored
                : webCrawlerProperties.getDefaultOutputTypes());
    }

    @Override
    public boolean isImageEnabled() {
        return catalog.getImageEnabled() != null ? catalog.getImageEnabled()
                : webCrawlerProperties.getImage().isEnabled();
    }

    @Override
    public ContentMode getContentMode() {
        return catalog.getContentMode();
    }

}
