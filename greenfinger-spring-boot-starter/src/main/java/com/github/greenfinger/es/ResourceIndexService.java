package com.github.greenfinger.es;

import static com.github.greenfinger.es.SearchResult.SEARCH_FIELD_CATALOG;
import static com.github.greenfinger.es.SearchResult.SEARCH_FIELD_VERSION;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.util.StopWatch;
import com.github.doodler.common.page.EachPage;
import com.github.doodler.common.page.PageReader;
import com.github.doodler.common.page.PageRequest;
import com.github.doodler.common.page.PageResponse;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.utils.CharsetUtils;
import com.github.doodler.common.utils.MapUtils;
import com.github.greenfinger.ResourceManager;
import com.github.greenfinger.api.CatalogInfo;
import com.github.greenfinger.components.Extractor;
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
public class ResourceIndexService {

    @Autowired
    private IndexedResourceRepository indexedResourceRepository;

    @Autowired
    private ResourceManager resourceManager;

    @Autowired
    private Extractor pageExtractor;

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
        if (version > 0) {
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

    public void upgradeCatalogIndex() {
        final StopWatch stopWatch = new StopWatch();
        int page = 1;
        PageResponse<CatalogInfo> pageResponse = resourceManager.pageForCatalog(null, page, 10);
        for (EachPage<CatalogInfo> eachPage : pageResponse) {
            for (CatalogInfo catalog : eachPage.getContent()) {
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
        PageResponse<CatalogInfo> pageResponse = resourceManager.pageForCatalog(null, page, 10);
        for (EachPage<CatalogInfo> current : pageResponse) {
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
        PageResponse<Resource> pageResponse =
                resourceManager.pageForResourceForIndex(catalogId, page, 100);
        for (EachPage<Resource> current : pageResponse) {
            for (Resource resource : current.getContent()) {
                try {
                    indexResource(catalog, resource, true, version);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
        int effectedRows = resourceManager.updateResourceVersion(catalogId, version);
        log.info("Index catalog '{}' completedly. Effected rows: {}, Total time: {}",
                catalog.getName(), effectedRows, System.currentTimeMillis() - startTime);
    }

    public void indexResource(Catalog catalog, Resource resource, boolean refresh, int version) {
        IndexedResource indexedResource = new IndexedResource();
        String html = resource.getHtml();
        if (refresh) {
            Packet packet = Packet.wrap(MapUtils.obj2Map(catalog));
            try {
                html = pageExtractor.extractHtml(catalog.getUrl(), resource.getUrl(),
                        CharsetUtils.toCharset(catalog.getPageEncoding()), packet);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                html = pageExtractor.defaultPage(catalog.getUrl(), resource.getUrl(),
                        CharsetUtils.toCharset(catalog.getPageEncoding()), packet, e);
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

    public PageResponse<SearchResult> search(String cat, String keyword, Integer version, int page,
            int size) {
        return search(cat, keyword, version).list(PageRequest.of(page, size));
    }

    public PageReader<SearchResult> search(String cat, String keyword, Integer version) {
        if (version == null || version < 1) {
            version = resourceManager.maximumVersionOfCatalogIndex(cat);
        }
        return new ElasticsearchTemplateResultSlice(cat, keyword, version, elasticsearchTemplate);
    }

}
