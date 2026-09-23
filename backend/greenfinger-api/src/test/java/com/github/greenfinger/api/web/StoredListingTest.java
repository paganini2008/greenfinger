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

package com.github.greenfinger.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.Image;
import com.github.greenfinger.core.model.Resource;
import com.github.greenfinger.core.model.ResourceImage;
import com.github.greenfinger.core.record.ResourceRecord;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.output.vector.VectorHit;
import com.github.greenfinger.service.CatalogAdminService;

/**
 * What a blank search box is answered with.
 *
 * <p>
 * The paging is the part worth testing and the part that was wrong first time: the store turns an
 * offset into a page number by dividing by the limit, so a running offset carried across catalogs
 * asks the second one for a page that cannot exist. Everything after the first catalog then
 * returned nothing, quietly, and only a second catalog in the fixture shows it.
 * 
 * @Description: StoredListingTest
 * @Author: Fred Feng
 * @Date: 23/09/2026
 * @Version 2.0.0
 */
class StoredListingTest {

    private static final String A = "catalog-a";
    private static final String B = "catalog-b";

    private final Map<String, List<ResourceRecord>> rows = new LinkedHashMap<>();
    private StoredListing listing;

    @BeforeEach
    void setUp() {
        ResourceRecordStore store = mock(ResourceRecordStore.class);
        when(store.load(anyString(), anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            String catalogId = invocation.getArgument(0);
            int version = invocation.getArgument(1);
            int offset = invocation.getArgument(2);
            int limit = invocation.getArgument(3);
            List<ResourceRecord> all = rows.getOrDefault(catalogId + ":" + version, List.of());
            if (offset >= all.size()) {
                return List.of();
            }
            return all.subList(offset, Math.min(all.size(), offset + limit));
        });

        CatalogAdminService catalogs = mock(CatalogAdminService.class);
        when(catalogs.findAll()).thenReturn(List.of(named(A, "Alpha"), named(B, "Beta")));
        listing = new StoredListing(store, catalogs);
    }

    private static Catalog named(String id, String name) {
        Catalog catalog = new Catalog();
        catalog.setId(id);
        catalog.setName(name);
        return catalog;
    }

    /** @param images how many pictures this page carries */
    private void give(String catalogId, int version, int pages, int images) {
        List<ResourceRecord> records = new ArrayList<>();
        for (int i = 0; i < pages; i++) {
            Resource resource = new Resource();
            resource.setId(catalogId + "-r" + i);
            resource.setCatalogId(catalogId);
            resource.setVersion(version);
            resource.setUrl("https://" + catalogId + "/" + i);
            resource.setTitle("Page " + i);
            resource.setCat("tech");
            List<ResourceRecord.ImageRecord> pictures = new ArrayList<>();
            for (int j = 0; j < images; j++) {
                Image image = new Image();
                image.setId(resource.getId() + "-img" + j);
                image.setImageFilePath("/p/" + image.getId());
                ResourceImage reference = new ResourceImage();
                reference.setImageId(image.getId());
                reference.setResourceId(resource.getId());
                reference.setSourceUrl("https://" + catalogId + "/" + i + "/" + j + ".jpg");
                pictures.add(new ResourceRecord.ImageRecord(image, reference));
            }
            records.add(new ResourceRecord(resource, pictures));
        }
        rows.put(catalogId + ":" + version, records);
    }

    @Test
    @DisplayName("every page of one catalog, with the keys the semantic hits carry")
    void listsPages() {
        give(A, 0, 3, 0);

        List<VectorHit> hits = listing.pages(List.of(A + ":0"), 10, 0);

        assertThat(hits).hasSize(3);
        assertThat(hits.get(0).payload()).containsEntry("catalog", "Alpha")
                .containsEntry("catalogVersion", A + ":0").containsKeys("url", "title", "cat");
        // nothing was compared, so nothing is claimed about similarity
        assertThat(hits).allSatisfy(hit -> assertThat(hit.score()).isZero());
    }

    @Test
    @DisplayName("a second catalog is reached: the page index restarts for each one")
    void reachesEveryCatalog() {
        give(A, 0, 2, 0);
        give(B, 1, 2, 0);

        List<VectorHit> hits = listing.pages(List.of(A + ":0", B + ":1"), 10, 0);

        assertThat(hits).hasSize(4);
        assertThat(hits).extracting(hit -> hit.payload().get("catalog"))
                .containsExactly("Alpha", "Alpha", "Beta", "Beta");
    }

    @Test
    @DisplayName("size is a number of hits, and it stops there")
    void stopsAtSize() {
        give(A, 0, 5, 0);
        give(B, 1, 5, 0);

        assertThat(listing.pages(List.of(A + ":0", B + ":1"), 3, 0)).hasSize(3);
    }

    @Test
    @DisplayName("offset skips hits, across catalogs")
    void skipsTheOffset() {
        give(A, 0, 2, 0);
        give(B, 1, 2, 0);

        List<VectorHit> hits = listing.pages(List.of(A + ":0", B + ":1"), 10, 3);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).payload()).containsEntry("catalog", "Beta");
    }

    @Test
    @DisplayName("pictures are counted one by one, not one per page")
    void listsImages() {
        // two pages of four pictures each: eight hits, not two
        give(A, 0, 2, 4);

        List<VectorHit> hits = listing.images(List.of(A + ":0"), 20, 0);

        assertThat(hits).hasSize(8);
        assertThat(hits.get(0).payload()).containsKeys("imageId", "imageFilePath", "imageUrl")
                .containsEntry("catalog", "Alpha");
    }

    @Test
    @DisplayName("a page with no pictures costs nothing")
    void skipsPagesWithoutPictures() {
        give(A, 0, 3, 0);
        give(B, 1, 1, 2);

        List<VectorHit> hits = listing.images(List.of(A + ":0", B + ":1"), 20, 0);

        assertThat(hits).hasSize(2);
        assertThat(hits).allSatisfy(
                hit -> assertThat(hit.payload()).containsEntry("catalog", "Beta"));
    }

    @Test
    @DisplayName("size caps the pictures even when one page carries more than it")
    void capsImagesWithinOnePage() {
        give(A, 0, 1, 9);

        assertThat(listing.images(List.of(A + ":0"), 4, 0)).hasSize(4);
    }

    @Test
    @DisplayName("nothing stored is an empty answer rather than a failure")
    void emptyIsEmpty() {
        assertThat(listing.pages(List.of(A + ":0"), 10, 0)).isEmpty();
        assertThat(listing.images(List.of(), 10, 0)).isEmpty();
    }

    @Test
    @DisplayName("a version that is not a number is stepped over")
    void ignoresRubbishVersions() {
        give(A, 0, 1, 0);

        assertThat(listing.pages(List.of("no-colon", A + ":not-a-number", A + ":0"), 10, 0))
                .hasSize(1);
    }

}
