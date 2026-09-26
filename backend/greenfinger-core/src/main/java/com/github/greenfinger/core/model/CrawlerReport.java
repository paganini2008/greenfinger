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

package com.github.greenfinger.core.model;

import java.io.Serializable;
import java.util.Date;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * What one version of one catalog cost to build. One row per {@code (catalog, version)} holding the
 * whole picture as json: per-node counters and totals, the cluster as it stood, the stores written
 * into, and the settings the run used. Re-running a version rewrites the row and moves
 * {@code updated_at}; {@code created_at} still says when it was first built.
 *
 * <p>
 * The per-run report file beside the pages stays -- it survives the database being thrown away --
 * but it cannot be queried, and it is gone once the version is deleted, which is when somebody
 * wants it. Json rather than fifty columns: nothing here is ever joined on.
 * 
 * @Description: CrawlerReport
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
@Getter
@Setter
@ToString
@Entity
@Table(name = "crawler_report",
        uniqueConstraints = @UniqueConstraint(name = "uk_report_catalog_version",
                columnNames = {"catalog_id", "version"}),
        indexes = @Index(name = "idx_report_catalog", columnList = "catalog_id"))
public class CrawlerReport implements Serializable {

    private static final long serialVersionUID = -6116944128104525533L;

    /**
     * Derived from the catalog id and the version rather than random, so the node that writes the
     * report a second time updates the row it wrote the first time -- without a select to find it,
     * and without two nodes ever creating two rows for the same version.
     */
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "catalog_id", nullable = false, length = 36)
    private String catalogId;

    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * The whole report, as json. A declared length, never {@code @Lob}: MySQL maps that to
     * {@code tinytext} (255 bytes) and PostgreSQL to {@code oid}, which stores a large-object
     * reference instead of the text. The length maps to the right type on every dialect.
     */
    @Column(name = "content", nullable = false, length = 1_000_000)
    private String content;

    /** When this version was first built. */
    @Column(name = "created_at", nullable = false)
    private Date createdAt;

    /** When it was last crawled, updated or resumed. */
    @Column(name = "updated_at", nullable = false)
    private Date updatedAt;

}
