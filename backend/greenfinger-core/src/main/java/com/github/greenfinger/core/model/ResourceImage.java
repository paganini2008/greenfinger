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
 * A page referencing an image: the many to many link between {@link Resource} and {@link Image}.
 *
 * <p>
 * The three text columns describe how <em>this</em> page used the image, which is why they belong
 * here rather than on the image: the same picture carries different alt text on different pages.
 * {@code context_text} is the wording around the tag, and it is what lets an image with no alt
 * attribute still be found by a text search -- the same principle image search has always run on.
 *
 * <p>
 * This row's id is also the point id of the image's vector, because the two express the same
 * thing: one page-image reference.
 * 
 * @Description: ResourceImage
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Getter
@Setter
@ToString
@Entity
@Table(name = "crawler_resource_image",
        uniqueConstraints = @UniqueConstraint(name = "uk_resource_image",
                columnNames = {"resource_id", "image_id"}),
        indexes = {@Index(name = "idx_ri_catalog_ver", columnList = "catalog_id,version"),
                @Index(name = "idx_ri_resource", columnList = "resource_id"),
                @Index(name = "idx_ri_image", columnList = "image_id")})
public class ResourceImage implements Serializable {

    private static final long serialVersionUID = 3321574936625390018L;

    /** UUID v5 of {@code version + "|" + resourceId + "|" + imageId}. */
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "catalog_id", nullable = false, length = 36)
    private String catalogId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "resource_id", nullable = false, length = 36)
    private String resourceId;

    @Column(name = "image_id", nullable = false, length = 36)
    private String imageId;

    /** The url this page pointed at, which need not be where the bytes were first found. */
    @Column(name = "source_url", nullable = false, length = 1000)
    private String sourceUrl;

    @Column(name = "alt_text", length = 1000)
    private String altText;

    @Column(name = "title_text", length = 1000)
    private String titleText;

    /** Wording around the tag, truncated. Makes an image findable by text. */
    @Column(name = "context_text", length = 2000)
    private String contextText;

    @Column(name = "created_at")
    private Date createdAt;

    @Column(name = "updated_at")
    private Date updatedAt;

}
