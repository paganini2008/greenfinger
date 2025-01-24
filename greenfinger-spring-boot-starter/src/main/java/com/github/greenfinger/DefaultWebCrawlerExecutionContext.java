package com.github.greenfinger;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.annotation.Scope;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;
import com.github.doodler.common.context.BeanLifeCycleUtils;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.utils.SerializableTaskTimer;
import com.github.greenfinger.components.Dashboard;
import com.github.greenfinger.components.ExistingUrlPathFilter;
import com.github.greenfinger.components.Extractor;
import com.github.greenfinger.components.InterruptionChecker;
import com.github.greenfinger.components.StatefulExtractor;
import com.github.greenfinger.components.UrlPathAcceptor;
import com.github.greenfinger.components.WebCrawlerComponentFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: DefaultWebCrawlerExecutionContext
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@Slf4j
@Scope("prototype")
@Component
public class DefaultWebCrawlerExecutionContext
        implements WebCrawlerExecutionContext, Runnable, ApplicationEventPublisherAware {

    @Autowired
    private CatalogDetailsService catalogDetailsService;

    @Autowired
    private WebCrawlerComponentFactory webCrawlerComponentFactory;

    @Autowired
    private SerializableTaskTimer taskTimer;

    private CatalogDetails catalogDetails;

    private List<InterruptionChecker> interruptionCheckers;

    private List<UrlPathAcceptor> urlPathAcceptors;

    private Extractor extractor;

    private ExistingUrlPathFilter existingUrlPathFilter;

    private Dashboard dashboard;

    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public CatalogDetails getCatalogDetails() {
        return catalogDetails;
    }

    @Override
    public List<InterruptionChecker> getInterruptionCheckers() {
        return interruptionCheckers;
    }

    @Override
    public List<UrlPathAcceptor> getUrlPathAcceptors() {
        return urlPathAcceptors;
    }

    @Override
    public Extractor getExtractor() {
        return extractor;
    }

    @Override
    public ExistingUrlPathFilter getExistingUrlPathFilter() {
        return existingUrlPathFilter;
    }

    @Override
    public Dashboard getDashboard() {
        return dashboard;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        CatalogDetails catalogDetails = catalogDetailsService.loadRunningCatalogDetails();
        this.catalogDetails = catalogDetails;
        log.info("Initializing WebCrawler ExecutionContext to {} catalog '{}'",
                catalogDetails.getRunningState(), catalogDetails.toString());

        interruptionCheckers = webCrawlerComponentFactory.getInterruptionCheckers(catalogDetails);
        AnnotationAwareOrderComparator.sort(interruptionCheckers);
        BeanLifeCycleUtils.afterPropertiesSet(interruptionCheckers);
        log.info("Initialized InterruptionChecker Component");

        urlPathAcceptors = webCrawlerComponentFactory.getUrlPathAcceptors(catalogDetails);
        AnnotationAwareOrderComparator.sort(urlPathAcceptors);
        BeanLifeCycleUtils.afterPropertiesSet(urlPathAcceptors);
        log.info("Initialized UrlPathAcceptor Component");

        existingUrlPathFilter = webCrawlerComponentFactory.getExistingUrlPathFilter(catalogDetails);
        BeanLifeCycleUtils.afterPropertiesSet(existingUrlPathFilter);
        log.info("Initialized ExistingUrlPathFilter Component {}",
                existingUrlPathFilter.getDescription());

        extractor = webCrawlerComponentFactory.getExtractor(catalogDetails);
        BeanLifeCycleUtils.afterPropertiesSet(extractor);
        log.info("Initialized Extractor Component {}", extractor.getDescription());

        dashboard = webCrawlerComponentFactory.getDashboard(catalogDetails);
        BeanLifeCycleUtils.afterPropertiesSet(dashboard);
        log.info("Initialized Dashboard Component {}", dashboard.getDescription());

        if (extractor instanceof StatefulExtractor) {
            ((StatefulExtractor<?>) extractor).login(catalogDetails);
            log.info("User Login: {}", Arrays.toString(catalogDetails.getCatalogCredentials()));
        }

        taskTimer.addBatch(this);
        log.info("Initialized WebCrawler ExecutionContext to {} catalog '{}' successfully.",
                catalogDetails.getRunningState(), catalogDetails.toString());
    }

    @Override
    public void destroy() throws Exception {
        log.info("Destroying WebCrawler ExecutionContext ...");

        if (extractor instanceof StatefulExtractor) {
            ((StatefulExtractor<?>) extractor).logout(catalogDetails);
            log.info("User logout.");
        }

        BeanLifeCycleUtils.destroyQuietly(interruptionCheckers);
        BeanLifeCycleUtils.destroyQuietly(urlPathAcceptors);
        BeanLifeCycleUtils.destroyQuietly(existingUrlPathFilter);
        BeanLifeCycleUtils.destroyQuietly(extractor);
        BeanLifeCycleUtils.destroyQuietly(dashboard);

        log.info("Destroyed WebCrawler ExecutionContext successfully.");
    }

    @Override
    public boolean isUrlAcceptable(String referUrl, String path, Packet packet) {
        if (urlPathAcceptors == null) {
            return true;
        }
        for (UrlPathAcceptor urlPathAcceptor : urlPathAcceptors) {
            if (!urlPathAcceptor.accept(catalogDetails, referUrl, path, packet)) {
                if (log.isTraceEnabled()) {
                    log.trace("Filter url by: {}", urlPathAcceptor.getDescription());
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isCompleted() {
        return dashboard != null && dashboard.isCompleted();
    }

    @Override
    public boolean shouldInterrupt() {
        for (InterruptionChecker interruptionChecker : interruptionCheckers) {
            if (interruptionChecker.shouldInterrupt(catalogDetails, dashboard)) {
                if (dashboard != null) {
                    dashboard.setCompleted(true);
                }
                break;
            }
        }
        return isCompleted();
    }

    @Override
    public void run() {
        if (shouldInterrupt()) {
            applicationEventPublisher
                    .publishEvent(new WebCrawlerInterruptEvent(this, catalogDetails));
            log.info("WebCrawlerJob is interrupted. Current Info: {}", dashboard.toString());
        }
    }

}
