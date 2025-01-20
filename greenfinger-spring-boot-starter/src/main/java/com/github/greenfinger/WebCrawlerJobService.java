package com.github.greenfinger;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
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
    private ResourceManager resourceManager;

    public void rebuild(long catalogId) {
        resourceManager.setRunningState(catalogId, "rebuild");
    }

    public void crawl(long catalogId) {
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

    public void update(long catalogId) {
        resourceManager.setRunningState(catalogId, "update");
    }

    public void finish(long catalogId) {
        WebCrawlerExecutionContextUtils.remove(catalogId);
        resourceManager.setRunningState(catalogId, "none");
    }

}
