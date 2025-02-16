/*
 * Copyright 2017-2025 Fred Feng (paganini.fy@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.greenfinger;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.github.doodler.common.context.InstanceId;
import com.github.doodler.common.scheduler.RunAsPrimary;
import com.github.doodler.common.scheduler.RunAsSecondary;
import com.github.doodler.common.transmitter.ChannelSwitcher;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: WebCrawlerJob
 * @Author: Fred Feng
 * @Date: 15/01/2025
 * @Version 1.0.0
 */
@Slf4j
@Component
public class WebCrawlerJob {

    @Autowired
    private InstanceId instanceId;

    @Autowired
    private CatalogDetailsService catalogDetailsService;

    @Autowired
    private WebCrawlerService webCrawlerService;

    @Autowired
    private ChannelSwitcher channelSwitcher;

    @Autowired
    private WebCrawlerSemaphore semaphore;

    @Scheduled(cron = "0 */1 * * * ?")
    public void run() {
        log.info("WebCrawlerJob start running.");
    }

    @RunAsPrimary
    public void runAsPrimary() throws Exception {
        if (semaphore.isOccupied()) {
            return;
        }
        CatalogDetails catalogDetails = catalogDetailsService.loadRunningCatalogDetails();
        if (catalogDetails == null) {
            return;
        }
        if (!semaphore.acquire()) {
            return;
        }
        log.info("Starting Catalog WebCrawler with configuration: {}", catalogDetails);
        try {
            channelSwitcher.enableExternalChannels(false);
            semaphore.setCatalogId(catalogDetails.getId());
            WebCrawlerExecutionContextUtils.remove(catalogDetails.getId());
            WebCrawlerExecutionContext context =
                    WebCrawlerExecutionContextUtils.get(catalogDetails.getId(), true);
            String runningState = catalogDetails.getRunningState();
            if (StringUtils.isNotBlank(runningState)) {
                switch (runningState) {
                    case "rebuild":
                        webCrawlerService.rebuild(catalogDetails.getId());
                        break;
                    case "crawl":
                        webCrawlerService.crawl(catalogDetails.getId());
                        break;
                    case "update":
                        webCrawlerService.update(catalogDetails.getId(), null);
                        break;
                    default:
                        throw new UnsupportedOperationException(
                                "Unknown catalog running state: " + runningState);
                }
                context.getGlobalStateManager().addMember(instanceId.get());
                log.info("Current Catalog WebCrawler has been initialized.");
            } else {
                throw new WebCrawlerException("Null running state!");
            }
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error(e.getMessage(), e);
            }
            WebCrawlerExecutionContextUtils.remove(catalogDetails.getId());
            semaphore.release();
        }
    }

    @RunAsSecondary
    public void runAsSecondary() throws Exception {
        if (semaphore.isOccupied()) {
            return;
        }
        CatalogDetails catalogDetails = catalogDetailsService.loadRunningCatalogDetails();
        if (catalogDetails == null) {
            return;
        }
        if (!semaphore.acquire()) {
            return;
        }
        log.info("Starting Catalog WebCrawler with configuration: {}", catalogDetails);
        try {
            channelSwitcher.enableExternalChannels(false);
            semaphore.setCatalogId(catalogDetails.getId());
            WebCrawlerExecutionContextUtils.remove(catalogDetails.getId());
            WebCrawlerExecutionContext context =
                    WebCrawlerExecutionContextUtils.get(catalogDetails.getId(), true);
            context.getGlobalStateManager().addMember(instanceId.get());
            log.info("Current Catalog WebCrawler has been initialized.");
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error(e.getMessage(), e);
            }
            WebCrawlerExecutionContextUtils.remove(catalogDetails.getId());
            semaphore.release();
        }
    }
}
