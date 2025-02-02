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

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.StringRedisTemplate;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: RedisUrlPathFilter
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@Slf4j
public class RedisUrlPathFilter extends RedisBasedUrlPathFilter {

    private final StringRedisTemplate redisTemplate;

    public RedisUrlPathFilter(long catalogId, int version,
            RedisConnectionFactory redisConnectionFactory) {
        super(catalogId, version);
        this.redisTemplate = new StringRedisTemplate(redisConnectionFactory);
    }

    @Override
    public boolean mightExist(String path) {
        boolean existed;
        if (!(existed = redisTemplate.opsForSet().isMember(key, path))) {
            redisTemplate.opsForSet().add(key, path);
        }
        return existed;
    }

    @Override
    public void clean() {
        if (redisTemplate.hasKey(key)) {
            redisTemplate.delete(key);
            if (log.isInfoEnabled()) {
                log.info("Clean RedisUrlPathFilter: {}", key);
            }
        }
    }

    @Override
    public long size() {
        return redisTemplate.opsForSet().size(key);
    }

    @Override
    public int export(UrlPathFilterExporter exporter, boolean deleted) throws Exception {
        Cursor<String> cursor = redisTemplate.opsForSet().scan(key, null);
        String item;
        int n = 0;
        while (cursor.hasNext()) {
            item = cursor.next();
            if (exporter != null) {
                if (exporter.doExport(n++, item)) {
                    if (deleted) {
                        redisTemplate.opsForSet().remove(key, item);
                    }
                } else {
                    break;
                }
            }
        }
        return n;
    }

}
