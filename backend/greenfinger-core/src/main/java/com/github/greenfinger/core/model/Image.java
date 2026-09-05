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
 * One distinct image, deduplicated by its bytes within a catalog and version.
 *
 * <p>
 * Separating the image from the pages that reference it is what keeps a site's logo from becoming
 * five thousand files and five thousand vectors. The relationship is many to many and lives in
 * {@link ResourceImage}.
 *
 * <p>
 * {@code first_source_url} is where the bytes were first seen, kept for diagnosis only: the same
 * bytes may be served from several urls, and which url a given page used belongs to the reference,
 * not to the image.
 * 
 * @Description: Image
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Getter
@Setter
@ToString
@Entity
@Table(name = "crawler_image",
        uniqueConstraints = @UniqueConstraint(name = "uk_image",
                columnNames = {"catalog_id", "version", "content_hash"}),
        indexes = @Index(name = "idx_image_catalog_ver", columnList = "catalog_id,version"))
public class Image implements Serializable {

    private static final long serialVersionUID = 7422955384625390013L;

    /** UUID v5 of {@code version + "|" + content_hash} within the catalog's namespace. */
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "catalog_id", nullable = false, length = 36)
    private String catalogId;

    @Column(name = "version", nullable = false)
    private Integer version;

    /** SHA-256 of the bytes. Deduplication key, and the input the id is derived from. */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "first_source_url", length = 1000)
    private String firstSourceUrl;

    @Column(name = "image_file_path", nullable = false, length = 1000)
    private String imageFilePath;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "bytes")
    private Long bytes;

    @Column(name = "created_at")
    private Date createdAt;

    @Column(name = "updated_at")
    private Date updatedAt;

}
