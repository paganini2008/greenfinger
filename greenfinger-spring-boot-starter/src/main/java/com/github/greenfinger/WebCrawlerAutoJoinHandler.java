package com.github.greenfinger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.github.doodler.common.cloud.ApplicationInfoHolder;
import com.github.doodler.common.events.EventPublisher;
import com.github.doodler.common.events.GlobalApplicationEventPublisher;
import com.github.doodler.common.events.GlobalApplicationEventPublisherAware;
import com.github.doodler.common.transmitter.ChannelSwitcher;
import com.github.doodler.common.transmitter.Packet;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: WebCrawlerAutoJoinHandler
 * @Author: Fred Feng
 * @Date: 20/01/2025
 * @Version 1.0.0
 */
@Slf4j
@Component
public class WebCrawlerAutoJoinHandler implements GlobalApplicationEventPublisherAware {

    @Autowired
    private EventPublisher<Packet> eventPublisher;

    @Autowired
    private ChannelSwitcher channelSwitcher;

    @Autowired
    private WebCrawlerSemaphore semaphore;

    @Autowired
    private CatalogDetailsService catalogDetailsService;

    @Autowired
    private ApplicationInfoHolder applicationInfoHolder;

    @EventListener({ApplicationReadyEvent.class})
    public void autoJoin() throws Exception {
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
            channelSwitcher.toggle(true);
            globalApplicationEventPublisher
                    .publishEvent(new WebCrawlerNewJoinerEvent(applicationInfoHolder.get()));

            eventPublisher.enableBufferCleaner(true);
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error(e.getMessage(), e);
            }
            channelSwitcher.toggle(false);
            WebCrawlerExecutionContextUtils.remove(catalogDetails.getId());
            semaphore.release();
        }
    }

    private GlobalApplicationEventPublisher globalApplicationEventPublisher;

    @Override
    public void setGlobalApplicationEventPublisher(
            GlobalApplicationEventPublisher globalApplicationEventPublisher) {
        this.globalApplicationEventPublisher = globalApplicationEventPublisher;
    }

}
