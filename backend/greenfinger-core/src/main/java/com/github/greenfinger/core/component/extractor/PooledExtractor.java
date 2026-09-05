/*
 * Copyright 2017-2026 Fred Feng (paganini.fy@gmail.com)
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

package com.github.greenfinger.core.component.extractor;

import java.time.Duration;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.greenfinger.core.ManagedBeanLifeCycle;

/**
 * Base for engines whose client is expensive to build -- a browser context, a WebDriver session --
 * and is therefore pooled rather than created per request.
 * 
 * @Description: PooledExtractor
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public abstract class PooledExtractor<T> extends AbstractExtractor
        implements NamedExtractor, ManagedBeanLifeCycle {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected GenericObjectPool<T> objectPool;
    protected ObjectPoolConfig<T> objectPoolConfig = new ObjectPoolConfig<>();

    public ObjectPoolConfig<T> getObjectPoolConfig() {
        return objectPoolConfig;
    }

    public void setObjectPoolConfig(ObjectPoolConfig<T> objectPoolConfig) {
        this.objectPoolConfig = objectPoolConfig;
    }

    protected abstract BasePooledObjectFactory<T> createObjectFactory();

    @Override
    public void afterPropertiesSet() throws Exception {
        objectPool = new GenericObjectPool<>(createObjectFactory(), getObjectPoolConfig());
    }

    @Override
    public void destroy() throws Exception {
        if (objectPool != null) {
            objectPool.close();
        }
    }

    /**
     * 
     * @Description: ObjectPoolConfig
     * @Author: Fred Feng
     * @Date: 29/08/2026
     * @Version 2.0.0
     */
    public static class ObjectPoolConfig<T> extends GenericObjectPoolConfig<T> {

        public ObjectPoolConfig() {
            setMinIdle(1);
            setMaxIdle(2);
            setMaxTotal(20);
            setTimeBetweenEvictionRuns(Duration.ofMillis(15000));
            setMinEvictableIdleDuration(Duration.ofMillis(60000));
            setBlockWhenExhausted(true);
        }

    }

}
