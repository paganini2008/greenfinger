/**
 * Copyright 2017-2025 Fred Feng (paganini.fy@gmail.com)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.github.greenfinger.components;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.doodler.common.context.ManagedBeanLifeCycle;
import com.github.greenfinger.components.test.AbstractExtractor;

/**
 * 
 * @Description: PooledExtractor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public abstract class PooledExtractor<T> extends AbstractExtractor
        implements Extractor, ManagedBeanLifeCycle {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected ObjectPool<T> objectPool;
    protected PooledConfig<T> pooledConfig = new PooledConfig<T>();

    private final BasePooledObjectFactory<T> basePooledObjectFactory =
            new BasePooledObjectFactory<T>() {

                @Override
                public T create() throws Exception {
                    return createObject();
                }

                @Override
                public PooledObject<T> wrap(T object) {
                    return new DefaultPooledObject<T>(object);
                }

                @Override
                public void destroyObject(PooledObject<T> p) throws Exception {
                    destroyObject(p);
                }
            };

    public PooledConfig<T> getPooledConfig() {
        return pooledConfig;
    }

    public void setPooledConfig(PooledConfig<T> pooledConfig) {
        this.pooledConfig = pooledConfig;
    }

    public abstract T createObject() throws Exception;

    public abstract void destroyObject(PooledObject<T> object) throws Exception;

    @Override
    public void afterPropertiesSet() throws Exception {
        objectPool = new GenericObjectPool<T>(basePooledObjectFactory, pooledConfig);
    }

    @Override
    public void destroy() throws Exception {
        if (objectPool != null) {
            objectPool.close();
        }
    }

    /**
     * 
     * @Description: ExtractorPooledConfig
     * @Author: Fred Feng
     * @Date: 10/01/2025
     * @Version 1.0.0
     */
    public static class PooledConfig<T> extends GenericObjectPoolConfig<T> {
        PooledConfig() {
            setMinIdle(1);
            setMaxIdle(5);
            setMaxTotal(20);
        }
    }

}