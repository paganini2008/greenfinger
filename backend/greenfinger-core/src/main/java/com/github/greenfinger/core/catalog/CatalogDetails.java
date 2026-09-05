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

import java.util.List;
import java.util.Set;
import com.github.greenfinger.core.component.extractor.ThreadWait;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.model.ContentMode;
import com.github.greenfinger.core.model.ExtractorType;
import com.github.greenfinger.core.model.OutputType;

/**
 * The runtime view of a catalog: the stored definition with the configured defaults already
 * applied, so nothing downstream has to decide what a null column means.
 *
 * <p>
 * Obtained only through {@link CatalogDetailsService}, never constructed from request parameters.
 * A crawl therefore always runs against a definition that is in the database, which is what lets
 * the command line and a web front end share one path.
 * 
 * @Description: CatalogDetails
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public interface CatalogDetails {

    String getId();

    String getName();

    String getUrl();

    String getStartUrl();

    /**
     * A sitemap in an unusual place. Normally null, and then it is discovered from robots.txt or
     * the conventional location.
     */
    default String getSitemapUrl() {
        return null;
    }

    String getCategory();

    List<String> getPathPatterns();

    List<String> getExcludedPathPatterns();

    String getPageEncoding();

    Integer getMaxFetchSize();

    Integer getMaxFetchDepth();

    ThreadWait getThreadWait();

    Long getFetchInterval();

    Long getFetchDuration();

    CountingType getCountingType();

    Integer getMaxRetryCount();

    List<String> getUrlPathAcceptors();

    String getUrlPathFilter();

    ExtractorType getExtractor();

    /** The version being written. */
    Integer getVersion();

    /** The most recent completed version, which is the one search reads. */
    Integer getSearchVersion();

    /** How many versions to keep before pruning the oldest. */
    Integer getMaxVersions();

    String getRunningState();

    /** Every output this crawl feeds. Always contains {@link OutputType#FILE}. */
    Set<OutputType> getOutputTypes();

    default boolean hasOutput(OutputType outputType) {
        return getOutputTypes().contains(outputType);
    }

    /** Whether images are fetched at all. */
    boolean isImageEnabled();

    /** Whether the index and the vector store receive those images. */
    ContentMode getContentMode();

    /**
     * The value the index and the vector store filter on, of the form {@code <catalogId>:<version>}.
     * One field carries both, so a search spanning catalogs whose current versions differ is a
     * single any-of match rather than a nest of and/or.
     */
    default String getCatalogVersion() {
        return getId() + ":" + getVersion();
    }

}
