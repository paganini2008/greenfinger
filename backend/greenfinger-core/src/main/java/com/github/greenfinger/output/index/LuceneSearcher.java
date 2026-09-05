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

package com.github.greenfinger.output.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.FunctionScoreQuery;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.search.uhighlight.DefaultPassageFormatter;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.QueryBuilder;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.core.output.IndexAdmin;
import com.github.greenfinger.core.output.SearchRequest;
import com.github.greenfinger.core.output.SearchResponse;
import com.github.greenfinger.core.output.SearchResult;
import com.github.greenfinger.core.output.Searcher;
import com.github.greenfinger.output.OutputProperties;

/**
 * Searches the embedded index.
 *
 * <p>
 * Everything the Elasticsearch searcher does, in the same order and with the same weights: the
 * title above the body, detail pages above listings, matching passages marked, and a cursor so
 * paging past the tenth page costs the same as the first.
 *
 * <h2>Reading several catalogs at once</h2>
 * One index per catalog means a search across three catalogs opens three readers, and they are
 * combined into a {@link MultiReader} rather than searched one at a time and merged afterwards.
 * That is not a convenience: term statistics are what BM25 scores with, and gathering them per
 * index would score a word that is rare overall but common in one small catalog as though it were
 * common everywhere. Combined first, the numbers are the corpus's.
 * 
 * @Description: LuceneSearcher
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
public class LuceneSearcher implements Searcher {

    /**
     * The same ceiling Elasticsearch imposes, kept deliberately.
     *
     * <p>
     * Lucene has no such limit of its own, and could be asked for the ten-thousand-and-first hit.
     * It would do it by collecting every one of the ten thousand before it, which is exactly the
     * cost Elasticsearch refuses to pay silently -- and having the two engines answer the same
     * request differently would be worse than the limit.
     */
    static final int MAX_RESULT_WINDOW = 10_000;

    private final OutputProperties.Index config;
    private final LuceneIndexes indexes;

    public LuceneSearcher(OutputProperties.Index config, LuceneIndexes indexes) {
        this.config = config;
        this.indexes = indexes;
    }

    @Override
    public String getName() {
        return "lucene";
    }

    /**
     * The indices a request touches: the ones its catalogs live in, or all of them.
     */
    private List<String> indicesOf(SearchRequest request) {
        if (request.getCatalogVersions() == null || request.getCatalogVersions().isEmpty()) {
            return indexes.names().stream()
                    .filter(name -> name.startsWith(config.getPrefix() + "-")).toList();
        }
        Set<String> names = new LinkedHashSet<>();
        for (String catalogVersion : request.getCatalogVersions()) {
            names.add(IndexAdmin.indexOf(config.getPrefix(),
                    IndexAdmin.catalogIdOf(catalogVersion)));
        }
        // an index that was never written is not an error, it is a catalog with nothing in it
        return names.stream().filter(indexes::exists).toList();
    }

    @Override
    public SearchResponse search(SearchRequest request) throws Exception {
        long start = System.currentTimeMillis();
        List<String> names = indicesOf(request);
        List<String> borrowed = new ArrayList<>();
        List<IndexSearcher> searchers = new ArrayList<>();
        try {
            for (String name : names) {
                IndexSearcher searcher = indexes.acquire(name);
                if (searcher != null) {
                    borrowed.add(name);
                    searchers.add(searcher);
                }
            }
            if (searchers.isEmpty()) {
                return empty(request, start);
            }
            IndexReader[] readers =
                    searchers.stream().map(IndexSearcher::getIndexReader).toArray(IndexReader[]::new);
            // false: the sub readers belong to the searcher manager that lent them
            IndexSearcher searcher = new IndexSearcher(new MultiReader(readers, false));
            return run(request, searcher, start);
        } finally {
            for (int i = 0; i < borrowed.size(); i++) {
                indexes.release(borrowed.get(i), searchers.get(i));
            }
        }
    }

    private SearchResponse run(SearchRequest request, IndexSearcher searcher, long start)
            throws IOException {
        Query query = queryOf(request, searcher);
        Sort sort = new Sort(SortField.FIELD_SCORE,
                new SortField(LuceneFields.SORT_ID, SortField.Type.STRING));

        int pageSize = Math.max(1, request.getPageSize());
        TopFieldDocs top;
        if (request.getCursor() != null && !request.getCursor().isEmpty()) {
            top = searcher.searchAfter(afterOf(request.getCursor(), searcher), query, pageSize,
                    sort, true);
        } else {
            int from = Math.max(0, (request.getPage() - 1) * pageSize);
            if (from + pageSize > MAX_RESULT_WINDOW) {
                throw new WebCrawlerException("Page " + request.getPage() + " is past the "
                        + MAX_RESULT_WINDOW + " result ceiling. Page forward with the cursor from"
                        + " the previous response instead of jumping to a page number.");
            }
            TopFieldDocs all = searcher.search(query, from + pageSize, sort, true);
            top = new TopFieldDocs(all.totalHits,
                    java.util.Arrays.copyOfRange(all.scoreDocs,
                            Math.min(from, all.scoreDocs.length), all.scoreDocs.length),
                    all.fields);
        }

        String[] contentHighlights = highlight(searcher, query, top, LuceneFields.CONTENT);
        String[] titleHighlights = highlight(searcher, query, top, LuceneFields.TITLE);

        List<SearchResult> results = new ArrayList<>();
        List<Object> nextCursor = null;
        for (int i = 0; i < top.scoreDocs.length; i++) {
            ScoreDoc hit = top.scoreDocs[i];
            Document document = searcher.storedFields().document(hit.doc);
            nextCursor = List.of(hit.score, document.get(LuceneFields.ID));

            List<String> highlights = new ArrayList<>();
            addIfPresent(highlights, contentHighlights, i);
            addIfPresent(highlights, titleHighlights, i);

            results.add(SearchResult.builder().id(document.get(LuceneFields.ID))
                    .title(StringUtils.defaultString(document.get(LuceneFields.TITLE)))
                    .url(StringUtils.defaultString(document.get(LuceneFields.URL)))
                    .cat(document.get(LuceneFields.CAT))
                    .catalog(document.get(LuceneFields.CATALOG))
                    .version(intOf(document, LuceneFields.VERSION))
                    .createTime(dateOf(document))
                    .score(hit.score).highlights(highlights).build());
        }

        return SearchResponse.builder().results(results)
                .nextCursor(results.size() < pageSize ? null : nextCursor)
                .total(top.totalHits.value)
                .page(request.getPage()).pageSize(pageSize)
                .elapsedMillis(System.currentTimeMillis() - start).build();
    }

    /**
     * Where the next page starts.
     *
     * <p>
     * The document id is deliberately the highest legal one rather than the hit's own. Lucene only
     * consults it when a document ties with the cursor on every sort field, and the last sort field
     * is the document's unique id -- so the only document that can tie is the one the cursor names,
     * and the highest value is what skips exactly that one. Carrying the real Lucene document
     * number instead would have worked until the first merge renumbered it.
     */
    private FieldDoc afterOf(List<Object> cursor, IndexSearcher searcher) {
        if (cursor.size() < 2) {
            throw new WebCrawlerException("That cursor did not come from this search.");
        }
        float score = cursor.get(0) instanceof Number number ? number.floatValue() : 0f;
        BytesRef id = new BytesRef(String.valueOf(cursor.get(1)));
        int highest = Math.max(0, searcher.getIndexReader().maxDoc() - 1);
        return new FieldDoc(highest, score, new Object[] {score, id});
    }

    private void addIfPresent(List<String> highlights, String[] fragments, int index) {
        if (fragments != null && index < fragments.length
                && StringUtils.isNotBlank(fragments[index])) {
            highlights.add(fragments[index]);
        }
    }

    private String[] highlight(IndexSearcher searcher, Query query, TopFieldDocs top,
            String field) {
        if (top.scoreDocs.length == 0) {
            return new String[0];
        }
        try {
            UnifiedHighlighter highlighter = UnifiedHighlighter
                    .builder(searcher, indexes.getAnalyzer())
                    .withFormatter(new DefaultPassageFormatter("<em>", "</em>", " ... ", false))
                    .build();
            highlighter.setMaxLength(100_000);
            return highlighter.highlight(field, query, top, 2);
        } catch (Exception e) {
            // a missing highlight is a worse result, not a failed search
            return new String[0];
        }
    }

    private Integer intOf(Document document, String field) {
        String value = document.get(field);
        try {
            return value != null ? Integer.valueOf(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Date dateOf(Document document) {
        String value = document.get(LuceneFields.CREATE_TIME);
        try {
            return value != null ? new Date(Long.parseLong(value)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The words, the filters, and the preference for detail pages.
     */
    private Query queryOf(SearchRequest request, IndexSearcher searcher) {
        BooleanQuery.Builder matched = new BooleanQuery.Builder();
        if (StringUtils.isNotBlank(request.getKeyword())) {
            QueryBuilder builder = new QueryBuilder(indexes.getAnalyzer());
            BooleanQuery.Builder words = new BooleanQuery.Builder();
            // the title above the body, and what the pictures are described as below both
            addField(words, builder, LuceneFields.TITLE, request.getKeyword(), 2f);
            addField(words, builder, LuceneFields.CONTENT, request.getKeyword(), 1f);
            addField(words, builder, LuceneFields.IMAGE_TEXT, request.getKeyword(), 0.5f);
            matched.add(words.build(), Occur.MUST);
        } else {
            matched.add(new MatchAllDocsQuery(), Occur.MUST);
        }
        if (StringUtils.isNotBlank(request.getCat())) {
            matched.add(new TermQuery(new Term(LuceneFields.CAT, request.getCat())), Occur.FILTER);
        }
        if (request.getCatalogVersions() != null && !request.getCatalogVersions().isEmpty()) {
            BooleanQuery.Builder versions = new BooleanQuery.Builder();
            request.getCatalogVersions().forEach(catalogVersion -> versions.add(
                    new TermQuery(new Term(LuceneFields.CATALOG_VERSION, catalogVersion)),
                    Occur.SHOULD));
            matched.add(versions.build(), Occur.FILTER);
        }
        Query query = matched.build();
        return request.isPreferDetailPages()
                ? FunctionScoreQuery.boostByValue(query, new LinkDensityScore())
                : query;
    }

    private void addField(BooleanQuery.Builder words, QueryBuilder builder, String field,
            String keyword, float boost) {
        Query query = builder.createBooleanQuery(field, keyword, Occur.SHOULD);
        if (query != null) {
            words.add(boost == 1f ? query : new BoostQuery(query, boost), Occur.SHOULD);
        }
    }

    private SearchResponse empty(SearchRequest request, long start) {
        return SearchResponse.builder().results(List.of()).total(0L).page(request.getPage())
                .pageSize(request.getPageSize())
                .elapsedMillis(System.currentTimeMillis() - start).build();
    }

}
