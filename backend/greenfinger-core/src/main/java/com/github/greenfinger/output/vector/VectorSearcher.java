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

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.github.greenfinger.output.OutputProperties;
import lombok.RequiredArgsConstructor;

/**
 * Semantic and cross modal search.
 *
 * <p>
 * Two things happen here that a vector store cannot do on its own. It over-fetches and re-ranks, so
 * that detail pages come above listings -- a listing's chunks match a query as readily as the
 * article they link to, and a vector store has no equivalent of Elasticsearch's function_score.
 * And it keeps one hit per page, since twenty chunks of the same article are one result to a
 * reader.
 *
 * <p>
 * Searching for images by words is not the same call as searching for text: the two live in
 * different spaces, so the query has to be encoded by the model that produced the image vectors --
 * {@link EmbeddingClient#queryToImageVector} -- and passing a text-space vector to the image
 * collection would return noise rather than an error.
 * 
 * @Description: VectorSearcher
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@RequiredArgsConstructor
public class VectorSearcher {

    /** How much wider to search before re-ranking, so the reordering has something to work with. */
    private static final int OVER_FETCH = 4;

    private final OutputProperties.Vector config;
    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;

    public String textCollection() {
        return config.getTextCollection() + "_" + embeddingClient.textDimensions();
    }

    public String imageCollection() {
        return config.getImageCollection() + "_" + embeddingClient.imageDimensions();
    }

    /**
     * @param preferDetailPages push articles above listings; on unless a caller wants the raw order
     */
    public List<VectorHit> searchText(String keyword, List<String> catalogVersions, int limit,
            boolean preferDetailPages) throws Exception {
        return searchText(keyword, catalogVersions, limit, 0, preferDetailPages);
    }

    /**
     * @param offset how many results to skip, for the second page and beyond
     */
    public List<VectorHit> searchText(String keyword, List<String> catalogVersions, int limit,
            int offset, boolean preferDetailPages) throws Exception {
        float[] query = embeddingClient.queryToVector(keyword);
        // the whole span is fetched and then skipped over locally rather than pushed down as a
        // store offset: re-ranking and one-result-per-page both happen after the store has
        // answered, so a store-side offset would skip rows this method was going to remove anyway
        // and the second page would start in the wrong place
        List<VectorHit> hits = vectorStore.search(textCollection(), query,
                (offset + limit) * OVER_FETCH, catalogVersions);
        return dedupeByResource(rank(hits, preferDetailPages), offset, limit);
    }

    /**
     * Finds pictures by describing them. Requires an embedding client that does images; a text-only
     * one refuses here rather than returning something meaningless.
     */
    public List<VectorHit> searchImages(String keyword, List<String> catalogVersions, int limit)
            throws Exception {
        return searchImages(keyword, catalogVersions, limit, 0);
    }

    /**
     * @param offset how many of the nearest pictures to skip. Pushed down to the store, since
     *        nothing is re-ranked or removed on the way back
     */
    public List<VectorHit> searchImages(String keyword, List<String> catalogVersions, int limit,
            int offset) throws Exception {
        if (!embeddingClient.supportsImages()) {
            throw new UnsupportedOperationException(embeddingClient.getName()
                    + " does not embed images, so images cannot be searched by description."
                    + " Configure greenfinger.embedding.provider=local.");
        }
        float[] query = embeddingClient.queryToImageVector(keyword);
        return vectorStore.search(imageCollection(), query, limit, offset, catalogVersions);
    }

    /**
     * Similarity multiplied by how article-like the page is. The factor spans 0.5 to 1.5, enough to
     * reorder neighbours of comparable similarity and not enough to promote an unrelated one.
     */
    private List<VectorHit> rank(List<VectorHit> hits, boolean preferDetailPages) {
        if (!preferDetailPages) {
            return hits;
        }
        return hits.stream()
                .sorted(Comparator.<VectorHit>comparingDouble(
                        hit -> -(hit.score() * (1.5d - hit.linkDensity()))).thenComparing(
                                VectorHit::id))
                .toList();
    }

    /**
     * One result per page. A long article contributes many chunks, and a reader wants the article
     * once.
     */
    private List<VectorHit> dedupeByResource(List<VectorHit> hits, int offset, int limit) {
        Set<String> seen = new LinkedHashSet<>();
        return hits.stream().filter(hit -> {
            String resourceId = hit.text("resourceId");
            return resourceId == null || seen.add(resourceId);
        }).skip(offset).limit(limit).toList();
    }

}
