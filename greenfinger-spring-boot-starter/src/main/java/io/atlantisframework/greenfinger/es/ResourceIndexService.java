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
package io.atlantisframework.greenfinger.es;

import static io.atlantisframework.greenfinger.es.SearchResult.SEARCH_FIELD_CATALOG;
import static io.atlantisframework.greenfinger.es.SearchResult.SEARCH_FIELD_VERSION;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.util.StopWatch;

import com.github.paganini2008.devtools.CharsetUtils;
import com.github.paganini2008.devtools.beans.PropertyUtils;
import com.github.paganini2008.devtools.jdbc.PageRequest;
import com.github.paganini2008.devtools.jdbc.PageResponse;
import com.github.paganini2008.devtools.jdbc.ResultSetSlice;
import com.github.paganini2008.devtools.time.Duration;

import io.atlantisframework.greenfinger.PageExtractor;
import io.atlantisframework.greenfinger.ResourceManager;
import io.atlantisframework.greenfinger.api.CatalogInfo;
import io.atlantisframework.greenfinger.model.Catalog;
import io.atlantisframework.greenfinger.model.Resource;
import io.atlantisframework.vortex.common.Tuple;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * ResourceIndexService
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
 */
@Slf4j
public class ResourceIndexService {

	@Autowired
	private IndexedResourceRepository indexedResourceRepository;

	@Autowired
	private ResourceManager resourceManager;

	@Autowired
	private PageExtractor pageExtractor;

	@Autowired
	private ElasticsearchTemplate elasticsearchTemplate;

	public void createIndex(String indexName) {
		elasticsearchTemplate.createIndex(indexName);
	}

	public void deleteIndex(String indexName) {
		elasticsearchTemplate.deleteIndex(indexName);
	}

	public void deleteResource(long catalogId, int version) {
		Catalog catalog = resourceManager.getCatalog(catalogId);
		BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery()
				.must(QueryBuilders.matchQuery(SEARCH_FIELD_CATALOG, catalog.getName()));
		if (version > 0) {
			boolQueryBuilder = boolQueryBuilder.must(QueryBuilders.termQuery(SEARCH_FIELD_VERSION, version));
		}
		DeleteQuery deleteQuery = new DeleteQuery();
		deleteQuery.setQuery(boolQueryBuilder);
		elasticsearchTemplate.delete(deleteQuery, IndexedResource.class);
		log.info("Delete indexed resource by catalogId '{}' successfully.", catalogId);
	}

	public void saveResource(IndexedResource indexedResource) {
		indexedResourceRepository.save(indexedResource);
	}

	public void deleteResource(IndexedResource indexedResource) {
		indexedResourceRepository.delete(indexedResource);
	}

	public long indexCount() {
		return indexedResourceRepository.count();
	}

	public void upgradeCatalogIndex() {
		final StopWatch stopWatch = new StopWatch();
		int page = 1;
		PageResponse<CatalogInfo> pageResponse = resourceManager.selectForCatalog(null, page, 10);
		for (PageResponse<CatalogInfo> current : pageResponse) {
			for (CatalogInfo catalog : current.getContent()) {
				stopWatch.start(String.format("[%s<%s>]", catalog.getName(), catalog.getUrl()));
				upgradeCatalogIndex(catalog.getId());
				stopWatch.stop();
			}
		}
		log.info(stopWatch.prettyPrint());
	}

	public void indexCatalogIndex() {
		final StopWatch stopWatch = new StopWatch();
		int page = 1;
		PageResponse<CatalogInfo> pageResponse = resourceManager.selectForCatalog(null, page, 10);
		for (PageResponse<CatalogInfo> current : pageResponse) {
			for (CatalogInfo catalog : current.getContent()) {
				stopWatch.start(String.format("[%s<%s>]", catalog.getName(), catalog.getUrl()));
				indexCatalogIndex(catalog.getId());
				stopWatch.stop();
			}
		}
		log.info(stopWatch.prettyPrint());
	}

	public void upgradeCatalogIndex(long catalogId) {
		resourceManager.incrementCatalogIndexVersion(catalogId);
		indexCatalogIndex(catalogId);
	}

	public void indexCatalogIndex(long catalogId) {
		long startTime = System.currentTimeMillis();
		Catalog catalog = resourceManager.getCatalog(catalogId);
		log.info("Start to index catalog '{}' ...", catalog.getName());
		int version = resourceManager.getCatalogIndexVersion(catalogId);
		int page = 1;
		PageResponse<Resource> pageResponse = resourceManager.selectForResourceForIndex(catalogId, page, 100);
		for (PageResponse<Resource> current : pageResponse) {
			for (Resource resource : current.getContent()) {
				try {
					indexResource(catalog, resource, true, version);
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		}
		int effectedRows = resourceManager.updateResourceVersion(catalogId, version);
		log.info("Index catalog '{}' completedly. Effected rows: {}, Total time: {}", catalog.getName(), effectedRows,
				Duration.HOUR.format(System.currentTimeMillis() - startTime));
	}

	public void indexResource(Catalog catalog, Resource resource, boolean refresh, int version) {
		IndexedResource indexedResource = new IndexedResource();
		String html = resource.getHtml();
		if (refresh) {
			Tuple tuple = Tuple.wrap(PropertyUtils.convertToMap(catalog));
			try {
				html = pageExtractor.extractHtml(catalog.getUrl(), resource.getUrl(), CharsetUtils.toCharset(catalog.getPageEncoding()),
						tuple);
			} catch (Exception e) {
				log.error(e.getMessage(), e);
				html = pageExtractor.defaultPage(catalog.getUrl(), resource.getUrl(), CharsetUtils.toCharset(catalog.getPageEncoding()),
						tuple, e);
			}
		}
		Document document = Jsoup.parse(html);
		indexedResource.setId(resource.getId());
		indexedResource.setTitle(resource.getTitle());
		indexedResource.setContent(document.body().text());
		indexedResource.setPath(resource.getUrl());
		indexedResource.setCat(resource.getCat());
		indexedResource.setUrl(catalog.getUrl());
		indexedResource.setCatalog(catalog.getName());
		indexedResource.setCreateTime(resource.getCreateTime().getTime());
		indexedResource.setVersion(version);
		indexedResourceRepository.save(indexedResource);
		if (log.isTraceEnabled()) {
			log.trace("Index resource: " + resource.toString());
		}
	}

	public PageResponse<SearchResult> search(String cat, String keyword, Integer version, int page, int size) {
		return search(cat, keyword, version).list(PageRequest.of(page, size));
	}

	public ResultSetSlice<SearchResult> search(String cat, String keyword, Integer version) {
		if (version == null || version < 1) {
			version = resourceManager.maximumVersionOfCatalogIndex(cat);
		}
		return new ElasticsearchTemplateResultSlice(cat, keyword, version, elasticsearchTemplate);
	}

}
