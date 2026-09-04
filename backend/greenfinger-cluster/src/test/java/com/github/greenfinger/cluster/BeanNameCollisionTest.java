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

package com.github.greenfinger.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import com.github.greenfinger.record.GreenfingerRecordConfiguration;
import com.github.greenfinger.service.GreenfingerConfiguration;

/**
 * That this module's beans are named differently from the ones they replace.
 *
 * <p>
 * Core's configuration is imported by the application rather than auto-configured, so it is
 * processed first. A bean here with the same method name as one there is therefore not an
 * override: it is a second definition of the same name, and the application does not start at all
 * -- "a bean with that name has already been defined", before anything has had a chance to run.
 *
 * <p>
 * Every override in this module is consequently a differently named {@code @Primary} bean, and a
 * new one that forgets is caught here rather than by the first person to launch the jar.
 * 
 * @Description: BeanNameCollisionTest
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
class BeanNameCollisionTest {

    @Test
    @DisplayName("no cluster bean is named after one in core")
    void clusterBeansDoNotCollideWithCore() {
        Set<String> core = beanNames(GreenfingerConfiguration.class);
        core.addAll(beanNames(GreenfingerRecordConfiguration.class));

        Set<String> clustered = beanNames(GreenfingerClusterAutoConfiguration.class);
        assertThat(clustered).isNotEmpty();
        assertThat(core).isNotEmpty();

        assertThat(clustered.stream().filter(core::contains).toList())
                .as("these would be duplicate definitions, not overrides").isEmpty();
    }

    private Set<String> beanNames(Class<?> configuration) {
        return Arrays.stream(configuration.getDeclaredMethods())
                .filter(method -> method.getAnnotation(Bean.class) != null)
                .map(Method::getName).collect(Collectors.toCollection(java.util.HashSet::new));
    }

}
