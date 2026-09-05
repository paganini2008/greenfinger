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

package com.github.greenfinger.core.report;

import java.util.List;
import java.util.Optional;
import com.github.greenfinger.core.model.CrawlerReport;

/**
 * Reading and writing the {@code crawler_report} rows. An interface for the same reason
 * {@code CatalogStore} is one: the engine must not import a repository.
 * 
 * @Description: CrawlReportStore
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
public interface CrawlReportStore {

    /**
     * Writes the report for one version, keeping the {@code created_at} of the row already there.
     */
    CrawlerReport save(String catalogId, int version, String content);

    Optional<CrawlerReport> find(String catalogId, int version);

    /** Every version of one catalog, newest first. */
    List<CrawlerReport> findByCatalog(String catalogId);

    long deleteByCatalogAndVersion(String catalogId, int version);

    /** Every version's report at once. */
    long deleteByCatalog(String catalogId);

}
