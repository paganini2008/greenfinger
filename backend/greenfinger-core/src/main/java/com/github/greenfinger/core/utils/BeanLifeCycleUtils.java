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

package com.github.greenfinger.core.utils;

import java.util.Collection;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Drives {@link InitializingBean} and {@link DisposableBean} callbacks on objects that are created
 * by hand rather than by the Spring container, which is how every pluggable crawler component is
 * assembled.
 * 
 * @Description: BeanLifeCycleUtils
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@Slf4j
@UtilityClass
public class BeanLifeCycleUtils {

    public void afterPropertiesSet(Object object) throws Exception {
        if (object instanceof Collection) {
            for (Object o : (Collection<?>) object) {
                afterPropertiesSet(o);
            }
        } else if (object instanceof InitializingBean) {
            ((InitializingBean) object).afterPropertiesSet();
        }
    }

    public void destroy(Object object) throws Exception {
        if (object instanceof Collection) {
            for (Object o : (Collection<?>) object) {
                destroy(o);
            }
        } else if (object instanceof DisposableBean) {
            ((DisposableBean) object).destroy();
        }
    }

    public void destroyQuietly(Object object) {
        try {
            destroy(object);
        } catch (Exception e) {
            if (log.isWarnEnabled()) {
                log.warn("Failed to destroy {}: {}", object, e.getMessage());
            }
        }
    }

}
