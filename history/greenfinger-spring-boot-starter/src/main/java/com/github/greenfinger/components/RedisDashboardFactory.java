/*
 * Copyright 2017-2025 Fred Feng (paganini.fy@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
