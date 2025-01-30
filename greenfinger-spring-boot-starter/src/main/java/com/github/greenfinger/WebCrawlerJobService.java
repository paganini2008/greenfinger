package com.github.greenfinger;

import javax.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: WebCrawlerJobService
 * @Author: Fred Feng
 * @Date: 15/01/2025
 * @Version 1.0.0
 */
@Slf4j
@Service
public class WebCrawlerJobService {

    @Autowired
    private RedisOperations<String, Object> redisOperations;

    @Autowired
    private CatalogDetailsService catalogDetailsService;

    @Autowired
    private ResourceManager resourceManager;

    private CatalogDelayQueue catalogDelayQueue;

    @PostConstruct
    public void configure() {
        catalogDelayQueue = new CatalogDelayQueue(redisOperations, action -> {
            try {
                if ("rebuild".equals(action.getAction())) {
                    rebuild(action.getCatalogId());
                } else {
                    crawl(action.getCatalogId());
                }
            } catch (Exception e) {
                if (log.isErrorEnabled()) {
                    log.error(e.getMessage(), e);
                }
            }
            return null;
        });
    }

    @WebCrawling
    public void rebuild(long catalogId) throws WebCrawlerException {
        CatalogDetails catalogDetails = catalogDetailsService.loadRunningCatalogDetails();
        if (catalogDetails != null) {
            catalogDelayQueue.rebuild(catalogId);
        } else {
            resourceManager.setRunningState(catalogId, "rebuild");
        }
    }

    @WebCrawling
    public void crawl(long catalogId) throws WebCrawlerException {
        CatalogDetails catalogDetails = catalogDetailsService.loadRunningCatalogDetails();
        if (catalogDetails != null) {
            catalogDelayQueue.crawl(catalogId);
        } else {
            String path;
            try {
                path = resourceManager.getLatestReferencePath(catalogId);
            } catch (DataAccessException e) {
                if (log.isErrorEnabled()) {
                    log.error(e.getMessage(), e);
                }
                path = "";
            } catch (Exception e) {
                throw e;
            }
            if (StringUtils.isNotBlank(path)) {
                resourceManager.setRunningState(catalogId, "update");
            } else {
                resourceManager.setRunningState(catalogId, "crawl");
            }
        }
    }

    @EventListener({WebCrawlerCompletionEvent.class})
    public void onWebCrawlerCompletion() {
        catalogDelayQueue.runNext();
    }

}
