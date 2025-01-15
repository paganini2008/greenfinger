package com.github.greenfinger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 
 * @Description: WebCrawlerJobService
 * @Author: Fred Feng
 * @Date: 15/01/2025
 * @Version 1.0.0
 */
@Service
public class WebCrawlerJobService {

    @Autowired
    private ResourceManager resourceManager;

    public void rebuild(long catalogId) {
        resourceManager.setRunningState(catalogId, "rebuild");
    }

    public void crawl(long catalogId) {
        resourceManager.setRunningState(catalogId, "crawl");
    }

    public void update(long catalogId) {
        resourceManager.setRunningState(catalogId, "update");
    }

    public void finish(long catalogId) {
        WebCrawlerExecutionContextUtils.remove(catalogId);
        resourceManager.setRunningState(catalogId, "none");
    }

}
