package com.github.greenfinger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import com.github.doodler.common.context.ApplicationContextUtils;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.utils.MapUtils;
import com.github.doodler.common.utils.SerializableTaskTimer;
import com.github.greenfinger.model.Catalog;
import com.github.greenfinger.model.CatalogIndex;
import com.github.greenfinger.test.InterruptibleCondition;
import com.github.greenfinger.test.OneTimeDashboardData;
import com.github.greenfinger.test.WebCrawler;
import com.github.greenfinger.utils.ExistingUrlPathFilter;
import com.github.greenfinger.utils.PageSourceExtractor;
import lombok.Getter;

/**
 * 
 * @Description: WebCrawlerExecutionContext
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@Getter
public final class WebCrawlerExecutionContext implements Runnable {

    private static final Map<Long, WebCrawlerExecutionContext> cache = new ConcurrentHashMap<>();

    public static WebCrawlerExecutionContext get(long catalogId) {
        return MapUtils.getOrCreate(cache, catalogId, () -> {
            WebCrawlerExecutionContext context = new WebCrawlerExecutionContext(catalogId);
            return ApplicationContextUtils.autowireBean(context);
        });
    }

    private final long catalogId;

    private List<InterruptibleCondition> interruptedConditions;

    private List<UrlPathAcceptor> urlPathAcceptors;

    private PageSourceExtractor pageSourceExtractor;

    private ExistingUrlPathFilter existingUrlPathFilter;

    private OneTimeDashboardData dashboardData;

    WebCrawlerExecutionContext(long catalogId) {
        this.catalogId = catalogId;
    }

    private Catalog catalog;

    @Autowired
    public void configure(WebCrawler webCrawler) {
        this.interruptedConditions = webCrawler.getInterruptedConditions();
        this.urlPathAcceptors = webCrawler.getUrlPathAcceptors();
        this.pageSourceExtractor = webCrawler.getPageSourceExtractor();
    }

    @Autowired
    public void configure2(ResourceManager resourceManager,
            RedisConnectionFactory redisConnectionFactory) {
        this.catalog = resourceManager.getCatalog(catalogId);
        CatalogIndex catalogIndex = resourceManager.getCatalogIndex(catalogId);
        this.existingUrlPathFilter = new RedisBloomUrlPathFilter(catalogId,
                catalogIndex.getVersion(), redisConnectionFactory);
        this.dashboardData = new OneTimeDashboardData(catalogId, catalogIndex.getVersion(),
                redisConnectionFactory);
    }

    @Autowired
    public void configure(SerializableTaskTimer timer) {
        timer.addBatch(this);
    }

    public boolean acceptUrlPath(String refer, String path, Packet packet) {
        for (UrlPathAcceptor urlPathAcceptor : urlPathAcceptors) {
            if (!urlPathAcceptor.accept(refer, path, packet)) {
                return false;
            }
        }
        return true;
    }

    public boolean isCompleted() {
        return dashboardData.isCompleted();
    }

    public boolean isInterrupted() {
        for (InterruptibleCondition interruptedCondition : interruptedConditions) {
            if (interruptedCondition.shouldInterrupt(dashboardData, catalog)) {
                dashboardData.setCompleted(true);
                break;
            }
        }
        return dashboardData.isCompleted();
    }

    @Override
    public void run() {
        isInterrupted();
    }

}
