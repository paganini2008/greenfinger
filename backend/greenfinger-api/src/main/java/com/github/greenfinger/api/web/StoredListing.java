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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.record.ResourceRecord;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.output.vector.VectorHit;
import com.github.greenfinger.service.CatalogAdminService;
import lombok.RequiredArgsConstructor;

/**
 * What "everything" is, when the question is blank.
 *
 * <p>
 * Words already answered a blank box: an empty keyword is a match-all, and the index has always
 * understood it. The two vector modes did not, because similarity has no match-all -- "things
 * like this" needs a this. That left a box behaving one way under one tab and another way under
 * the next, which is the sort of inconsistency somebody has to learn rather than notice.
 *
 * <p>
 * So a blank box is answered from the table instead of from the vector store. It is the same set
 * -- every page, every picture, of the versions search is serving -- shaped as the hits the two
 * modes already return, so the page renders it with the cards it already has. The score is zero
 * and means nothing, because nothing was compared; the front end leaves the similarity off when
 * it did not ask a question.
 *
 * @Description: StoredListing
 * @Author: Fred Feng
 * @Date: 23/09/2026
 * @Version 2.0.0
 */
@RequiredArgsConstructor
public class StoredListing {

    private final ResourceRecordStore recordStore;
    private final CatalogAdminService catalogAdminService;

    /** How many rows are read from the store at a time while filling a page of hits. */
    private static final int PAGE = 100;

    /** Every page of the versions given, oldest first, as the semantic mode's hits. */
    public List<VectorHit> pages(List<String> catalogVersions, int size, int offset) {
        List<VectorHit> hits = new ArrayList<>();
        walk(catalogVersions, size, offset, hits, (record, catalogName, into) -> into
                .add(new VectorHit(record.resource().getId(), 0d, pageOf(record, catalogName))));
        return hits;
    }

    /** Every picture of the versions given, as the image mode's hits. */
    public List<VectorHit> images(List<String> catalogVersions, int size, int offset) {
        List<VectorHit> hits = new ArrayList<>();
        walk(catalogVersions, size, offset, hits, (record, catalogName, into) -> {
            for (ResourceRecord.ImageRecord image : record.images()) {
                into.add(new VectorHit(image.image().getId(), 0d,
                        imageOf(record, image, catalogName)));
            }
        });
        return hits;
    }

    /**
     * Walks the versions in order until {@code size} hits have been collected.
     *
     * <p>
     * Counted in hits rather than in rows, because a row is one hit under Meaning and a dozen
     * under Pictures, and what the caller asked for is a page of what it is looking at.
     *
     * <p>
     * Read a page of rows at a time rather than all of them: a catalog can hold hundreds of
     * thousands, and "show me everything" is a request for the first page of everything. The page
     * index restarts for each version, which is the detail the first cut got wrong -- the store
     * turns an offset into a page by dividing by the limit, so carrying a running offset across
     * versions asked the second one for a page that was never going to exist, and everything
     * after the first catalog quietly returned nothing.
     */
    private void walk(List<String> catalogVersions, int size, int offset, List<VectorHit> hits,
            Visitor visitor) {
        int skipped = 0;
        for (String catalogVersion : catalogVersions) {
            int colon = catalogVersion.lastIndexOf(':');
            if (colon < 0) {
                continue;
            }
            String catalogId = catalogVersion.substring(0, colon);
            int version;
            try {
                version = Integer.parseInt(catalogVersion.substring(colon + 1));
            } catch (NumberFormatException e) {
                continue;
            }
            String catalogName = nameOf(catalogId);
            for (int page = 0; hits.size() < size; page++) {
                List<ResourceRecord> rows = recordStore.load(catalogId, version, page * PAGE,
                        PAGE);
                if (rows.isEmpty()) {
                    break;
                }
                for (ResourceRecord record : rows) {
                    if (hits.size() >= size) {
                        return;
                    }
                    int before = hits.size();
                    visitor.accept(record, catalogName, hits);
                    // the offset counts hits, and a row that produced none costs nothing
                    while (skipped < offset && hits.size() > before) {
                        hits.remove(before);
                        skipped++;
                    }
                    while (hits.size() > size) {
                        hits.remove(hits.size() - 1);
                    }
                }
            }
            if (hits.size() >= size) {
                return;
            }
        }
    }

    private String nameOf(String catalogId) {
        return catalogAdminService.findAll().stream()
                .filter(one -> catalogId.equals(one.getId())).map(Catalog::getName).findFirst()
                .orElse(catalogId);
    }

    /** The keys the semantic hits carry, so one page renders both without knowing which it got. */
    private static Map<String, Object> pageOf(ResourceRecord record, String catalogName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("catalogVersion",
                record.resource().getCatalogId() + ":" + record.resource().getVersion());
        payload.put("catalogId", record.resource().getCatalogId());
        payload.put("catalog", catalogName);
        payload.put("version", record.resource().getVersion());
        payload.put("resourceId", record.resource().getId());
        payload.put("url", record.resource().getUrl());
        payload.put("title", record.resource().getTitle());
        payload.put("cat", record.resource().getCat());
        payload.put("htmlFilePath", record.resource().getHtmlFilePath());
        payload.put("linkCount", record.resource().getLinkCount());
        payload.put("textLength", record.resource().getTextLength());
        return payload;
    }

    private static Map<String, Object> imageOf(ResourceRecord record,
            ResourceRecord.ImageRecord image, String catalogName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("catalogVersion",
                record.resource().getCatalogId() + ":" + record.resource().getVersion());
        payload.put("catalogId", record.resource().getCatalogId());
        payload.put("catalog", catalogName);
        payload.put("version", record.resource().getVersion());
        payload.put("imageId", image.image().getId());
        payload.put("imageFilePath", image.image().getImageFilePath());
        payload.put("imageUrl", image.reference().getSourceUrl());
        payload.put("contentType", image.image().getContentType());
        payload.put("width", image.image().getWidth());
        payload.put("height", image.image().getHeight());
        payload.put("alt", image.reference().getAltText());
        payload.put("context", image.reference().getContextText());
        payload.put("resourceId", record.resource().getId());
        payload.put("url", record.resource().getUrl());
        payload.put("title", record.resource().getTitle());
        return payload;
    }

    /**
     *
     * @Description: Visitor
     * @Author: Fred Feng
     * @Date: 23/09/2026
     * @Version 2.0.0
     */
    @FunctionalInterface
    private interface Visitor {

        void accept(ResourceRecord record, String catalogName, List<VectorHit> into);
    }

}
