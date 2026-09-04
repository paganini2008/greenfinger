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

package com.github.greenfinger.core.output;

import java.util.List;
import java.util.Locale;

/**
 * Index housekeeping, whichever engine is behind it: counting, deleting, and saying what exists.
 *
 * <p>
 * One index per catalog, named {@code <prefix>-<catalogId>}. Both implementations take a
 * {@code catalogVersion} rather than an index name, because that string -- {@code <catalogId>:<n>}
 * -- already carries both halves: which index, and which version inside it.
 * 
 * @Description: IndexAdmin
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
public interface IndexAdmin extends AutoCloseable {

    /** {@code lucene} or {@code elasticsearch}. */
    String getName();

    /** Where the indices are: a directory, or the server's url. */
    String getLocation();

    String getIndexPrefix();

    /** The index one catalog's documents live in. */
    String indexOf(String catalogId);

    boolean indexExists(String catalogId);

    long countByCatalogVersion(String catalogVersion) throws Exception;

    /**
     * Everything the catalog has, across every version.
     *
     * <p>
     * What makes it possible to tell "delete these versions" from "delete the lot": when the
     * versions being removed account for every document in the index, there is nothing left to
     * keep and the index itself can go.
     */
    long countByCatalog(String catalogId) throws Exception;

    /**
     * One version, by query. A marked deletion on both engines -- the space comes back when
     * segments merge -- which is the price of keeping every version of a catalog in one index.
     */
    long deleteByCatalogVersion(String catalogVersion) throws Exception;

    /**
     * Every version at once, by query. The index survives, emptied -- which is what makes this
     * the "clean it out" of the three: the catalog is still there and can be crawled again into
     * the index it already has.
     */
    long deleteAllVersions(String catalogId) throws Exception;

    /**
     * The index itself. Immediate, complete, nothing to merge afterwards, and it takes with it any
     * documents belonging to versions nothing else remembers.
     */
    long deleteByCatalog(String catalogId) throws Exception;

    /** Every index this engine holds under the prefix. */
    List<String> listIndices() throws Exception;

    /** Makes everything written so far visible to search. */
    void refresh() throws Exception;

    @Override
    default void close() throws Exception {}

    /**
     * The index name for a catalog. Lower case because Elasticsearch refuses anything else, and a
     * uuid is lower case hex already -- so the two engines agree on the name without either having
     * to know about the other.
     */
    static String indexOf(String prefix, String catalogId) {
        return (prefix + "-" + catalogId).toLowerCase(Locale.ROOT);
    }

    /**
     * The catalog id half of a {@code <catalogId>:<version>} pair.
     */
    static String catalogIdOf(String catalogVersion) {
        if (catalogVersion == null) {
            return "";
        }
        int colon = catalogVersion.lastIndexOf(':');
        return colon > 0 ? catalogVersion.substring(0, colon) : catalogVersion;
    }

}
