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

package com.github.greenfinger.core.catalog;

import java.util.List;
import java.util.Optional;
import com.github.greenfinger.core.model.Catalog;

/**
 * Where crawl definitions live. 2.0 has exactly one implementation, backed by the database, which
 * is mandatory: every crawl -- whether launched from the command line or from a web front end --
 * saves its definition first and runs from what was saved.
 * 
 * @Description: CatalogStore
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public interface CatalogStore {

    String getName();

    /** Creates or updates. A catalog with no id is assigned a UUID v7. */
    Catalog save(Catalog catalog);

    Optional<Catalog> findById(String id);

    Optional<Catalog> findByName(String name);

    List<Catalog> findAll();

    /** Every distinct category, which is what a search filters by. */
    List<String> findAllCategories();

    boolean deleteById(String id);

    /**
     * Starts a new version: {@code index_version + 1}, leaving {@code search_version} where it is
     * so searches keep serving the previous version until the new one finishes.
     *
     * @return the new index version
     */
    int incrementIndexVersion(String id);

    /**
     * Promotes the version that just finished, which is what makes it visible to search.
     */
    void publishSearchVersion(String id, int version);

    /**
     * Back to defined and never crawled: {@code index_version = 0} and no search version.
     *
     * <p>
     * What an emptied catalog is. Leaving the numbers where they were would have the next crawl
     * write v4 into a catalog whose v0 to v3 no longer exist anywhere -- a version count that
     * counts nothing, and a first crawl that reports itself as the fourth.
     */
    void resetVersions(String id);

    void setRunningState(String id, String runningState);

    List<Catalog> findRunning();

}
