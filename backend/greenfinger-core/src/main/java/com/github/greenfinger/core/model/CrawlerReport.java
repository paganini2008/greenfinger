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
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * What one version of one catalog cost to build, kept in the database.
 *
 * <p>
 * There is already a report per run beside the pages themselves, and it stays: it is written by
 * every node, it survives the database being thrown away, and a directory copied elsewhere carries
 * its own account of how it was made. What it cannot do is be queried, and it cannot be read at all
 * once its version has been deleted -- which is exactly when somebody wants to know what that
 * version had been.
 *
 * <p>
 * So this row is the other half: one per {@code (catalog, version)}, holding the whole picture as
 * json -- every node's counters and the totals, the cluster as it stood, the database and blob
 * store the run wrote into, how many files and images came out of it, and the settings the crawl
 * ran under. Re-running the same version -- an update, a resume -- rewrites the row and moves
 * {@code updated_at}; {@code created_at} keeps saying when that version was first built.
 *
 * <p>
 * The content is json rather than fifty columns because the shape is a report, not a model: it
 * grows a field whenever there is one more thing worth recording, and none of it is ever joined on.
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

    /** The whole report, as json. */
    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    /** When this version was first built. */
    @Column(name = "created_at", nullable = false)
    private Date createdAt;

    /** When it was last crawled, updated or resumed. */
    @Column(name = "updated_at", nullable = false)
    private Date updatedAt;

}
