package com.github.greenfinger;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import com.github.doodler.common.context.BeanLifeCycleUtils;
import com.github.doodler.common.context.ManagedBeanLifeCycle;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.utils.SerializableTaskTimer;
import com.github.greenfinger.components.Dashboard;
import com.github.greenfinger.components.ExistingUrlPathFilter;
import com.github.greenfinger.components.Extractor;
import com.github.greenfinger.components.InterruptionChecker;
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
public class DefaultWebCrawlerExecutionContext
        implements WebCrawlerExecutionContext, Runnable, ManagedBeanLifeCycle {

    private final CatalogDetails catalogDetails;

    @Autowired
    private WebCrawlerComponentFactory webCrawlerComponentFactory;

    @Autowired
    private SerializableTaskTimer taskTimer;

    private List<InterruptionChecker> interruptionCheckers;

    private List<UrlPathAcceptor> urlPathAcceptors;

    private Extractor extractor;

    private ExistingUrlPathFilter existingUrlPathFilter;

    private Dashboard dashboard;

    DefaultWebCrawlerExecutionContext(CatalogDetails catalogDetails) {
        this.catalogDetails = catalogDetails;
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
        log.info("Initializing WebCrawler ExecutionContext For Catalog: {}",
                catalogDetails.toString());

        interruptionCheckers = webCrawlerComponentFactory.getInterruptionCheckers(catalogDetails);
        AnnotationAwareOrderComparator.sort(interruptionCheckers);
        BeanLifeCycleUtils.afterPropertiesSet(interruptionCheckers);

        urlPathAcceptors = webCrawlerComponentFactory.getUrlPathAcceptors(catalogDetails);
        AnnotationAwareOrderComparator.sort(urlPathAcceptors);
        BeanLifeCycleUtils.afterPropertiesSet(urlPathAcceptors);

        existingUrlPathFilter = webCrawlerComponentFactory.getExistingUrlPathFilter(catalogDetails);
        BeanLifeCycleUtils.afterPropertiesSet(existingUrlPathFilter);

        extractor = webCrawlerComponentFactory.getExtractor(catalogDetails);
        BeanLifeCycleUtils.afterPropertiesSet(extractor);

        dashboard = webCrawlerComponentFactory.getDashboard(catalogDetails);
        BeanLifeCycleUtils.afterPropertiesSet(dashboard);

        taskTimer.addBatch(this);
    }



    @Override
    public void destroy() throws Exception {
        BeanLifeCycleUtils.destroy(interruptionCheckers);
        BeanLifeCycleUtils.destroy(urlPathAcceptors);
        BeanLifeCycleUtils.destroy(existingUrlPathFilter);
        BeanLifeCycleUtils.destroy(extractor);
        BeanLifeCycleUtils.destroy(dashboard);
    }

    @Override
    public boolean isUrlAcceptable(String referUrl, String path, Packet packet) {
        if (urlPathAcceptors == null) {
            return true;
        }
        for (UrlPathAcceptor urlPathAcceptor : urlPathAcceptors) {
            if (!urlPathAcceptor.accept(catalogDetails, referUrl, path, packet)) {
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
            taskTimer.removeBatch(this);

            log.info("{}", dashboard.toString());
        }
    }

}
