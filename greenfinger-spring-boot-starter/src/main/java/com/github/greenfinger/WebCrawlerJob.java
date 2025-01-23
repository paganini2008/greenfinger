package com.github.greenfinger;

import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.github.doodler.common.cloud.ApplicationInfoHolder;
import com.github.doodler.common.events.EventPublisher;
import com.github.doodler.common.scheduler.RunAsPrimary;
import com.github.doodler.common.scheduler.RunAsSecondary;
import com.github.doodler.common.transmitter.ChannelSwitcher;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.utils.DateUtils;
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
    private EventPublisher<Packet> eventPublisher;

    @Autowired
    private CatalogDetailsService catalogDetailsService;

    @Autowired
    private WebCrawlerService webCrawlerService;

    @Autowired
    private ChannelSwitcher channelSwitcher;

    @Autowired
    private ApplicationInfoHolder applicationInfoHolder;

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
        try {
            if (!semaphore.acquire()) {
                return;
            }
            final long catalogId = catalogDetails.getId();
            semaphore.setCatalogId(catalogId);
            WebCrawlerExecutionContextUtils.remove(catalogId);
            WebCrawlerExecutionContext executionContext =
                    WebCrawlerExecutionContextUtils.get(catalogId);
            executionContext.getDashboard().reset(
                    DateUtils.convertToMillis(catalogDetails.getFetchDuration(), TimeUnit.MINUTES));

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
                channelSwitcher.setEnabled(true);
            }
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error(e.getMessage(), e);
            }
            channelSwitcher.setEnabled(false);
            WebCrawlerExecutionContextUtils.remove(catalogDetails.getId());
            semaphore.release();
        }
        eventPublisher.enableBufferCleaner(true);

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
        try {
            if (!semaphore.acquire()) {
                return;
            }
            semaphore.setCatalogId(catalogDetails.getId());
            WebCrawlerExecutionContextUtils.remove(catalogDetails.getId());
            WebCrawlerExecutionContextUtils.get(catalogDetails.getId());
            channelSwitcher.setEnabled(true);
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error(e.getMessage(), e);
            }
            channelSwitcher.setEnabled(false);
            WebCrawlerExecutionContextUtils.remove(catalogDetails.getId());
            semaphore.release();
        }
        eventPublisher.enableBufferCleaner(true);
    }
}
