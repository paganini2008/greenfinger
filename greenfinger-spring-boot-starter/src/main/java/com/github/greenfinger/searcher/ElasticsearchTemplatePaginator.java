/**
 * Copyright 2017-2025 Fred Feng (paganini.fy@gmail.com)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.github.greenfinger.searcher;

import static com.github.greenfinger.searcher.SearchResult.SEARCH_FIELD_CAT;
import static com.github.greenfinger.searcher.SearchResult.SEARCH_FIELD_CONTENT;
import static com.github.greenfinger.searcher.SearchResult.SEARCH_FIELD_TITLE;
import static com.github.greenfinger.searcher.SearchResult.SEARCH_FIELD_VERSION;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import com.github.doodler.common.page.DefaultPageContent;
import com.github.doodler.common.page.PageContent;
import com.github.doodler.common.page.PageReader;
import com.github.doodler.common.utils.BeanCopyUtils;
import com.github.doodler.common.utils.MapUtils;

/**
 * 
 * @Description: ElasticsearchTemplatePaginator
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public class ElasticsearchTemplatePaginator implements PageReader<SearchResult> {

    private final String cat;
    private final String keyword;
    private final int version;
    private final ElasticsearchRestTemplate elasticsearchRestTemplate;

    public ElasticsearchTemplatePaginator(String cat, String keyword, int version,
            ElasticsearchRestTemplate elasticsearchRestTemplate) {
        this.cat = cat;
        this.keyword = keyword;
        this.version = version;
        this.elasticsearchRestTemplate = elasticsearchRestTemplate;
    }

    @Override
    public long rowCount() {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        if (version >= 0) {
            boolQueryBuilder.must(QueryBuilders.termQuery(SEARCH_FIELD_VERSION, version));
        }
        if (StringUtils.isNotBlank(cat)) {
            boolQueryBuilder =
                    boolQueryBuilder.must(QueryBuilders.termQuery(SEARCH_FIELD_CAT, cat));
        }
        if (StringUtils.isNotBlank(keyword)) {
            boolQueryBuilder =
                    boolQueryBuilder.must(QueryBuilders.matchQuery(SEARCH_FIELD_TITLE, keyword))
                            .should(QueryBuilders.matchQuery(SEARCH_FIELD_CONTENT, keyword));
        }
        NativeSearchQuery searchQuery =
                new NativeSearchQueryBuilder().withQuery(boolQueryBuilder).build();
        return elasticsearchRestTemplate.count(searchQuery, IndexedResource.class);
    }

    @Override
    public PageContent<SearchResult> list(int pageNumber, int offset, int limit, Object nextToken) {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        if (version >= 0) {
            boolQueryBuilder.must(QueryBuilders.termQuery(SEARCH_FIELD_VERSION, version));
        }
        if (StringUtils.isNotBlank(cat)) {
            boolQueryBuilder =
                    boolQueryBuilder.must(QueryBuilders.termQuery(SEARCH_FIELD_CAT, cat));
        }
        if (StringUtils.isNotBlank(keyword)) {
            boolQueryBuilder =
                    boolQueryBuilder.must(QueryBuilders.matchQuery(SEARCH_FIELD_TITLE, keyword))
                            .should(QueryBuilders.matchQuery(SEARCH_FIELD_CONTENT, keyword));
        }
        NativeSearchQueryBuilder searchQueryBuilder = new NativeSearchQueryBuilder()
                .withQuery(boolQueryBuilder)
                .withHighlightFields(new HighlightBuilder.Field(SEARCH_FIELD_TITLE),
                        new HighlightBuilder.Field(SEARCH_FIELD_CONTENT))
                .withHighlightBuilder(new HighlightBuilder()
                        .preTags("<font color='red' class='searchKeyword'>").postTags("</font>")
                        .fragmentSize(120).numOfFragments(3).noMatchSize(120));
        if (limit > 0) {
            searchQueryBuilder =
                    searchQueryBuilder.withPageable(PageRequest.of(pageNumber - 1, limit));
        }
        SearchHits<IndexedResource> hits =
                elasticsearchRestTemplate.search(searchQueryBuilder.build(), IndexedResource.class);
        if (hits.isEmpty()) {
            return new DefaultPageContent<>();
        }
        List<SearchResult> dataList = new ArrayList<SearchResult>();
        for (SearchHit<IndexedResource> hit : hits.getSearchHits()) {
            dataList.add(convertValueObject(hit));
        }
        return new DefaultPageContent<>(dataList, null);
    }

    private SearchResult convertValueObject(SearchHit<IndexedResource> hit) {
        SearchResult searchResult = BeanCopyUtils.copyBean(hit.getContent(), SearchResult.class);
        Map<String, List<String>> map = hit.getHighlightFields();
        if (MapUtils.isEmpty(map)) {
            return searchResult;
        }
        String propertyName;
        List<String> fragments;
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            propertyName = entry.getKey();
            fragments = entry.getValue();
            try {
                PropertyUtils.setProperty(searchResult, propertyName,
                        String.join(" ", fragments.toArray(new String[0])));
            } catch (Exception ingored) {
            }
        }
        return searchResult;
    }

}
