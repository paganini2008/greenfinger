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

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.output.FileLayout;
import com.github.greenfinger.output.blob.LocalBlobStore;
import com.github.greenfinger.service.StorageUsageService.CatalogUsage;
import com.github.greenfinger.service.StorageUsageService.StorageUsage;

/**
 * Against a real store on a real directory. The measuring is a walk over paths, and a stub that
 * returned a list of strings would be testing the stub's idea of the layout rather than the
 * layout.
 * 
 * @Description: StorageUsageServiceTest
 * @Author: Fred Feng
 * @Date: 05/09/2026
 * @Version 2.0.0
 */
class StorageUsageServiceTest {

    @TempDir
    Path root;

    private final StorageUsageService service = new StorageUsageService(null, null);

    private Catalog catalog(String id, String name) {
        Catalog catalog = new Catalog();
        catalog.setId(id);
        catalog.setName(name);
        return catalog;
    }

    @Test
    @DisplayName("pages and images are counted apart, and a page is one page rather than two files")
    void countsPagesAndImages() throws Exception {
        LocalBlobStore store = new LocalBlobStore(root);
        store.afterPropertiesSet();
        FileLayout layout = new FileLayout("cat-a", 0, 2);
        // a page is written twice, as html and as text: counting files would say two pages
        store.write(layout.html("11111111-1111-1111-1111-111111111111"), new byte[100],
                "text/html");
        store.writeText(layout.text("11111111-1111-1111-1111-111111111111"), "some text");
        store.write(layout.html("22222222-2222-2222-2222-222222222222"), new byte[50],
                "text/html");
        store.writeText(layout.text("22222222-2222-2222-2222-222222222222"), "more text");
        store.write(layout.image("33333333-3333-3333-3333-333333333333", "image/png", "a.png"),
                new byte[10], "image/png");

        StorageUsage usage = service.usage(store, List.of(catalog("cat-a", "A")));

        assertThat(usage.getTarget()).isEqualTo("local");
        assertThat(usage.getPageCount()).isEqualTo(2);
        assertThat(usage.getImageCount()).isEqualTo(1);
        assertThat(usage.getBytes()).isGreaterThan(160L);
        assertThat(usage.getCatalogs()).singleElement()
                .extracting(CatalogUsage::getCatalogName).isEqualTo("A");
    }

    @Test
    @DisplayName("every version counts, because an unpruned one is still occupying the disk")
    void countsAcrossVersions() throws Exception {
        LocalBlobStore store = new LocalBlobStore(root);
        store.afterPropertiesSet();
        for (int version = 0; version < 3; version++) {
            FileLayout layout = new FileLayout("cat-a", version, 2);
            store.write(layout.html("4444444" + version + "-4444-4444-4444-444444444444"),
                    new byte[10], "text/html");
        }

        StorageUsage usage = service.usage(store, List.of(catalog("cat-a", "A")));

        assertThat(usage.getPageCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("a catalog that wrote nothing reports zero rather than going missing")
    void aCatalogWithNoFilesStillHasARow() throws Exception {
        LocalBlobStore store = new LocalBlobStore(root);
        store.afterPropertiesSet();

        StorageUsage usage =
                service.usage(store, List.of(catalog("cat-a", "A"), catalog("cat-b", "B")));

        assertThat(usage.getCatalogs()).hasSize(2);
        assertThat(usage.getPageCount()).isZero();
        assertThat(usage.getBytes()).isZero();
    }

    @Test
    @DisplayName("the totals are the sum of the rows, so the two never disagree on screen")
    void totalsAddUp() throws Exception {
        LocalBlobStore store = new LocalBlobStore(root);
        store.afterPropertiesSet();
        store.write(new FileLayout("cat-a", 0, 2).html("55555555-5555-5555-5555-555555555555"),
                new byte[10], "text/html");
        store.write(new FileLayout("cat-b", 0, 2).html("66666666-6666-6666-6666-666666666666"),
                new byte[10], "text/html");

        StorageUsage usage =
                service.usage(store, List.of(catalog("cat-a", "A"), catalog("cat-b", "B")));

        assertThat(usage.getPageCount())
                .isEqualTo(usage.getCatalogs().stream().mapToLong(CatalogUsage::getPageCount).sum())
                .isEqualTo(2);
        assertThat(usage.getBytes())
                .isEqualTo(usage.getCatalogs().stream().mapToLong(CatalogUsage::getBytes).sum());
    }

}
