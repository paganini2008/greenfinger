package com.github.greenfinger.components;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.redis.core.RedisTemplate;
import com.github.doodler.common.utils.MapUtils;
import com.github.greenfinger.CatalogDetails;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @Description: RedisDashboardFactory
 * @Author: Fred Feng
 * @Date: 26/01/2025
 * @Version 1.0.0
 */
@RequiredArgsConstructor
public class RedisDashboardFactory implements DashboardFactory {

    private final RedisTemplate<String, Object> redisTemplate;

    private final Map<Long, Dashboard> snapshots = new ConcurrentHashMap<>();

    @Override
    public Dashboard getDashboard(CatalogDetails catalogDetails) {
        return new RedisDashboard(catalogDetails, redisTemplate.getConnectionFactory());
    }

    @Override
    public Dashboard getReadyonlyDashboard(CatalogDetails catalogDetails) {
        String keyPattern = String.format(RedisGlobalStateManager.NAMESPACE_PATTERN,
                catalogDetails.getId(), catalogDetails.getVersion(), "*");
        Set<String> keys = redisTemplate.keys(keyPattern);
        if (CollectionUtils.isNotEmpty(keys)) {
            return MapUtils.getOrCreate(snapshots, catalogDetails.getId(),
                    () -> new ReadonlyDashboard(getDashboard(catalogDetails)));
        }
        return MapUtils.getOrCreate(snapshots, catalogDetails.getId(),
                () -> new EmptyDashboard(catalogDetails));
    }


}
