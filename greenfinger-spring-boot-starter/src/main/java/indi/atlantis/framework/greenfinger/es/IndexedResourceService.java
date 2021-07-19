/**
* Copyright 2018-2021 Fred Feng (paganini.fy@gmail.com)

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

import static indi.atlantis.framework.greenfinger.es.SearchResult.SEARCH_FIELD_CATALOG;
import static indi.atlantis.framework.greenfinger.es.SearchResult.SEARCH_FIELD_VERSION;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.util.StopWatch;

import com.github.paganini2008.devtools.CharsetUtils;
import com.github.paganini2008.devtools.date.Duration;
import com.github.paganini2008.devtools.jdbc.PageRequest;
import com.github.paganini2008.devtools.jdbc.PageResponse;
import com.github.paganini2008.devtools.jdbc.ResultSetSlice;

import indi.atlantis.framework.greenfinger.PageExtractor;
import indi.atlantis.framework.greenfinger.ResourceManager;
import indi.atlantis.framework.greenfinger.model.Catalog;
import indi.atlantis.framework.greenfinger.model.CatalogIndex;
import indi.atlantis.framework.greenfinger.model.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * IndexedResourceService
 *
 * @author Fred Feng
 * 
 * @since 1.0
 */
@Slf4j
public class IndexedResourceService {

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

	public void indexAll(boolean upgrade) {
		StopWatch stopWatch = new StopWatch();
		int page = 1;
		PageResponse<Catalog> pageResponse = resourceManager.queryForCatalog(page, 10);
		for (PageResponse<Catalog> current : pageResponse) {
			for (Catalog catalog : current.getContent()) {
				stopWatch.start(String.format("[%s<%s>]", catalog.getName(), catalog.getUrl()));
				indexAll(catalog.getId(), upgrade);
				stopWatch.stop();
			}
		}
		log.info(stopWatch.prettyPrint());
	}

	public void indexAll(long catalogId, boolean upgrade) {
		long startTime = System.currentTimeMillis();
		Catalog catalog = resourceManager.getCatalog(catalogId);
		log.info("Start to index catalog '{}' ...", catalog.getName());
		if (upgrade) {
			resourceManager.incrementCatalogIndexVersion(catalogId);
		}
		CatalogIndex catalogIndex = resourceManager.getCatalogIndex(catalogId);
		int page = 1;
		PageResponse<Resource> pageResponse = resourceManager.queryForResourceForIndex(catalogId, page, 100);
		for (PageResponse<Resource> current : pageResponse) {
			for (Resource resource : current.getContent()) {
				try {
					index(catalog, resource, true, catalogIndex.getVersion());
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		}
		int effectedRows = resourceManager.updateResourceVersion(catalogId, catalogIndex.getVersion());
		log.info("Index catalog '{}' completedly. Effected rows: {}, Total time: {}", catalog.getName(), effectedRows,
				Duration.HOUR.format(System.currentTimeMillis() - startTime));
	}

	public void index(Catalog catalog, Resource resource, boolean refresh, int version) {
		IndexedResource indexedResource = new IndexedResource();
		String html = resource.getHtml();
		if (refresh) {
			try {
				html = pageExtractor.extractHtml(catalog.getUrl(), resource.getUrl(), CharsetUtils.toCharset(catalog.getPageEncoding()));
			} catch (Exception e) {
				log.error(e.getMessage(), e);
				html = pageExtractor.defaultPage(catalog.getUrl(), resource.getUrl(), CharsetUtils.toCharset(catalog.getPageEncoding()), e);
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

	public PageResponse<SearchResult> search(String keyword, int version, int page, int size) {
		return search(keyword, version).list(PageRequest.of(page, size));
	}

	public ResultSetSlice<SearchResult> search(String keyword, int version) {
		if (version < 1) {
			version = resourceManager.maximumVersionOfCatalogIndex();
		}
		return new ElasticsearchTemplateResultSlice(keyword, version, elasticsearchTemplate);
	}

}
