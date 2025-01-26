package com.github.greenfinger;

import java.util.List;
import com.github.doodler.common.context.ManagedBeanLifeCycle;
import com.github.doodler.common.transmitter.Packet;
import com.github.greenfinger.components.ExistingUrlPathFilter;
import com.github.greenfinger.components.Extractor;
import com.github.greenfinger.components.GlobalStateManager;
import com.github.greenfinger.components.InterruptionChecker;
import com.github.greenfinger.components.UrlPathAcceptor;

/**
 * 
 * @Description: WebCrawlerExecutionContext
 * @Author: Fred Feng
 * @Date: 12/01/2025
 * @Version 1.0.0
 */
public interface WebCrawlerExecutionContext extends ManagedBeanLifeCycle {

    CatalogDetails getCatalogDetails();

    List<InterruptionChecker> getInterruptionCheckers();

    List<UrlPathAcceptor> getUrlPathAcceptors();

    Extractor getExtractor();

    ExistingUrlPathFilter getExistingUrlPathFilter();

    GlobalStateManager getGlobalStateManager();

    boolean isUrlAcceptable(String referUrl, String path, Packet packet);

    boolean isCompleted();

    boolean shouldInterrupt();

}
