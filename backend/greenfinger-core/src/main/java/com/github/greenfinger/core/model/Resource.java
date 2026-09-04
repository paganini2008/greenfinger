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
 * One url crawled at one version. Metadata only.
 *
 * <p>
 * 1.x kept the whole page here, in {@code html}. Crawling a hundred thousand pages made that one
 * column tens of gigabytes, and neither of the database's two jobs -- driving the crawl, and
 * letting someone look at what was collected -- needs the body. So the body lives in the file
 * store and this row points at it.
 *
 * <p>
 * {@code url_hash} exists because of MySQL. 1.x put its unique constraint straight on
 * {@code url varchar(1000)}, which works on PostgreSQL but exceeds InnoDB's 3072 byte index key
 * limit under utf8mb4. The hash is a fixed 64 characters, and it is also the input the id is
 * derived from.
 * 
 * @Description: Resource
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Getter
@Setter
@ToString
@Entity
@Table(name = "crawler_resource",
        uniqueConstraints = @UniqueConstraint(name = "uk_resource",
                columnNames = {"catalog_id", "version", "url_hash"}),
        indexes = {@Index(name = "idx_resource_catalog_ver", columnList = "catalog_id,version"),
                @Index(name = "idx_resource_created_at",
                        columnList = "catalog_id,version,created_at")})
public class Resource implements Serializable {

    private static final long serialVersionUID = -4629236151028422706L;

    /** UUID v5 of {@code version + "|" + url_hash} within the catalog's namespace. */
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "catalog_id", nullable = false, length = 36)
    private String catalogId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    @Column(name = "url_hash", nullable = false, length = 64)
    private String urlHash;

    @Column(name = "title", length = 1000)
    private String title;

    @Column(name = "cat", nullable = false, length = 45)
    private String cat;

    /** SHA-256 of the extracted text, at url + version granularity. */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    /**
     * What the site said about this page last time, offered back on the next merge so it can
     * answer 304 rather than send the page again. Null for a site that publishes neither, and for
     * every page fetched by a browser engine, which never sees the response.
     */
    @Column(name = "etag", length = 200)
    private String etag;

    /**
     * The site's {@code Last-Modified} header, not this row's. Named apart from {@code updated_at}
     * on purpose: one is an http validator to be echoed back on the next merge, the other is when
     * the row changed, and a column called {@code last_modified} sitting next to {@code updated_at}
     * is a question nobody should have to ask twice.
     */
    @Column(name = "http_last_modified", length = 100)
    private String httpLastModified;

    @Column(name = "depth")
    private Integer depth;

    /**
     * Outgoing links, and the length of the extracted text.
     *
     * <p>
     * Together these separate a detail page from a listing without any classification: a listing is
     * mostly links and little prose, a detail page the reverse. Search boosts on the ratio, which is
     * why the two numbers are stored rather than computed at query time -- a replay has to produce
     * the same ranking as the original crawl.
     */
    @Column(name = "link_count")
    private Integer linkCount;

    @Column(name = "text_length")
    private Integer textLength;

    /**
     * Characters of text inside links. Divided by {@code text_length} this is the link density that
     * boilerplate detection has used since Boilerpipe: near one for a listing, near zero for an
     * article, and unlike a raw link count it is not skewed by how long the page is.
     */
    @Column(name = "link_text_length")
    private Integer linkTextLength;

    @Column(name = "referer", length = 1000)
    private String referer;

    /** Relative to the output root; the same string is the MinIO object key. */
    @Column(name = "html_file_path", length = 1000)
    private String htmlFilePath;

    /** The extracted text, as a .txt sidecar. This is what the index and the vectors read. */
    @Column(name = "html_content_file_path", length = 1000)
    private String htmlContentFilePath;

    @Column(name = "created_at")
    private Date createdAt;

    @Column(name = "updated_at")
    private Date updatedAt;

}
