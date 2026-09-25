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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.github.greenfinger.core.model.Resource;

/**
 * 
 * @Description: ResourceRepository
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public interface ResourceRepository extends JpaRepository<Resource, String> {

    long countByCatalogIdAndVersion(String catalogId, int version);

    Optional<Resource> findByCatalogIdAndVersionAndUrlHash(String catalogId, int version,
            String urlHash);

    List<Resource> findByCatalogIdAndVersionOrderByCreatedAtAsc(String catalogId, int version,
            Pageable pageable);

    List<Resource> findByCatalogIdAndVersionOrderByCreatedAtDesc(String catalogId, int version,
            Pageable pageable);

    @Query("select distinct r.version from Resource r where r.catalogId = ?1 order by r.version")
    List<Integer> findVersions(String catalogId);

    /**
     * Browsing rather than searching: what a crawl stored, in crawl order, for any version --
     * including one never published, which the index cannot show.
     *
     * <p>
     * Every filter is optional, so the four are one query rather than sixteen. The keyword is
     * lowered on both sides, since the databases disagree about whether {@code like} is case
     * sensitive. The casts are what make "optional" work on PostgreSQL: {@code ? is null} leaves
     * the parameter untypeable and it refuses the statement ("could not determine data type of
     * parameter $7") where H2 and MySQL guess.
     */
    @Query("select r from Resource r where r.catalogId = :catalogId"
            + " and (cast(:version as Integer) is null or r.version = :version)"
            + " and (cast(:keyword as String) is null or lower(r.url) like :keyword"
            + "      or lower(r.title) like :keyword)"
            + " and (cast(:from as Timestamp) is null or r.createdAt >= :from)"
            + " and (cast(:to as Timestamp) is null or r.createdAt <= :to)")
    Page<Resource> browse(@Param("catalogId") String catalogId, @Param("version") Integer version,
            @Param("keyword") String keyword, @Param("from") Date from, @Param("to") Date to,
            Pageable pageable);

    @Modifying
    @Query("delete from Resource r where r.catalogId = ?1 and r.version = ?2")
    int deleteByCatalogIdAndVersion(String catalogId, int version);

    int deleteByCatalogId(String catalogId);

}
