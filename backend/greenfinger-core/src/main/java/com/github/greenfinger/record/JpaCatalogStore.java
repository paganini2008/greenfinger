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

package com.github.greenfinger.record;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import com.github.greenfinger.core.catalog.CatalogDetailsNotFoundException;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.ContentMode;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.utils.UuidUtils;
import lombok.RequiredArgsConstructor;

/**
 * The catalog store. Definitions and crawled metadata live in the same database, so the two cannot
 * drift apart.
 * 
 * @Description: JpaCatalogStore
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@RequiredArgsConstructor
public class JpaCatalogStore implements CatalogStore {

    /** Matches WebCrawlerProperties, so a catalog saved through the store alone is still valid. */
    private static final int DEFAULT_MAX_VERSIONS = 10;

    private final CatalogRepository catalogRepository;

    @Override
    public String getName() {
        return "jpa";
    }

    @Override
    @Transactional
    public Catalog save(Catalog catalog) {
        if (catalog.getId() == null) {
            catalog.setId(UuidUtils.timeBasedString());
        }
        requireNameIsFree(catalog);
        if (catalog.getIndexVersion() == null) {
            catalog.setIndexVersion(0);
        }
        if (catalog.getSearchVersion() == null) {
            // -1 rather than 0: nothing has finished yet, so there is no version to search
            catalog.setSearchVersion(-1);
        }
        if (catalog.getOutputTypesValue() == null) {
            catalog.setOutputTypes(java.util.Set.of(OutputType.FILE));
        }
        if (catalog.getDownstreamContentValue() == null) {
            catalog.setContentMode(ContentMode.TEXT_IMAGE);
        }
        if (catalog.getMaxVersions() == null) {
            catalog.setMaxVersions(DEFAULT_MAX_VERSIONS);
        }
        if (catalog.getImageEnabled() == null) {
            catalog.setImageEnabled(Boolean.TRUE);
        }
        Date now = new Date();
        // written once and never again: a catalog edited five times still says when it was made
        if (catalog.getCreatedAt() == null) {
            catalog.setCreatedAt(now);
        }
        catalog.setUpdatedAt(now);
        return catalogRepository.save(catalog);
    }

    /**
     * One catalog per name, enforced here rather than left to {@code uk_catalog_name}.
     *
     * <p>
     * The constraint is declared on the entity and H2, MySQL, PostgreSQL and the rest all create
     * it. SQLite does not: Hibernate's community dialect emits neither the unique constraint nor
     * a unique index for it, so the generated schema -- and {@code docs/sql/schema-sqlite.sql},
     * which is generated from the same entities -- has no uniqueness of any kind. A deployment on
     * SQLite would therefore accept two catalogs called the same thing, and
     * {@code findByName} would answer with whichever the query happened to reach first.
     *
     * <p>
     * That matters more in a cluster than on one machine. Nodes are free to use different
     * databases, and a name collision that one node refuses and another accepts is precisely the
     * divergence the replication in {@code greenfinger-cluster} then has to repair. Checking here
     * makes every dialect behave like the strictest of them.
     *
     * <p>
     * A check and then a write is not atomic, and on the dialects that create the constraint the
     * constraint is still what makes it safe. This closes the gap on the one that does not; two
     * catalogs created in the same millisecond on two connections to one SQLite file remain
     * possible in theory, and the reconciler in the cluster module is what notices.
     */
    private void requireNameIsFree(Catalog catalog) {
        if (catalog.getName() == null) {
            return;
        }
        catalogRepository.findByNameIgnoreCase(catalog.getName())
                .filter(other -> !Objects.equals(other.getId(), catalog.getId()))
                .ifPresent(other -> {
                    throw new DataIntegrityViolationException(
                            "unique index or primary key violation: uk_catalog_name '"
                                    + catalog.getName() + "' is already held by " + other.getId());
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Catalog> findById(String id) {
        return catalogRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Catalog> findByName(String name) {
        return catalogRepository.findByNameIgnoreCase(name);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Catalog> findAll() {
        return catalogRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findAllCategories() {
        return catalogRepository.findAll().stream().map(Catalog::getCat).filter(Objects::nonNull)
                .distinct().sorted().toList();
    }

    @Override
    @Transactional
    public boolean deleteById(String id) {
        if (!catalogRepository.existsById(id)) {
            return false;
        }
        catalogRepository.deleteById(id);
        return true;
    }

    @Override
    @Transactional
    public int incrementIndexVersion(String id) {
        Catalog catalog = require(id);
        int next = (catalog.getIndexVersion() != null ? catalog.getIndexVersion() : 0) + 1;
        catalog.setIndexVersion(next);
        catalog.setUpdatedAt(new Date());
        catalogRepository.save(catalog);
        return next;
    }

    @Override
    @Transactional
    public void publishSearchVersion(String id, int version) {
        Catalog catalog = require(id);
        catalog.setSearchVersion(version);
        catalog.setLastIndexed(new Date());
        catalogRepository.save(catalog);
    }

    @Override
    @Transactional
    public void resetVersions(String id) {
        Catalog catalog = require(id);
        catalog.setIndexVersion(0);
        catalog.setSearchVersion(-1);
        catalog.setLastIndexed(null);
        catalogRepository.save(catalog);
    }

    @Override
    @Transactional
    public void setRunningState(String id, String runningState) {
        Catalog catalog = require(id);
        catalog.setRunningState(runningState);
        catalogRepository.save(catalog);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Catalog> findRunning() {
        return catalogRepository.findByRunningStateNotNull().stream()
                .filter(c -> !com.github.greenfinger.core.WebCrawlerConstants.RUNNING_STATE_NONE
                        .equalsIgnoreCase(c.getRunningState()))
                .toList();
    }

    private Catalog require(String id) {
        return catalogRepository.findById(id)
                .orElseThrow(() -> new CatalogDetailsNotFoundException("No catalog: " + id));
    }

}
