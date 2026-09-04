/*
 * Copyright 2017-2025 Fred Feng (paganini.fy@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.github.greenfinger;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.annotation.Scope;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;
import com.github.doodler.common.context.BeanLifeCycleUtils;
import com.github.doodler.common.events.EventPublisher;
import com.github.doodler.common.transmitter.NioContext;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.utils.DateUtils;
import com.github.doodler.common.utils.SerializableTaskTimer;
import com.github.doodler.common.utils.ThreadUtils;
import com.github.greenfinger.components.ExistingUrlPathFilter;
import com.github.greenfinger.components.Extractor;
import com.github.greenfinger.components.GlobalStateManager;
import com.github.greenfinger.components.InterruptionChecker;
import com.github.greenfinger.components.ProgressBarSupplier;
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
    private WebCrawlerProperties webCrawlerProperties;

    @Autowired
    private EventPublisher<Packet> eventPublisher;

    @Autowired
    private CatalogDetailsService catalogDetailsService;

    @Autowired
    private WebCrawlerComponentFactory webCrawlerComponentFactory;

    @Autowired
    private SerializableTaskTimer taskTimer;

    @Autowired
    private NioContext nioContext;

    private CatalogDetails catalogDetails;

    private List<InterruptionChecker> interruptionCheckers;

    private List<UrlPathAcceptor> urlPathAcceptors;

    private Extractor extractor;

    private ExistingUrlPathFilter existingUrlPathFilter;

    private GlobalStateManager globalStateManager;

    private ProgressBarSupplier progressBarSupplier;

    private ApplicationEventPublisher applicationEventPublisher;

    private CompletableFuture<?> completableFuture;

    DefaultWebCrawlerExecutionContext() {}

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
    public GlobalStateManager getGlobalStateManager() {
        return globalStateManager;
    }

    @Override
    public AtomicInteger getConcurrents() {
        return nioContext.getConcurrents();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        CatalogDetails catalogDetails = catalogDetailsService.loadRunningCatalogDetails();
        if (catalogDetails == null) {
            throw new CatalogDetailsNotFoundException("");
        }

        this.catalogDetails = catalogDetails;
        log.info("Initializing WebCrawler ExecutionContext to {} catalog '{}'",
                catalogDetails.getRunningState(), catalogDetails.toString());

        interruptionCheckers = webCrawlerComponentFactory.getInterruptionCheckers(catalogDetails);
        AnnotationAwareOrderComparator.sort(interruptionCheckers);
        BeanLifeCycleUtils.afterPropertiesSet(interruptionCheckers);
        log.info("Initialized InterruptionChecker Component: {}", interruptionCheckers);

        urlPathAcceptors = webCrawlerComponentFactory.getUrlPathAcceptors(catalogDetails);
        AnnotationAwareOrderComparator.sort(urlPathAcceptors);
        BeanLifeCycleUtils.afterPropertiesSet(urlPathAcceptors);
        log.info("Initialized UrlPathAcceptor Component: {}", urlPathAcceptors);

        existingUrlPathFilter = webCrawlerComponentFactory.getExistingUrlPathFilter(catalogDetails);
        BeanLifeCycleUtils.afterPropertiesSet(existingUrlPathFilter);
        log.info("Initialized ExistingUrlPathFilter Component: {}",
                existingUrlPathFilter.getName());

        extractor = webCrawlerComponentFactory.getExtractor(catalogDetails);
        BeanLifeCycleUtils.afterPropertiesSet(extractor);
        log.info("Initialized Extractor Component: {}", extractor.getName());

        globalStateManager = webCrawlerComponentFactory.getGlobalStateManager(catalogDetails);
        BeanLifeCycleUtils.afterPropertiesSet(globalStateManager);
        log.info("Initialized Dashboard Component: {}", globalStateManager.getName());

        progressBarSupplier = webCrawlerComponentFactory.getProgressBarSupplier(catalogDetails);
        BeanLifeCycleUtils.afterPropertiesSet(progressBarSupplier);
        log.info("Initialized ProgressBar Component: {}", progressBarSupplier.getName());

        if (extractor instanceof StatefulExtractor) {
            ((StatefulExtractor<?>) extractor).login(catalogDetails);
            log.info("Simulate User Login with authentication {}",
                    Arrays.toString(catalogDetails.getCatalogCredentials()));
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
            log.info("Simulate User Logout.");
        }

        BeanLifeCycleUtils.destroyQuietly(interruptionCheckers);
        log.info("Destroyed interruptionCheckers");
        BeanLifeCycleUtils.destroyQuietly(urlPathAcceptors);
        log.info("Destroyed urlPathAcceptors");
        BeanLifeCycleUtils.destroyQuietly(existingUrlPathFilter);
        log.info("Destroyed existingUrlPathFilter");
        BeanLifeCycleUtils.destroyQuietly(extractor);
        log.info("Destroyed extractor");
        BeanLifeCycleUtils.destroyQuietly(globalStateManager);
        log.info("Destroyed dashboard");
        BeanLifeCycleUtils.destroyQuietly(progressBarSupplier);
        log.info("Destroyed ProgressBar Component: {}", progressBarSupplier.getName());

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
                    log.trace("Filter url '{}' by: {}", path, urlPathAcceptor.getName());
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isCompleted() {
        return globalStateManager.isCompleted();
    }

    @Override
    public boolean shouldInterrupt() {
        if (!isCompleted()) {
            for (InterruptionChecker interruptionChecker : interruptionCheckers) {
                if (interruptionChecker.shouldInterrupt(catalogDetails,
                        globalStateManager.getDashboard())) {
                    globalStateManager.setCompleted(true);
                    break;
                }
            }
        }
        return isCompleted();
    }

    @Override
    public void waitForTermination(long timeout, TimeUnit timeUnit) throws Exception {
        if (completableFuture == null || completableFuture.isDone()
                || completableFuture.isCompletedExceptionally()
                || completableFuture.isCancelled()) {
            return;
        }
        boolean fired = true;
        try {
            completableFuture.get(timeout, timeUnit);
        } catch (TimeoutException e) {
            throw new TimeoutException("Unable to wait for termination because time is up to "
                    + DateUtils.converToSecond(timeout, timeUnit) + " seconds.");
        } catch (Exception e) {
            fired = false;
            if (log.isErrorEnabled()) {
                log.error(e.getMessage(), e);
            }
        } finally {
            if (fired) {
                applicationEventPublisher
                        .publishEvent(new WebCrawlerInterruptEvent(this, catalogDetails));
            }
        }
    }

    @Override
    public void run() {
        if (!shouldInterrupt()) {
            return;
        }
        if (log.isInfoEnabled()) {
            log.info("Catalog web crawler '{}' is interrupted.", catalogDetails.toString());
        }
        if (completableFuture == null) {
            prepareInterruption();
        }
    }

    private void prepareInterruption() {
        completableFuture = CompletableFuture.supplyAsync(() -> {
            while (eventPublisher.isActive()) {
                ThreadUtils.randomSleep(100);
            }
            return 0;
        }).thenApply(n -> {
            while (getConcurrents().get() > 0) {
                ThreadUtils.randomSleep(100);
            }
            return getConcurrents().get();
        }).completeOnTimeout(0, webCrawlerProperties.getEstimatedCompletionDelayDuration(),
                TimeUnit.MINUTES).thenAccept(cons -> {
                    log.info("Final Concurrents: {}", cons);
                    taskTimer.removeBatch(DefaultWebCrawlerExecutionContext.this);
                    applicationEventPublisher.publishEvent(new WebCrawlerInterruptEvent(
                            DefaultWebCrawlerExecutionContext.this, catalogDetails));
                });
    }

}
