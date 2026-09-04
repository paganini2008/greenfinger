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

package com.github.greenfinger.record;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;
import com.github.greenfinger.core.model.CrawlerReport;
import com.github.greenfinger.core.report.CrawlReportStore;
import com.github.greenfinger.core.utils.UuidUtils;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @Description: JpaCrawlReportStore
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
@RequiredArgsConstructor
public class JpaCrawlReportStore implements CrawlReportStore {

    private final CrawlerReportRepository crawlerReportRepository;

    /**
     * The id is derived from the catalog and the version, so a second run of the same version
     * overwrites its own row rather than adding one. {@code created_at} is only ever written once.
     */
    @Transactional
    @Override
    public CrawlerReport save(String catalogId, int version, String content) {
        Date now = new Date();
        CrawlerReport report = crawlerReportRepository
                .findByCatalogIdAndVersion(catalogId, version).orElseGet(() -> {
                    CrawlerReport fresh = new CrawlerReport();
                    fresh.setId(idOf(catalogId, version));
                    fresh.setCatalogId(catalogId);
                    fresh.setVersion(version);
                    fresh.setCreatedAt(now);
                    return fresh;
                });
        report.setContent(content);
        report.setUpdatedAt(now);
        return crawlerReportRepository.save(report);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<CrawlerReport> find(String catalogId, int version) {
        return crawlerReportRepository.findByCatalogIdAndVersion(catalogId, version);
    }

    @Transactional(readOnly = true)
    @Override
    public List<CrawlerReport> findByCatalog(String catalogId) {
        return crawlerReportRepository.findByCatalogIdOrderByVersionDesc(catalogId);
    }

    @Transactional
    @Override
    public long deleteByCatalogAndVersion(String catalogId, int version) {
        return crawlerReportRepository.deleteByCatalogIdAndVersion(catalogId, version);
    }

    @Transactional
    @Override
    public long deleteByCatalog(String catalogId) {
        return crawlerReportRepository.deleteByCatalogId(catalogId);
    }

    static String idOf(String catalogId, int version) {
        return UuidUtils.nameBased(catalogId, "report:v" + version);
    }

}
