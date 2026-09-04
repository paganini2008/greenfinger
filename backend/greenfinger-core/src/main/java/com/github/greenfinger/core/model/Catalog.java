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
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.greenfinger.core.component.state.CountingType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * A crawl task: what to fetch, how to fetch it, and where the result goes. The largest unit in the
 * system -- crawl, update, rebuild and delete all take a catalog, never a single url.
 *
 * <p>
 * Differences from 1.x worth knowing:
 *
 * <ul>
 * <li>{@code crawler_catalog_index} is folded in, as {@code index_version}.</li>
 * <li>{@code indexed} became {@code output_types}, since a crawl now feeds any combination of
 * file, index and vector rather than merely indexing or not.</li>
 * <li>{@code search_version} is new. 1.x incremented the version at the start of a rebuild while
 * search took the maximum version, so search went blank for the whole rebuild.</li>
 * <li>{@code interval} was renamed {@code fetch_interval}: INTERVAL is a reserved word, and the
 * 1.x name only survived because it ran on PostgreSQL alone.</li>
 * <li>{@code credential_handler} is gone with the rest of the login support.</li>
 * </ul>
 * 
 * @Description: Catalog
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Getter
@Setter
@ToString
@Entity
@Table(name = "crawler_catalog",
        uniqueConstraints = @UniqueConstraint(name = "uk_catalog_name", columnNames = "name"),
        indexes = {@Index(name = "idx_catalog_running_state", columnList = "running_state"),
                @Index(name = "idx_catalog_cat", columnList = "cat")})
public class Catalog implements Serializable {

    private static final long serialVersionUID = 1980884447290929341L;

    /** UUID v7: a catalog has no natural key, so the id must not be derived from one. */
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "url", nullable = false, length = 255)
    private String url;

    @Column(name = "start_url", length = 1000)
    private String startUrl;

    /** A sitemap in an unusual place; normally null and discovered automatically. */
    @Column(name = "sitemap_url", length = 1000)
    private String sitemapUrl;

    /** A label the user groups catalogs by. Nothing in the system maintains or depends on it. */
    @Column(name = "cat", nullable = false, length = 45)
    private String cat;

    @Column(name = "path_pattern", nullable = false, length = 2000)
    private String pathPattern;

    @Column(name = "excluded_path_pattern", length = 2000)
    private String excludedPathPattern;

    @Column(name = "page_encoding", length = 45)
    private String pageEncoding;

    @Column(name = "max_fetch_size")
    private Integer maxFetchSize;

    @Column(name = "depth")
    private Integer depth;

    @Column(name = "fetch_interval")
    private Long fetchInterval;

    @Column(name = "duration")
    private Long duration;

    /**
     * Stored as the small integer 1.x used.
     *
     * <p>
     * Held as a plain column rather than as an enum field: Hibernate infers a check constraint for
     * an enum-typed column, and the constraint it generates rejects every value on H2. The enum is
     * exposed through {@link #getCountingType()} instead, which keeps the type safety where it is
     * useful without letting the mapping generate DDL of its own.
     */
    @JsonIgnore
    @Column(name = "counting_type")
    private Integer countingTypeValue;

    @Column(name = "max_retry_count")
    private Integer maxRetryCount;

    @Column(name = "url_path_acceptor", length = 2000)
    private String urlPathAcceptor;

    @Column(name = "url_path_filter", length = 45)
    private String urlPathFilter;

    /**
     * Stored as the lower case name {@link ExtractorType} uses, for the same reason the other two
     * enums here are: Hibernate infers a check constraint for an enum-typed column, and the one it
     * generates rejects every value on H2.
     */
    @JsonIgnore
    @Column(name = "extractor", length = 45)
    private String extractorValue;

    @Column(name = "running_state", length = 45)
    private String runningState;

    /** Comma separated; {@code file} is implied whether or not it appears. */
    @JsonIgnore
    @Column(name = "output_types", nullable = false, length = 100)
    private String outputTypesValue;

    /** Whether images are fetched at all. */
    @Column(name = "image_enabled")
    private Boolean imageEnabled;

    /** Whether the index and the vector store receive those images. */
    @JsonIgnore
    @Column(name = "downstream_content", length = 20)
    private String downstreamContentValue;

    /** The version currently being written. Incremented by rebuild. */
    @Column(name = "index_version", nullable = false)
    private Integer indexVersion;

    /**
     * The most recent version that finished. Search reads this one, so a rebuild in progress never
     * takes the previous version out of service.
     */
    @Column(name = "search_version", nullable = false)
    private Integer searchVersion;

    /** How many versions to keep. Older ones are pruned once a crawl completes. */
    @Column(name = "max_versions", nullable = false)
    private Integer maxVersions;

    /** When a crawl of this catalog last finished. A fact about the data, not about the row. */
    @Column(name = "last_indexed")
    private Date lastIndexed;

    @Column(name = "created_at")
    private Date createdAt;

    @Column(name = "updated_at")
    private Date updatedAt;

    @Transient
    @JsonProperty("countingType")
    public CountingType getCountingType() {
        return countingTypeValue != null ? CountingType.valueOf(countingTypeValue.intValue())
                : null;
    }

    @JsonProperty("countingType")
    public void setCountingType(CountingType countingType) {
        this.countingTypeValue = countingType != null ? countingType.getValue() : null;
    }

    @Transient
    @JsonProperty("outputTypes")
    public Set<OutputType> getOutputTypes() {
        return OutputType.parse(outputTypesValue);
    }

    @JsonProperty("outputTypes")
    public void setOutputTypes(Set<OutputType> outputTypes) {
        this.outputTypesValue = OutputType.format(outputTypes);
    }

    /**
     * The one accessor, and it is the enum. A string pair beside it would be convenient and would
     * also be two getters for one json property, which Jackson refuses outright -- and, worse, a
     * second door into the column that skips the validation.
     */
    @Transient
    @JsonProperty("extractor")
    public ExtractorType getExtractorType() {
        return ExtractorType.of(extractorValue);
    }

    @JsonProperty("extractor")
    public void setExtractorType(ExtractorType extractorType) {
        this.extractorValue = extractorType != null ? extractorType.getRepr() : null;
    }

    @Transient
    @JsonProperty("contentMode")
    public ContentMode getContentMode() {
        return ContentMode.of(downstreamContentValue);
    }

    @JsonProperty("contentMode")
    public void setContentMode(ContentMode contentMode) {
        this.downstreamContentValue =
                (contentMode != null ? contentMode : ContentMode.TEXT_IMAGE).getRepr();
    }

}
