/**
* Copyright 2017-2021 Fred Feng (paganini.fy@gmail.com)

* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package indi.atlantis.framework.greenfinger.es;

import static indi.atlantis.framework.greenfinger.es.SearchResult.SEARCH_FIELD_CONTENT;
import static indi.atlantis.framework.greenfinger.es.SearchResult.SEARCH_FIELD_TITLE;
import static indi.atlantis.framework.greenfinger.es.SearchResult.SEARCH_FIELD_VERSION;

import java.util.ArrayList;
import java.util.List;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;

import com.github.paganini2008.devtools.beans.BeanUtils;
import com.github.paganini2008.devtools.jdbc.PageableResultSetSlice;

/**
 * 
 * ElasticsearchTemplateResultSlice
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
 */
public class ElasticsearchTemplateResultSlice extends PageableResultSetSlice<SearchResult> {

	private final String keyword;
	private final int version;
	private final ElasticsearchTemplate elasticsearchTemplate;

	public ElasticsearchTemplateResultSlice(String keyword, int version, ElasticsearchTemplate elasticsearchTemplate) {
		this.keyword = keyword;
		this.version = version;
		this.elasticsearchTemplate = elasticsearchTemplate;
	}

	@Override
	public int rowCount() {
		BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery().must(QueryBuilders.termQuery(SEARCH_FIELD_VERSION, version))
				.must(QueryBuilders.matchQuery(SEARCH_FIELD_TITLE, keyword)).must(QueryBuilders.matchQuery(SEARCH_FIELD_CONTENT, keyword));
		NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(boolQueryBuilder).build();
		return (int) elasticsearchTemplate.count(searchQuery, IndexedResource.class);
	}

	@Override
	public List<SearchResult> list(int maxResults, int firstResult) {
		BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery().must(QueryBuilders.termQuery(SEARCH_FIELD_VERSION, version))
				.must(QueryBuilders.matchQuery(SEARCH_FIELD_TITLE, keyword)).must(QueryBuilders.matchQuery(SEARCH_FIELD_CONTENT, keyword));
		NativeSearchQueryBuilder searchQueryBuilder = new NativeSearchQueryBuilder().withQuery(boolQueryBuilder)
				.withHighlightFields(new HighlightBuilder.Field(SEARCH_FIELD_TITLE), new HighlightBuilder.Field(SEARCH_FIELD_CONTENT))
				.withHighlightBuilder(new HighlightBuilder().preTags("<font class=\"search-keyword\" color=\"#FF0000\">")
						.postTags("</font>").fragmentSize(120).numOfFragments(3).noMatchSize(100));
		if (maxResults > 0) {
			searchQueryBuilder = searchQueryBuilder.withPageable(PageRequest.of(getPageNumber(), maxResults));
		}
		AggregatedPage<IndexedResource> page = elasticsearchTemplate.queryForPage(searchQueryBuilder.build(), IndexedResource.class,
				new HighlightResultMapper(elasticsearchTemplate.getElasticsearchConverter().getMappingContext()));
		List<IndexedResource> content = page.getContent();
		List<SearchResult> dataList = new ArrayList<SearchResult>();
		for (IndexedResource resource : content) {
			dataList.add(BeanUtils.copy(resource, SearchResult.class, null));
		}
		return dataList;
	}

}
