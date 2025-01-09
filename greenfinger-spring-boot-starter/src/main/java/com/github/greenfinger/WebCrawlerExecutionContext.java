package com.github.greenfinger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import com.github.doodler.common.context.ApplicationContextUtils;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.utils.MapUtils;
import com.github.doodler.common.utils.SerializableTaskTimer;
import com.github.greenfinger.model.Catalog;
import com.github.greenfinger.model.CatalogIndex;
import com.github.greenfinger.test.InterruptionChecker;
import com.github.greenfinger.test.OneTimeDashboardData;
import com.github.greenfinger.utils.ExistingUrlPathFilter;
import com.github.greenfinger.utils.Extractor;
import com.github.greenfinger.utils.RobotRuleFilter;
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

    @Autowired
    private List<InterruptionChecker> interruptionCheckers;

    @Autowired
    private RobotRuleFilter robotRuleFilter;

    @Autowired
    private List<UrlPathAcceptor> urlPathAcceptors;

    @Autowired
    private Extractor pageSourceExtractor;

    private ExistingUrlPathFilter existingUrlPathFilter;

    private OneTimeDashboardData dashboardData;

    WebCrawlerExecutionContext(long catalogId) {
        this.catalogId = catalogId;
    }

    private Catalog catalog;

    @Autowired
    public void configure2(ResourceManager resourceManager,
            RedisConnectionFactory redisConnectionFactory, RedissonClient redissonClient) {
        this.catalog = resourceManager.getCatalog(catalogId);
        CatalogIndex catalogIndex = resourceManager.getCatalogIndex(catalogId);
        this.existingUrlPathFilter = new RedissionBloomUrlPathFilter(catalogId,
                catalogIndex.getVersion(), redissonClient);
        this.dashboardData = new OneTimeDashboardData(catalogId, catalogIndex.getVersion(),
                redisConnectionFactory);
    }

    @Autowired
    public void configure(SerializableTaskTimer timer) {
        timer.addBatch(this);
    }

    public boolean isUrlAllowable(String refer, String path) {
        return robotRuleFilter.isAllowed(path);
    }

    public boolean isUrlAcceptable(String refer, String path, Packet packet) {
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

    public boolean shouldInterrupt() {
        for (InterruptionChecker interruptionChecker : interruptionCheckers) {
            if (interruptionChecker.shouldInterrupt(dashboardData, catalog)) {
                dashboardData.setCompleted(true);
                break;
            }
        }
        return dashboardData.isCompleted();
    }

    @Override
    public void run() {
        shouldInterrupt();
    }

}
