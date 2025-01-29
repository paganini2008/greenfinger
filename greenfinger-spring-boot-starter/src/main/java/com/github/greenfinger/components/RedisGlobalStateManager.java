package com.github.greenfinger.components;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.support.atomic.RedisAtomicLong;
import com.github.doodler.common.utils.DateUtils;
import com.github.doodler.common.utils.LruList;
import com.github.doodler.common.utils.MapUtils;
import com.github.greenfinger.CatalogDetails;

/**
 * 
 * @Description: RedisGlobalStateManager
 * @Author: Fred Feng
 * @Date: 26/01/2025
 * @Version 1.0.0
 */
public class RedisGlobalStateManager implements GlobalStateManager, InitializingBean {

    public static final String NAMESPACE_PATTERN = "greenfinger:dashboard:%s:%s:%s";

    private final CatalogDetails catalogDetails;
    private final RedisGenericDataType<String> members;
    private final RedisDashboard redisDashboard;
    private final boolean initialized;

    public RedisGlobalStateManager(CatalogDetails catalogDetails,
            RedisConnectionFactory redisConnectionFactory, boolean initialized) {
        members = new RedisGenericDataType<String>(
                String.format(NAMESPACE_PATTERN, catalogDetails.getId(),
                        catalogDetails.getVersion(), "members"),
                String.class, redisConnectionFactory);
        redisDashboard = new RedisDashboard(catalogDetails, redisConnectionFactory);
        this.catalogDetails = catalogDetails;
        this.initialized = initialized;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (initialized) {
            redisDashboard.afterPropertiesSet();
        }
    }

    @Override
    public void addMember(String instanceId) {
        members.leftPush(instanceId);
    }

    @Override
    public void removeMember(String instanceId) {
        members.removeFromList(1, instanceId);
    }

    @Override
    public List<String> getMembers() {
        return members.list(0, members.sizeOfList());
    }

    @Override
    public void setCompleted(boolean completed) {
        redisDashboard.completed.set(completed);
    }

    @Override
    public long incrementCount(long startTime, CountingType countingType, int delta) {
        RedisAtomicLong longCounter = null;
        switch (countingType) {
            case URL_TOTAL_COUNT:
                longCounter = redisDashboard.totalUrlCount;
                break;
            case INVALID_URL_COUNT:
                longCounter = redisDashboard.invalidUrlCount;
                break;
            case EXISTING_URL_COUNT:
                longCounter = redisDashboard.existingUrlCount;
                break;
            case FILTERED_URL_COUNT:
                longCounter = redisDashboard.filteredUrlCount;
                break;
            case SAVED_RESOURCE_COUNT:
                longCounter = redisDashboard.savedResourceCount;
                break;
            case INDEXED_RESOURCE_COUNT:
                longCounter = redisDashboard.indexedResourceCount;
                break;
            default:
                throw new UnsupportedOperationException(
                        "Unknown incremental counting type: " + countingType);
        }
        try {
            if (delta == 1) {
                return longCounter.incrementAndGet();
            }
            return longCounter.addAndGet(delta);
        } finally {
            List<Long> elapsed = MapUtils.getOrCreate(redisDashboard.elapsed, countingType,
                    () -> new LruList<>(256));
            elapsed.add(System.currentTimeMillis() - startTime);
            redisDashboard.lastModified = System.currentTimeMillis();
        }
    }

    @Override
    public CatalogDetails getCatalogDetails() {
        return catalogDetails;
    }

    @Override
    public Dashboard getDashboard() {
        return redisDashboard;
    }

    @Override
    public boolean isCompleted() {
        return redisDashboard.isCompleted();
    }

    @Override
    public boolean isTimeout(long delay, TimeUnit timeUnit) {
        return System.currentTimeMillis() - redisDashboard.getLastModified() > DateUtils
                .convertToMillis(delay, timeUnit);
    }

}
