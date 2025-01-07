package com.github.greenfinger.test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import com.github.doodler.common.transmitter.NioClient;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.transmitter.Partitioner;
import com.github.doodler.common.utils.DateUtils;
import com.github.greenfinger.CatalogAdminService;
import com.github.greenfinger.ResourceManager;
import com.github.greenfinger.WebCrawlerExecutionContext;
import com.github.greenfinger.model.Catalog;
import com.github.greenfinger.model.CatalogIndex;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: WebCrawlerService
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@Slf4j
public final class WebCrawlerService {

    @Autowired
    private NioClient nioClient;

    @Autowired
    private Partitioner partitioner;

    @Autowired
    private ResourceManager resourceManager;

    @Autowired
    private CatalogAdminService catalogAdminService;

    public void rebuild(long catalogId) {
        catalogAdminService.cleanCatalog(catalogId, false);
        resourceManager.incrementCatalogIndexVersion(catalogId);

        WebCrawlerExecutionContext executionContext = WebCrawlerExecutionContext.get(catalogId);
        executionContext.getExistingUrlPathFilter().clean();
        crawl(catalogId, true);
    }

    public void crawl(long catalogId, boolean indexEnabled) {
        Catalog catalog = resourceManager.getCatalog(catalogId);
        CatalogIndex catalogIndex = resourceManager.getCatalogIndex(catalogId);

        WebCrawlerExecutionContext executionContext = WebCrawlerExecutionContext.get(catalogId);
        executionContext.getDashboardData()
                .reset(DateUtils.convertToMillis(catalog.getDuration(), TimeUnit.MINUTES), true);

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("partitioner", "hash");
        data.put("action", "crawl");
        data.put("catalogId", catalog.getId());
        data.put("refer", catalog.getUrl());
        data.put("path", catalog.getUrl());
        data.put("cat", catalog.getCat());
        data.put("pageEncoding", catalog.getPageEncoding());
        data.put("maxFetchSize", catalog.getMaxFetchSize());
        data.put("duration", catalog.getDuration());
        data.put("depth", catalog.getDepth());
        data.put("interval", catalog.getInterval());
        data.put("version", catalogIndex != null ? catalogIndex.getVersion() : 0);
        data.put("indexEnabled", indexEnabled);
        log.info("Catalog Config: {}", data);

        nioClient.send(Packet.wrap(data), partitioner);
    }

    public void update(long catalogId, boolean indexEnabled) {
        Catalog catalog = resourceManager.getCatalog(catalogId);
        CatalogIndex catalogIndex = resourceManager.getCatalogIndex(catalogId);
        WebCrawlerExecutionContext executionContext = WebCrawlerExecutionContext.get(catalogId);
        executionContext.getDashboardData()
                .reset(DateUtils.convertToMillis(catalog.getDuration(), TimeUnit.MINUTES), false);

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("partitioner", "hash");
        data.put("action", "update");
        data.put("catalogId", catalog.getId());
        data.put("refer", catalog.getUrl());
        data.put("path", getLatestPath(catalog.getId()));
        data.put("cat", catalog.getCat());
        data.put("pageEncoding", catalog.getPageEncoding());
        data.put("maxFetchSize", catalog.getMaxFetchSize());
        data.put("duration", catalog.getDuration());
        data.put("depth", catalog.getDepth());
        data.put("interval", catalog.getInterval());
        data.put("version", catalogIndex != null ? catalogIndex.getVersion() : 0);
        data.put("indexEnabled", indexEnabled);
        log.info("Catalog Config: {}", data);

        nioClient.send(Packet.wrap(data), partitioner);
    }

    private String getLatestPath(long catalogId) {
        return resourceManager.getLatestPath(catalogId);
    }
}
