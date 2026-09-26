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

package com.github.greenfinger.service.ops;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Set;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.extractor.ThreadWait;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.model.ContentMode;
import com.github.greenfinger.core.model.ExtractorType;
import com.github.greenfinger.core.model.OutputType;

/**
 * A catalog as it stands, flattened so it can travel. {@link CatalogDetails} is what a catalog
 * means once the defaults are applied, and it is computed from a row plus the properties in force
 * -- which only the crawler has. So the crawler resolves it and sends the answers.
 *
 * <p>
 * Unknown fields are ignored on the way in. Both interfaces have derived methods --
 * {@code getCatalogVersion()}, {@code getProgress()} -- which are written out because they are
 * getters and cannot be read back because there is nothing to set. Ignoring them also means a
 * node running a newer build can send a field an older one has never heard of.
 *
 * @Description: CatalogSnapshot
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
@JsonIgnoreProperties(value = {"catalogVersion"}, ignoreUnknown = true)
public class CatalogSnapshot implements CatalogDetails {
    private String id;
    private String name;
    private String url;
    private String startUrl;
    private String sitemapUrl;
    private String category;
    private List<String> pathPatterns;
    private List<String> excludedPathPatterns;
    private String pageEncoding;
    private Integer maxFetchSize;
    private Integer maxFetchDepth;
    private ThreadWait threadWait;
    private Long fetchInterval;
    private Long fetchDuration;
    private CountingType countingType;
    private Integer maxRetryCount;
    private List<String> urlPathAcceptors;
    private String urlPathFilter;
    private ExtractorType extractor;
    private Integer version;
    private Integer searchVersion;
    private Integer maxVersions;
    private String runningState;
    private Set<OutputType> outputTypes;
    private boolean imageEnabled;
    private ContentMode contentMode;

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String getStartUrl() {
        return startUrl;
    }

    public void setStartUrl(String startUrl) {
        this.startUrl = startUrl;
    }

    @Override
    public String getSitemapUrl() {
        return sitemapUrl;
    }

    public void setSitemapUrl(String sitemapUrl) {
        this.sitemapUrl = sitemapUrl;
    }

    @Override
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    @Override
    public List<String> getPathPatterns() {
        return pathPatterns;
    }

    public void setPathPatterns(List<String> pathPatterns) {
        this.pathPatterns = pathPatterns;
    }

    @Override
    public List<String> getExcludedPathPatterns() {
        return excludedPathPatterns;
    }

    public void setExcludedPathPatterns(List<String> excludedPathPatterns) {
        this.excludedPathPatterns = excludedPathPatterns;
    }

    @Override
    public String getPageEncoding() {
        return pageEncoding;
    }

    public void setPageEncoding(String pageEncoding) {
        this.pageEncoding = pageEncoding;
    }

    @Override
    public Integer getMaxFetchSize() {
        return maxFetchSize;
    }

    public void setMaxFetchSize(Integer maxFetchSize) {
        this.maxFetchSize = maxFetchSize;
    }

    @Override
    public Integer getMaxFetchDepth() {
        return maxFetchDepth;
    }

    public void setMaxFetchDepth(Integer maxFetchDepth) {
        this.maxFetchDepth = maxFetchDepth;
    }

    @Override
    public ThreadWait getThreadWait() {
        return threadWait;
    }

    public void setThreadWait(ThreadWait threadWait) {
        this.threadWait = threadWait;
    }

    @Override
    public Long getFetchInterval() {
        return fetchInterval;
    }

    public void setFetchInterval(Long fetchInterval) {
        this.fetchInterval = fetchInterval;
    }

    @Override
    public Long getFetchDuration() {
        return fetchDuration;
    }

    public void setFetchDuration(Long fetchDuration) {
        this.fetchDuration = fetchDuration;
    }

    @Override
    public CountingType getCountingType() {
        return countingType;
    }

    public void setCountingType(CountingType countingType) {
        this.countingType = countingType;
    }

    @Override
    public Integer getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(Integer maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    @Override
    public List<String> getUrlPathAcceptors() {
        return urlPathAcceptors;
    }

    public void setUrlPathAcceptors(List<String> urlPathAcceptors) {
        this.urlPathAcceptors = urlPathAcceptors;
    }

    @Override
    public String getUrlPathFilter() {
        return urlPathFilter;
    }

    public void setUrlPathFilter(String urlPathFilter) {
        this.urlPathFilter = urlPathFilter;
    }

    @Override
    public ExtractorType getExtractor() {
        return extractor;
    }

    public void setExtractor(ExtractorType extractor) {
        this.extractor = extractor;
    }

    @Override
    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public Integer getSearchVersion() {
        return searchVersion;
    }

    public void setSearchVersion(Integer searchVersion) {
        this.searchVersion = searchVersion;
    }

    @Override
    public Integer getMaxVersions() {
        return maxVersions;
    }

    public void setMaxVersions(Integer maxVersions) {
        this.maxVersions = maxVersions;
    }

    @Override
    public String getRunningState() {
        return runningState;
    }

    public void setRunningState(String runningState) {
        this.runningState = runningState;
    }

    @Override
    public Set<OutputType> getOutputTypes() {
        return outputTypes;
    }

    public void setOutputTypes(Set<OutputType> outputTypes) {
        this.outputTypes = outputTypes;
    }

    @Override
    public boolean isImageEnabled() {
        return imageEnabled;
    }

    public void setImageEnabled(boolean imageEnabled) {
        this.imageEnabled = imageEnabled;
    }

    @Override
    public ContentMode getContentMode() {
        return contentMode;
    }

    public void setContentMode(ContentMode contentMode) {
        this.contentMode = contentMode;
    }

    /** A copy of what {@code details} says right now, with every default already applied. */
    public static CatalogSnapshot of(CatalogDetails details) {
        if (details == null) {
            return null;
        }
        CatalogSnapshot copy = new CatalogSnapshot();
        copy.id = details.getId();
        copy.name = details.getName();
        copy.url = details.getUrl();
        copy.startUrl = details.getStartUrl();
        copy.sitemapUrl = details.getSitemapUrl();
        copy.category = details.getCategory();
        copy.pathPatterns = details.getPathPatterns();
        copy.excludedPathPatterns = details.getExcludedPathPatterns();
        copy.pageEncoding = details.getPageEncoding();
        copy.maxFetchSize = details.getMaxFetchSize();
        copy.maxFetchDepth = details.getMaxFetchDepth();
        copy.threadWait = details.getThreadWait();
        copy.fetchInterval = details.getFetchInterval();
        copy.fetchDuration = details.getFetchDuration();
        copy.countingType = details.getCountingType();
        copy.maxRetryCount = details.getMaxRetryCount();
        copy.urlPathAcceptors = details.getUrlPathAcceptors();
        copy.urlPathFilter = details.getUrlPathFilter();
        copy.extractor = details.getExtractor();
        copy.version = details.getVersion();
        copy.searchVersion = details.getSearchVersion();
        copy.maxVersions = details.getMaxVersions();
        copy.runningState = details.getRunningState();
        copy.outputTypes = details.getOutputTypes();
        copy.imageEnabled = details.isImageEnabled();
        copy.contentMode = details.getContentMode();
        return copy;
    }

}
