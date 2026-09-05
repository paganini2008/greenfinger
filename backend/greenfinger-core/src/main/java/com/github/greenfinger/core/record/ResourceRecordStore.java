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

package com.github.greenfinger.core.record;

import java.util.List;
import java.util.Optional;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawledPage;
import com.github.greenfinger.core.output.FileLayout;

/**
 * The first stop for every page, and the gate for all the others.
 *
 * <p>
 * Nothing reaches the file store, the index or the vector store until it is in here, because this
 * is where the unique constraint lives. That constraint is an assertion rather than a mechanism:
 * url deduplication happens in the crawl frontier, so if it ever fires, the deduplication layer has
 * a bug and the loud failure is the point.
 * 
 * @Description: ResourceRecordStore
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public interface ResourceRecordStore {

    /**
     * Writes the page and its images. File paths are derived from the ids, which are derived from
     * the natural keys, so they are known before a single byte is written and can be stored in the
     * same insert.
     *
     * @return the ids assigned, so the caller can write the files where the rows say they are
     */
    ResourceRecord save(CatalogDetails catalogDetails, CrawledPage page, FileLayout layout)
            throws Exception;

    /** Reads back what {@link #save} wrote, which is what the index and the vectors are built from. */
    Optional<ResourceRecord> load(String resourceId);

    /**
     * The content fingerprint already stored for a url, if there is one.
     *
     * <p>
     * This is what makes a refresh a merge rather than a re-crawl: fetch the page, compare, and
     * only write when it actually differs. Everything downstream is then untouched for the pages
     * that have not changed, which on most sites is nearly all of them.
     */
    Optional<String> findContentHash(String catalogId, int version, String urlHash);

    /**
     * Everything the last crawl of this url left behind that the next one can use: the fingerprint
     * that says whether the text changed, and the validators the site sent so it can be asked
     * rather than re-read. One lookup, because a merge needs both at the same moment.
     */
    Optional<PageState> findPageState(String catalogId, int version, String urlHash);

    /**
     * @param contentHash SHA-256 of the extracted text, or null if it was never recorded
     * @param etag the site's {@code ETag}, or null if it publishes none
     * @param lastModified the site's {@code Last-Modified}, or null
     */
    record PageState(String contentHash, String etag, String lastModified) {
    }

    /**
     * A page of a version, oldest first, for replaying a layer.
     */
    List<ResourceRecord> load(String catalogId, int version, int offset, int limit);

    /**
     * The most recently saved url. This is where {@code update} picks up when the frontier has
     * nothing left in it -- the 1.x behaviour, kept as the fallback.
     */
    Optional<String> getLatestReferencePath(String catalogId, int version);

    long countByCatalog(String catalogId, int version);

    long countImagesByCatalog(String catalogId, int version);

    /** Every version this catalog has data for, ascending. */
    List<Integer> findVersions(String catalogId);

    /**
     * Removes one version. Called last in the delete sequence, since until then this is where the
     * list of what to delete comes from.
     *
     * @return how many rows went, across all three tables
     */
    long deleteByCatalogAndVersion(String catalogId, int version);

    /**
     * Every version of one catalog at once.
     *
     * <p>
     * Three statements rather than three per version, which is what naming each version would
     * cost. The tables stay, obviously -- they are everybody's; what goes is this catalog's rows,
     * including any belonging to a version nothing else remembers.
     */
    long deleteByCatalog(String catalogId);

}
