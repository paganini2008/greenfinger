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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHitCountCollectorManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.index.LuceneIndexes;
import lombok.extern.slf4j.Slf4j;

/**
 * Semantic and cross modal search, embedded. The default, and the other half of what a fresh clone
 * gets without installing anything.
 *
 * <p>
 * Lucene's HNSW is the implementation Elasticsearch's own knn search is built on, so this is not a
 * lesser engine standing in for a real one -- it is the same engine, in one process instead of a
 * cluster. A collection is a directory, the vectors are one field, and the payload rides along as
 * json because nothing filters on its interior.
 *
 * <p>
 * Widths are carried in the collection name, {@code greenfinger_text_384}, exactly as they are for
 * Qdrant. Lucene requires every vector in a field to be the same width, so a model change that
 * would corrupt a collection instead lands in a new one, and {@link #ensureCollection} refuses the
 * mismatch out loud rather than letting the writer discover it mid-crawl.
 * 
 * @Description: LuceneVectorStore
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class LuceneVectorStore implements VectorStore {

    static final String FIELD_ID = "id";
    static final String FIELD_VECTOR = "vector";
    static final String FIELD_CATALOG_VERSION = "catalogVersion";
    static final String FIELD_CATALOG_ID = "catalogId";
    static final String FIELD_PAYLOAD = "payload";

    private final OutputProperties.Vector.Lucene config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VectorSimilarityFunction similarity;

    private volatile LuceneIndexes indexes;

    public LuceneVectorStore(OutputProperties.Vector.Lucene config) {
        this.config = config;
        this.similarity = similarityOf(config.getSimilarity());
    }

    static VectorSimilarityFunction similarityOf(String name) {
        String value = name == null ? "cosine" : name.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "", "cosine" -> VectorSimilarityFunction.COSINE;
            case "dot", "dot_product", "dotproduct" -> VectorSimilarityFunction.DOT_PRODUCT;
            case "euclidean", "euclid", "l2" -> VectorSimilarityFunction.EUCLIDEAN;
            default -> throw new IllegalArgumentException("Unknown lucene vector similarity '"
                    + name + "'. Use cosine, dot or euclidean.");
        };
    }

    @Override
    public String getName() {
        return "lucene";
    }

    @Override
    public void afterPropertiesSet() {
        indexes();
    }

    /**
     * Deliberately does not close anything.
     *
     * <p>
     * Every caller of a vector store initialises it and destroys it around its own work -- a
     * crawl, a replay, one search -- which is right for Qdrant, where each of those is a client
     * over http. Here they are all the same open directory, and a search that ran during a crawl
     * would otherwise close the index the crawl is still writing to. The directories are released
     * once, by {@link LuceneIndexes#closeShared()}, when the process ends.
     */
    @Override
    public void destroy() {
        this.indexes = null;
    }

    /**
     * The directories, opened on first use rather than only by {@code afterPropertiesSet}.
     *
     * <p>
     * Because not every caller opens one. A vector store used to be an http client, so the two
     * places that build one -- the output channel and the searcher -- reasonably assumed
     * constructing it was enough, and the channel calls {@code ensureCollection} before anything
     * has initialised it. Failing there would be correct and useless: the crawl skips the vector
     * output for the whole run and says so afterwards.
     *
     * <p>
     * A keyword analyzer, because nothing in these documents is prose: the ids and the
     * catalogVersion are matched whole, and the chunk text rides in the payload unindexed.
     */
    private LuceneIndexes indexes() {
        LuceneIndexes open = indexes;
        if (open == null) {
            synchronized (this) {
                open = indexes;
                if (open == null) {
                    open = LuceneIndexes.shared(config.getDirectory(), new KeywordAnalyzer());
                    indexes = open;
                }
            }
        }
        return open;
    }

    @Override
    public void ensureCollection(String collection, int dimensions) throws IOException {
        indexes().writer(collection);
        int existing = dimensionsOf(collection);
        if (existing > 0 && existing != dimensions) {
            throw new WebCrawlerException(String.format(
                    "Collection '%s' holds %d dimensional vectors but the embedding model produces"
                            + " %d. Delete the collection, or point the model back at what wrote"
                            + " it.",
                    collection, existing, dimensions));
        }
    }

    /**
     * The width of what is already in a collection, or 0 when it is empty.
     */
    private int dimensionsOf(String collection) throws IOException {
        IndexSearcher searcher = indexes().acquire(collection);
        if (searcher == null) {
            return 0;
        }
        try {
            for (LeafReaderContext leaf : searcher.getIndexReader().leaves()) {
                FieldInfo info = leaf.reader().getFieldInfos().fieldInfo(FIELD_VECTOR);
                if (info != null && info.getVectorDimension() > 0) {
                    return info.getVectorDimension();
                }
            }
            return 0;
        } finally {
            indexes().release(collection, searcher);
        }
    }

    @Override
    public void upsert(String collection, List<VectorPoint> points) throws Exception {
        if (points == null || points.isEmpty()) {
            return;
        }
        var writer = indexes().writer(collection);
        for (VectorPoint point : points) {
            Document document = new Document();
            document.add(new StringField(FIELD_ID, point.getId(), Field.Store.YES));
            document.add(new KnnFloatVectorField(FIELD_VECTOR, point.getVector(), similarity));
            // both, because the three delete semantics need both: one version, and every version
            // of a catalog at once
            keyword(document, point, FIELD_CATALOG_VERSION);
            keyword(document, point, FIELD_CATALOG_ID);
            document.add(new StoredField(FIELD_PAYLOAD,
                    objectMapper.writeValueAsString(point.getPayload())));
            // by id, so a re-crawl or a replay replaces rather than adds: the ids are derived from
            // the url and the chunk number, so the same chunk always lands on the same document
            writer.updateDocument(new Term(FIELD_ID, point.getId()), document);
        }
        indexes().commit(collection);
    }

    private void keyword(Document document, VectorPoint point, String field) {
        Object value = point.getPayload() != null ? point.getPayload().get(field) : null;
        if (value != null) {
            document.add(new StringField(field, String.valueOf(value), Field.Store.YES));
        }
    }

    @Override
    public long deleteByCatalogVersion(String collection, String catalogVersion) throws Exception {
        if (!indexes().exists(collection)) {
            return 0L;
        }
        long counted = count(collection, catalogVersion);
        indexes().writer(collection)
                .deleteDocuments(new Term(FIELD_CATALOG_VERSION, catalogVersion));
        indexes().commit(collection);
        return counted;
    }

    @Override
    public long deleteByCatalog(String collection, String catalogId) throws Exception {
        if (!indexes().exists(collection)) {
            return 0L;
        }
        long counted = countByCatalog(collection, catalogId);
        indexes().writer(collection).deleteDocuments(new Term(FIELD_CATALOG_ID, catalogId));
        indexes().commit(collection);
        return counted;
    }

    @Override
    public long countByCatalog(String collection, String catalogId) throws Exception {
        return countBy(collection, FIELD_CATALOG_ID, catalogId);
    }

    @Override
    public long count(String collection, String catalogVersion) throws Exception {
        return countBy(collection, FIELD_CATALOG_VERSION, catalogVersion);
    }

    private long countBy(String collection, String field, String value) throws Exception {
        IndexSearcher searcher = indexes().acquire(collection);
        if (searcher == null) {
            return 0L;
        }
        try {
            return searcher.search(new TermQuery(new Term(field, value)),
                    new TotalHitCountCollectorManager());
        } finally {
            indexes().release(collection, searcher);
        }
    }

    @Override
    public List<String> collectionsMatching(String prefix) {
        return indexes().names().stream().filter(name -> name.startsWith(prefix)).toList();
    }

    @Override
    public List<VectorHit> search(String collection, float[] vector, int limit, int offset,
            List<String> catalogVersions) throws Exception {
        IndexSearcher searcher = indexes().acquire(collection);
        if (searcher == null) {
            return List.of();
        }
        try {
            int wanted = Math.max(1, limit) + Math.max(0, offset);
            Query query = new KnnFloatVectorQuery(FIELD_VECTOR, vector, wanted,
                    filterOf(catalogVersions));
            TopDocs top = searcher.search(query, wanted);
            return hitsOf(searcher.getIndexReader(), searcher, top, offset, limit);
        } finally {
            indexes().release(collection, searcher);
        }
    }

    /**
     * The versions a search is allowed to see, as the pre-filter Lucene applies while walking the
     * graph rather than afterwards -- filtering after the fact is what makes a knn search return
     * three results when ten were asked for.
     */
    private Query filterOf(List<String> catalogVersions) {
        if (catalogVersions == null || catalogVersions.isEmpty()) {
            return null;
        }
        BooleanQuery.Builder filter = new BooleanQuery.Builder();
        catalogVersions.forEach(catalogVersion -> filter
                .add(new TermQuery(new Term(FIELD_CATALOG_VERSION, catalogVersion)), Occur.SHOULD));
        return filter.build();
    }

    private List<VectorHit> hitsOf(IndexReader reader, IndexSearcher searcher, TopDocs top,
            int offset, int limit) throws IOException {
        List<VectorHit> hits = new ArrayList<>();
        for (int i = Math.max(0, offset); i < top.scoreDocs.length && hits.size() < limit; i++) {
            ScoreDoc hit = top.scoreDocs[i];
            Document document = searcher.storedFields().document(hit.doc);
            hits.add(new VectorHit(document.get(FIELD_ID), hit.score,
                    payloadOf(document.get(FIELD_PAYLOAD))));
        }
        return hits;
    }

    private Map<String, Object> payloadOf(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.debug("Could not read a vector payload: {}", e.getMessage());
            return Map.of();
        }
    }

}
