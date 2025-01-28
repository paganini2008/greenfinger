package com.github.greenfinger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.github.doodler.common.cloud.PrimaryApplicationInfoReadyEvent;
import com.github.doodler.common.cloud.SecondaryApplicationInfoRefreshEvent;
import com.github.doodler.common.events.EventPublisher;
import com.github.doodler.common.transmitter.ChannelSwitcher;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.utils.SerializableTaskTimer;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: WebCrawlerEventManager
 * @Author: Fred Feng
 * @Date: 24/01/2025
 * @Version 1.0.0
 */
@Slf4j
@Component
public class WebCrawlerEventManager implements ApplicationEventPublisherAware {

    @Autowired
    private SerializableTaskTimer taskTimer;

    @Autowired
    private ResourceManager resourceManager;

    @Autowired
    private WebCrawlerSemaphore semaphore;

    @Autowired
    private EventPublisher<Packet> eventPublisher;

    @Autowired
    private ChannelSwitcher channelSwitcher;

    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @EventListener({WebCrawlerInterruptEvent.class})
    public void onInterrupt(WebCrawlerInterruptEvent event) {
        WebCrawlerExecutionContext context = (WebCrawlerExecutionContext) event.getSource();
        taskTimer.removeBatch((Runnable) context);

        CatalogDetails catalogDetails = event.getCatalogDetails();
        channelSwitcher.enableExternalChannels(false);
        resourceManager.setRunningState(catalogDetails.getId(), "none");
        WebCrawlerExecutionContextUtils.remove(catalogDetails.getId());
        semaphore.release();
        log.info("Catalog web crawler '{}' is completed.", catalogDetails.toString());
        applicationEventPublisher
                .publishEvent(new WebCrawlerCompletionEvent(event.getSource(), catalogDetails));
    }

    @EventListener({PrimaryApplicationInfoReadyEvent.class})
    public void onPrimaryApplicationInfoReadyEvent() {
        eventPublisher.enableBufferCleaner(true);
    }

    @EventListener({SecondaryApplicationInfoRefreshEvent.class})
    public void onSecondaryApplicationInfoRefreshEvent() {
        eventPublisher.enableBufferCleaner(false);
    }

}
