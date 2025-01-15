package com.github.greenfinger;

import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.github.doodler.common.cloud.ApplicationInfoHolder;
import com.github.doodler.common.events.GlobalApplicationEventPublisher;
import com.github.doodler.common.events.GlobalApplicationEventPublisherAware;
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
public class WebCrawlerJob implements GlobalApplicationEventPublisherAware {

    @Autowired
    private CatalogDetailsService catalogDetailsService;

    @Autowired
    private WebCrawlerService webCrawlerService;

    @Autowired
    private ChannelSwitcher channelSwitcher;

    @Autowired
    private ApplicationInfoHolder applicationInfoHolder;

    private AtomicBoolean primaryRunning = new AtomicBoolean(false);
    private AtomicBoolean secondaryRunning = new AtomicBoolean(false);

    @Scheduled(cron = "0 */1 * * * ?")
    public void run() {}

    @RunAsPrimary
    public void runAsPrimary() throws Exception {
        if (primaryRunning.get()) {
            return;
        }
        try {
            CatalogDetails catalogDetails = catalogDetailsService.loadRunningCatalogDetails();
            if (catalogDetails == null) {
                return;
            }
            primaryRunning.set(true);
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
                        log.warn("Unknown running state: {}", runningState);
                        break;
                }
            }
        } catch (Exception e) {
            primaryRunning.set(false);
            if (log.isErrorEnabled()) {
                log.error(e.getMessage(), e);
            }
        }

    }

    @RunAsSecondary
    public void runAsSecondary() throws Exception {
        if (secondaryRunning.get()) {
            return;
        }
        CatalogDetails catalogDetails = catalogDetailsService.loadRunningCatalogDetails();
        if (catalogDetails == null) {
            return;
        }
        try {
            secondaryRunning.set(true);
            WebCrawlerExecutionContextUtils.get(catalogDetails.getId());
            channelSwitcher.toggle(true);

            globalApplicationEventPublisher
                    .publishEvent(new WebCrawlerNewJoinerEvent(applicationInfoHolder.get()));
        } catch (Exception e) {
            secondaryRunning.set(false);
            if (log.isErrorEnabled()) {
                log.error(e.getMessage(), e);
            }
        }
    }

    private GlobalApplicationEventPublisher globalApplicationEventPublisher;

    @Override
    public void setGlobalApplicationEventPublisher(
            GlobalApplicationEventPublisher globalApplicationEventPublisher) {
        this.globalApplicationEventPublisher = globalApplicationEventPublisher;
    }

}
