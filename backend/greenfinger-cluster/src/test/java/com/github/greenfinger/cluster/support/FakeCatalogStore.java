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

package com.github.greenfinger.cluster.support;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.model.Catalog;

/**
 * One node's catalog table, with the two properties of a real one that the bugs turned on.
 *
 * <p>
 * <b>The unique name index.</b> {@code crawler_catalog} carries {@code uk_catalog_name}, and that
 * constraint is not a detail -- it is what actually broke. A node holding a stale row refused the
 * new catalog that replaced it, logged the rejection and carried on, and the crawl then ran
 * without that node while reporting success. A double that accepts every row cannot fail that
 * way, so this one throws exactly where the database does.
 *
 * <p>
 * <b>The local write stamp.</b> Every store stamps {@code updatedAt} with its own clock as it
 * writes, including when the row it is writing came from somewhere else. That is why the
 * reconciler cannot compare stamps alone, and a double that preserved the incoming stamp would
 * make the wrong design look right.
 *
 * @Description: FakeCatalogStore
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
public class FakeCatalogStore implements CatalogStore {

    private final String name;
    private final Map<String, Catalog> catalogs = new LinkedHashMap<>();

    private int writes;
    private int rejections;

    public FakeCatalogStore(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Catalog save(Catalog catalog) {
        Catalog clash = catalogs.values().stream()
                .filter(one -> one.getName() != null
                        && one.getName().equalsIgnoreCase(catalog.getName()))
                .filter(one -> !Objects.equals(one.getId(), catalog.getId())).findFirst()
                .orElse(null);
        if (clash != null) {
            rejections++;
            throw new IllegalStateException(
                    "unique index or primary key violation: uk_catalog_name '" + catalog.getName()
                            + "' is held by " + clash.getId());
        }
        writes++;
        Catalog copy = copyOf(catalog);
        if (copy.getCreatedAt() == null) {
            copy.setCreatedAt(new Date());
        }
        // stamped with this node's clock, exactly as a real store does
        copy.setUpdatedAt(new Date());
        catalogs.put(copy.getId(), copy);
        return copy;
    }

    /**
     * Stored by value, so a test holding the object it saved cannot mutate the table behind the
     * store's back -- which is a thing a real one makes impossible and an in-memory map does not.
     */
    private static Catalog copyOf(Catalog catalog) {
        Catalog copy = new Catalog();
        copy.setId(catalog.getId());
        copy.setName(catalog.getName());
        copy.setUrl(catalog.getUrl());
        copy.setCat(catalog.getCat());
        copy.setPathPattern(catalog.getPathPattern());
        copy.setIndexVersion(catalog.getIndexVersion());
        copy.setSearchVersion(catalog.getSearchVersion());
        copy.setRunningState(catalog.getRunningState());
        copy.setCreatedAt(catalog.getCreatedAt());
        copy.setUpdatedAt(catalog.getUpdatedAt());
        return copy;
    }

    @Override
    public Optional<Catalog> findById(String id) {
        return Optional.ofNullable(catalogs.get(id)).map(FakeCatalogStore::copyOf);
    }

    @Override
    public Optional<Catalog> findByName(String catalogName) {
        return catalogs.values().stream()
                .filter(one -> catalogName.equalsIgnoreCase(one.getName()))
                .map(FakeCatalogStore::copyOf).findFirst();
    }

    @Override
    public List<Catalog> findAll() {
        return catalogs.values().stream().map(FakeCatalogStore::copyOf).toList();
    }

    @Override
    public List<String> findAllCategories() {
        return catalogs.values().stream().map(Catalog::getCat).filter(Objects::nonNull).distinct()
                .sorted().toList();
    }

    @Override
    public boolean deleteById(String id) {
        return catalogs.remove(id) != null;
    }

    @Override
    public int incrementIndexVersion(String id) {
        Catalog catalog = catalogs.get(id);
        int version = (catalog.getIndexVersion() == null ? 0 : catalog.getIndexVersion()) + 1;
        catalog.setIndexVersion(version);
        catalog.setUpdatedAt(new Date());
        return version;
    }

    @Override
    public void publishSearchVersion(String id, int version) {
        Catalog catalog = catalogs.get(id);
        catalog.setSearchVersion(version);
        catalog.setUpdatedAt(new Date());
    }

    @Override
    public void resetVersions(String id) {
        Catalog catalog = catalogs.get(id);
        catalog.setIndexVersion(0);
        catalog.setSearchVersion(-1);
        catalog.setUpdatedAt(new Date());
    }

    @Override
    public void setRunningState(String id, String runningState) {
        Catalog catalog = catalogs.get(id);
        catalog.setRunningState(runningState);
        catalog.setUpdatedAt(new Date());
    }

    @Override
    public List<Catalog> findRunning() {
        return catalogs.values().stream().filter(one -> one.getRunningState() != null)
                .map(FakeCatalogStore::copyOf).toList();
    }

    /** Rows accepted. */
    public int writes() {
        return writes;
    }

    /** Rows the unique name index refused. */
    public int rejections() {
        return rejections;
    }

    public int size() {
        return catalogs.size();
    }

}
