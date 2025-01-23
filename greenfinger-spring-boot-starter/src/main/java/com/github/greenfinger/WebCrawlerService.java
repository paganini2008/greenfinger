package com.github.greenfinger;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Marker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.github.doodler.common.transmitter.NioClient;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.transmitter.Partitioner;
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
public class WebCrawlerService {

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

    @Autowired
    private Marker marker;

    public void rebuild(long catalogId) throws WebCrawlerException {
        catalogAdminService.cleanCatalog(catalogId, false);
        resourceManager.incrementCatalogIndexVersion(catalogId);
        crawl(catalogId);
    }

    @Async
    public void crawl(long catalogId) throws WebCrawlerException {
        CatalogDetails catalogDetails = catalogDetailsService.loadCatalogDetails(catalogId);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("partitioner", "hash");
        data.put("action", "crawl");
        data.put("catalogId", catalogDetails.getId());

        data.put("refer", catalogDetails.getUrl());
        String startUrl = catalogDetails.getStartUrl();
        if (StringUtils.isNotBlank(startUrl)) {
            data.put("path", startUrl);
        } else {
            data.put("path", catalogDetails.getUrl());
        }
        data.put("cat", catalogDetails.getCategory());
        data.put("pageEncoding", catalogDetails.getPageEncoding());
        data.put("maxFetchSize", catalogDetails.getMaxFetchSize());
        data.put("duration", catalogDetails.getFetchDuration());
        data.put("depth", catalogDetails.getMaxFetchDepth());
        data.put("interval", catalogDetails.getFetchInterval());
        data.put("version", catalogDetails.getVersion() != null ? catalogDetails.getVersion() : 0);
        data.put("indexEnabled", catalogDetails.getIndexed());
        log.info(marker, "Crawling catalog by config: {}", data);

        nioClient.send(Packet.wrap(data), partitioner);
    }

    @Async
    public void update(long catalogId, String referencePath) throws WebCrawlerException {
        CatalogDetails catalogDetails = catalogDetailsService.loadCatalogDetails(catalogId);
        if (StringUtils.isBlank(referencePath)) {
            referencePath = StringUtils.isNotBlank(catalogDetails.getStartUrl())
                    ? catalogDetails.getStartUrl()
                    : getLatestReferencePath(catalogDetails.getId());
        }

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("partitioner", "hash");
        data.put("action", "update");
        data.put("catalogId", catalogDetails.getId());
        data.put("refer", catalogDetails.getUrl());
        data.put("path", referencePath);
        data.put("cat", catalogDetails.getCategory());
        data.put("pageEncoding", catalogDetails.getPageEncoding());
        data.put("maxFetchSize", catalogDetails.getMaxFetchSize());
        data.put("duration", catalogDetails.getFetchDuration());
        data.put("depth", catalogDetails.getMaxFetchDepth());
        data.put("interval", catalogDetails.getFetchInterval());
        data.put("version", catalogDetails.getVersion() != null ? catalogDetails.getVersion() : 0);
        data.put("indexEnabled", catalogDetails.getIndexed());
        log.info(marker, "Updating catalog by config: {}", data);

        nioClient.send(Packet.wrap(data), partitioner);
    }

    private String getLatestReferencePath(long catalogId) {
        return resourceManager.getLatestReferencePath(catalogId);
    }

}
