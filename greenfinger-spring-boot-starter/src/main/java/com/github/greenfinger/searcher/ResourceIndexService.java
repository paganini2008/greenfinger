package com.github.greenfinger.searcher;

import static com.github.greenfinger.searcher.SearchResult.SEARCH_FIELD_CATALOG;
import static com.github.greenfinger.searcher.SearchResult.SEARCH_FIELD_VERSION;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import com.github.doodler.common.page.EachPage;
import com.github.doodler.common.page.PageReader;
import com.github.doodler.common.page.PageRequest;
import com.github.doodler.common.page.PageResponse;
import com.github.greenfinger.CatalogDetails;
import com.github.greenfinger.CatalogDetailsService;
import com.github.greenfinger.ResourceManager;
import com.github.greenfinger.WebCrawlerException;
import com.github.greenfinger.api.pojo.CatalogInfo;
import com.github.greenfinger.model.Catalog;
import com.github.greenfinger.model.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: ResourceIndexService
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@Slf4j
@Service
public class ResourceIndexService {

    @Autowired
    private IndexedResourceRepository indexedResourceRepository;

    @Autowired
    private CatalogDetailsService catalogDetailsService;

    @Autowired
    private ResourceManager resourceManager;

    @Autowired
    private ElasticsearchRestTemplate elasticsearchTemplate;

    public void createIndex(String indexName) {
        elasticsearchTemplate.indexOps(IndexCoordinates.of(indexName)).create();
    }

    public void deleteIndex(String indexName) {
        elasticsearchTemplate.indexOps(IndexCoordinates.of(indexName)).delete();
    }

    public void deleteResource(long catalogId, int version) {
        Catalog catalog = resourceManager.getCatalog(catalogId);
        NativeSearchQueryBuilder searchQueryBuilder = new NativeSearchQueryBuilder();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery()
                .must(QueryBuilders.termQuery(SEARCH_FIELD_CATALOG, catalog.getName()));
        if (version >= 0) {
            boolQueryBuilder =
                    boolQueryBuilder.must(QueryBuilders.termQuery(SEARCH_FIELD_VERSION, version));
        }
        NativeSearchQuery searchQuery = searchQueryBuilder.withQuery(boolQueryBuilder).build();
        elasticsearchTemplate.delete(searchQuery, IndexedResource.class);
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

    public void upgradeCatalogIndex() throws WebCrawlerException {
        final StopWatch stopWatch = new StopWatch();
        int page = 1;
        PageResponse<CatalogInfo> pageResponse = resourceManager.pageForCatalog2(null, page, 10);
        for (EachPage<CatalogInfo> eachPage : pageResponse) {
            for (CatalogInfo catalog : eachPage.getContent()) {
                stopWatch.start(String.format("[%s<%s>]", catalog.getName(), catalog.getUrl()));
                upgradeCatalogIndex(catalog.getId());
                stopWatch.stop();
            }
        }
        log.info(stopWatch.prettyPrint());
    }

    public void indexCatalogIndex() throws WebCrawlerException {
        final StopWatch stopWatch = new StopWatch();
        int page = 1;
        PageResponse<CatalogInfo> pageResponse = resourceManager.pageForCatalog2(null, page, 10);
        for (EachPage<CatalogInfo> current : pageResponse) {
            for (CatalogInfo catalog : current.getContent()) {
                stopWatch.start(String.format("[%s<%s>]", catalog.getName(), catalog.getUrl()));
                indexCatalogIndex(catalog.getId());
                stopWatch.stop();
            }
        }
        log.info(stopWatch.prettyPrint());
    }

    public void upgradeCatalogIndex(long catalogId) throws WebCrawlerException {
        resourceManager.incrementCatalogIndexVersion(catalogId);
        indexCatalogIndex(catalogId);
    }

    public void indexCatalogIndex(long catalogId) throws WebCrawlerException {
        long startTime = System.currentTimeMillis();
        CatalogDetails catalogDetails = catalogDetailsService.loadCatalogDetails(catalogId);
        log.info("Start to index catalog '{}' ...", catalogDetails.getName());
        int page = 1;
        PageResponse<Resource> pageResponse =
                resourceManager.pageForResourceForIndex(catalogId, page, 100);
        for (EachPage<Resource> current : pageResponse) {
            for (Resource resource : current.getContent()) {
                try {
                    indexResource(catalogDetails, resource, catalogDetails.getVersion());
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        int effectedRows =
                resourceManager.updateResourceVersion(catalogId, catalogDetails.getVersion());
        log.info("Index catalog '{}' completedly. Effected rows: {}, Total time: {}",
                catalogDetails.getName(), effectedRows, System.currentTimeMillis() - startTime);
    }

    public void indexResource(CatalogDetails catalogDetails, Resource resource, int version) {
        IndexedResource indexedResource = new IndexedResource();
        String html = resource.getHtml();
        Document document = Jsoup.parse(html);
        indexedResource.setId(resource.getId());
        indexedResource.setTitle(resource.getTitle());
        indexedResource.setContent(document.body().text());
        indexedResource.setPath(resource.getUrl());
        indexedResource.setCat(resource.getCat());
        indexedResource.setUrl(catalogDetails.getUrl());
        indexedResource.setCatalog(catalogDetails.getName());
        indexedResource.setCreateTime(resource.getCreateTime().getTime());
        indexedResource.setVersion(version);
        indexedResourceRepository.save(indexedResource);
        if (log.isTraceEnabled()) {
            log.trace("Index resource: " + resource.toString());
        }
    }

    public PageResponse<SearchResult> search(String cat, String keyword, Integer version, int page,
            int size) {
        return search(cat, keyword, version).list(PageRequest.of(page, size));
    }

    public PageReader<SearchResult> search(String cat, String keyword, Integer version) {
        if (version == null || version < 1) {
            version = resourceManager.maximumVersionOfCatalogIndex(cat);
        }
        return new ElasticsearchTemplatePaginator(cat, keyword, version, elasticsearchTemplate);
    }

}