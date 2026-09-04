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

package com.github.greenfinger.output.vector;

import java.util.List;
import com.github.greenfinger.core.ManagedBeanLifeCycle;

/**
 * Qdrant or Weaviate. Two collections are in play at once -- one for text, one for images -- so
 * every call names the collection it acts on.
 *
 * <p>
 * Point ids are the ids the database already assigned, which are legal UUIDs, so they serve as the
 * native id as well as travelling in the payload. Being derived from natural keys, writing the same
 * point twice overwrites rather than duplicates, which is what makes replaying a version safe.
 * 
 * @Description: VectorStore
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public interface VectorStore extends ManagedBeanLifeCycle {

    String getName();

    /**
     * Creates the collection when it is missing, and refuses to proceed when an existing one has a
     * different width -- switching embedding models is not something to discover mid-crawl.
     */
    void ensureCollection(String collection, int dimensions) throws Exception;

    void upsert(String collection, List<VectorPoint> points) throws Exception;

    /**
     * Removes everything belonging to one version.
     *
     * @param catalogVersion the {@code <catalogId>:<version>} value carried in every payload
     * @return how many points went, or -1 when the store does not say
     */
    long deleteByCatalogVersion(String collection, String catalogVersion) throws Exception;

    /**
     * Everything belonging to one catalog, every version at once.
     *
     * <p>
     * A collection is shared by every catalog -- its name carries the width of the vectors, not
     * the catalog -- so there is no collection to drop and this is the strongest form there is:
     * "clean it out" and "the catalog is gone" remove exactly the same points. What it saves over
     * naming each version is the round trips: one filtered delete rather than one per version.
     *
     * @return how many points went, or -1 when the store does not say
     */
    long deleteByCatalog(String collection, String catalogId) throws Exception;

    long count(String collection, String catalogVersion) throws Exception;

    /** Everything one catalog has in a collection, across every version. */
    long countByCatalog(String collection, String catalogId) throws Exception;

    /**
     * Every collection this store holds whose name starts with the prefix.
     *
     * <p>
     * Needed because a collection's name carries the width of the vectors in it -- 
     * {@code greenfinger_text_384}, {@code greenfinger_image_768} -- and the width is a property
     * of the embedding model, which is not loaded when a version is being deleted. Deleting by
     * the bare prefix instead was the bug this exists to fix: it addressed a collection that has
     * never existed, removed nothing, and reported nothing removed, which reads exactly like
     * "there was nothing to remove".
     */
    List<String> collectionsMatching(String prefix) throws Exception;

    /**
     * Nearest neighbours, restricted to the versions given.
     *
     * @param catalogVersions the {@code <catalogId>:<version>} values that are currently published
     */
    default List<VectorHit> search(String collection, float[] vector, int limit,
            List<String> catalogVersions) throws Exception {
        return search(collection, vector, limit, 0, catalogVersions);
    }

    /**
     * The same, starting further down the ranking.
     *
     * <p>
     * An offset rather than a cursor, unlike the index, because a vector search has no stable sort
     * key to carry forward -- the ordering is a distance to this query and exists only for this
     * call. Paging deeply therefore costs the store more each time, which is why the pages a user
     * can reach are counted rather than endless.
     *
     * @param offset how many of the nearest neighbours to skip
     */
    List<VectorHit> search(String collection, float[] vector, int limit, int offset,
            List<String> catalogVersions) throws Exception;

}
