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

package com.github.greenfinger.service;

import java.util.ArrayList;
import java.util.List;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.output.BlobStore;
import com.github.greenfinger.core.output.FileLayout;
import com.github.greenfinger.core.utils.BeanLifeCycleUtils;
import com.github.greenfinger.output.OutputFactory;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * How much of the blob store a crawl has taken, and in how many files.
 *
 * <p>
 * Counted from the store rather than from the database, because the two answer different
 * questions. The database says what this node recorded; the store says what is actually occupying
 * the disk or the bucket, including the versions nobody has pruned yet and, on a shared MinIO,
 * the files the other nodes wrote. Somebody asking how much space this is taking wants the
 * second.
 *
 * <p>
 * Walked on demand and never on a timer. Listing a prefix is a directory walk on local disk and a
 * paged list call on MinIO, and both cost real time on a large crawl -- acceptable when a person
 * asked for the number, wrong to pay every few seconds because a page is open.
 *
 * @Description: StorageUsageService
 * @Author: Fred Feng
 * @Date: 05/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class StorageUsageService {

    private final OutputFactory outputFactory;
    private final CatalogAdminService catalogAdminService;

    public StorageUsageService(OutputFactory outputFactory,
            CatalogAdminService catalogAdminService) {
        this.outputFactory = outputFactory;
        this.catalogAdminService = catalogAdminService;
    }

    /**
     * One row per catalog, plus the totals.
     *
     * @param catalogId one catalog, or null for every one of them.
     */
    public StorageUsage usage(String catalogId) {
        BlobStore blobStore = null;
        try {
            blobStore = outputFactory.getBlobStore();
            BeanLifeCycleUtils.afterPropertiesSet(blobStore);
            List<Catalog> wanted = catalogAdminService.findAll().stream()
                    .filter(c -> catalogId == null || catalogId.equals(c.getId())).toList();
            return usage(blobStore, wanted);
        } catch (Exception e) {
            log.warn("Could not measure the blob store: {}", e.getMessage());
            return StorageUsage.builder().target("unknown").catalogs(List.of()).build();
        } finally {
            BeanLifeCycleUtils.destroyQuietly(blobStore);
        }
    }

    /** The measuring itself, against a store somebody else opened and will close. */
    public StorageUsage usage(BlobStore blobStore, List<Catalog> wanted) {
        List<CatalogUsage> catalogs = new ArrayList<>();
        for (Catalog catalog : wanted) {
            catalogs.add(measure(blobStore, catalog));
        }
        long pages = catalogs.stream().mapToLong(CatalogUsage::getPageCount).sum();
        long images = catalogs.stream().mapToLong(CatalogUsage::getImageCount).sum();
        long bytes = catalogs.stream().mapToLong(CatalogUsage::getBytes).sum();
        return StorageUsage.builder().target(blobStore.getName()).catalogs(catalogs)
                .pageCount(pages).imageCount(images).bytes(bytes).build();
    }

    private CatalogUsage measure(BlobStore blobStore, Catalog catalog) {
        // Across every version rather than only the one being served: an unpruned version is
        // still occupying the disk, and hiding it is how a store fills up unnoticed.
        String prefix = catalog.getId();
        long pages = 0;
        long images = 0;
        long bytes = 0;
        try {
            bytes = blobStore.sizeOfPrefix(prefix);
            for (String path : blobStore.listPrefix(prefix)) {
                if (path.contains("/" + FileLayout.PAGES + "/")) {
                    // html and txt are two files for one page, and a page is what was asked for
                    if (path.endsWith(".html")) {
                        pages++;
                    }
                } else if (path.contains("/" + FileLayout.IMAGES + "/")) {
                    images++;
                }
            }
        } catch (Exception e) {
            log.warn("Could not measure catalog '{}': {}", catalog.getName(), e.getMessage());
        }
        return CatalogUsage.builder().catalogId(catalog.getId()).catalogName(catalog.getName())
                .pageCount(pages).imageCount(images).bytes(bytes).build();
    }

    /**
     * 
     * @Description: StorageUsage
     * @Author: Fred Feng
     * @Date: 05/09/2026
     * @Version 2.0.0
     */
    @Getter
    @Builder
    public static class StorageUsage {

        /** What the blob store calls itself: {@code local} or {@code minio}. */
        private final String target;
        private final long pageCount;
        private final long imageCount;
        private final long bytes;
        private final List<CatalogUsage> catalogs;
    }

    /**
     * 
     * @Description: CatalogUsage
     * @Author: Fred Feng
     * @Date: 05/09/2026
     * @Version 2.0.0
     */
    @Getter
    @Builder
    public static class CatalogUsage {

        private final String catalogId;
        private final String catalogName;
        private final long pageCount;
        private final long imageCount;
        private final long bytes;
    }

}
