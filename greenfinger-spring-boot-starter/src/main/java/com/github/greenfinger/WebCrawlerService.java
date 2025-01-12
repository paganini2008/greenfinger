package com.github.greenfinger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.github.doodler.common.transmitter.NioClient;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.transmitter.Partitioner;
import com.github.doodler.common.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: WebCrawlerService
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@Slf4j
@Service
public final class WebCrawlerService {

    @Autowired
    private NioClient nioClient;

    @Autowired
    private Partitioner partitioner;

    @Autowired
    private ResourceManager resourceManager;

    @Autowired
    private CatalogAdminService catalogAdminService;

    @Autowired
    private CatalogDetailsService catalogDetailsService;

    public void rebuild(long catalogId) throws WebCrawlerException {
        catalogAdminService.cleanCatalog(catalogId, false);
        resourceManager.incrementCatalogIndexVersion(catalogId);
        WebCrawlerExecutionContext executionContext =
                WebCrawlerExecutionContextUtils.get(catalogId);
        executionContext.getExistingUrlPathFilter().clean();
        crawl(catalogId, true);
    }

    public void crawl(long catalogId, boolean indexEnabled) throws WebCrawlerException {
        CatalogDetails catalog = catalogDetailsService.loadCatalogDetails(catalogId);

        WebCrawlerExecutionContext executionContext =
                WebCrawlerExecutionContextUtils.get(catalogId);
        executionContext.getDashboard().reset(
                DateUtils.convertToMillis(catalog.getFetchDuration(), TimeUnit.MINUTES), true);

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("partitioner", "hash");
        data.put("action", "crawl");
        data.put("catalogId", catalog.getId());
        data.put("refer", catalog.getUrl());
        data.put("path", catalog.getUrl());
        data.put("cat", catalog.getCategory());
        data.put("pageEncoding", catalog.getPageEncoding());
        data.put("maxFetchSize", catalog.getMaxFetchSize());
        data.put("duration", catalog.getFetchDuration());
        data.put("depth", catalog.getMaxFetchDepth());
        data.put("interval", catalog.getFetchInterval());
        data.put("version", catalog.getVersion() != null ? catalog.getVersion() : 0);
        data.put("indexEnabled", indexEnabled);
        log.info("Initializing Catalog Config: {}", data);

        nioClient.send(Packet.wrap(data), partitioner);
    }

    public void update(long catalogId, boolean indexEnabled) throws WebCrawlerException {
        CatalogDetails catalog = catalogDetailsService.loadCatalogDetails(catalogId);

        WebCrawlerExecutionContext executionContext =
                WebCrawlerExecutionContextUtils.get(catalogId);
        executionContext.getDashboard().reset(
                DateUtils.convertToMillis(catalog.getFetchDuration(), TimeUnit.MINUTES), false);

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("partitioner", "hash");
        data.put("action", "update");
        data.put("catalogId", catalog.getId());
        data.put("refer", catalog.getUrl());
        data.put("path", getLatestReferencePath(catalog.getId()));
        data.put("cat", catalog.getCategory());
        data.put("pageEncoding", catalog.getPageEncoding());
        data.put("maxFetchSize", catalog.getMaxFetchSize());
        data.put("duration", catalog.getFetchDuration());
        data.put("depth", catalog.getMaxFetchDepth());
        data.put("interval", catalog.getFetchInterval());
        data.put("version", catalog.getVersion() != null ? catalog.getVersion() : 0);
        data.put("indexEnabled", indexEnabled);
        log.info("Initializing Catalog Config: {}", data);

        nioClient.send(Packet.wrap(data), partitioner);
    }

    private String getLatestReferencePath(long catalogId) {
        return resourceManager.getLatestReferencePath(catalogId);
    }
}
