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

import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.github.doodler.common.redis.RedisBloomFilter;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: RedisBloomUrlPathFilter
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@Slf4j
public class RedisBloomUrlPathFilter extends RedisBasedUrlPathFilter
        implements InitializingBean {

    private static final int MAX_EXPECTED_INSERTIONS = 100_000_000;

    public RedisBloomUrlPathFilter(long catalogId, int version,
            RedisConnectionFactory redisConnectionFactory) {
        super(catalogId, version);
        this.redisConnectionFactory = redisConnectionFactory;
    }

    private final RedisConnectionFactory redisConnectionFactory;
    private RedisBloomFilter bloomFilter;


    @Override
    public void afterPropertiesSet() throws Exception {
        this.bloomFilter =
                new RedisBloomFilter(key, MAX_EXPECTED_INSERTIONS, 0.03d, redisConnectionFactory);
    }

    @Override
    public boolean mightExist(String path) {
        boolean existed;
        if ((existed = bloomFilter.mightContain(path)) == false) {
            bloomFilter.put(path);
        }
        return existed;
    }

    @Override
    public void clean() {
        StringRedisTemplate redisTemplate = new StringRedisTemplate(redisConnectionFactory);
        redisTemplate.delete(key);
        if (log.isInfoEnabled()) {
            log.info("Clean RedisUrlPathFilter: {}", key);
        }
    }
}
