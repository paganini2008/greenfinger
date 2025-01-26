package com.github.greenfinger.components;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.redis.connection.RedisConnectionFactory;
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

    private final RedisConnectionFactory redisConnectionFactory;

    private final Map<Long, Dashboard> snapshots = new ConcurrentHashMap<>();

    @Override
    public Dashboard getDashboard(CatalogDetails catalogDetails) {
        return new RedisDashboard(catalogDetails, redisConnectionFactory);
    }

    @Override
    public Dashboard getReadyonlyDashboard(CatalogDetails catalogDetails) {
        return MapUtils.getOrCreate(snapshots, catalogDetails.getId(),
                () -> new ReadonlyDashboard(getDashboard(catalogDetails)));
    }


}
