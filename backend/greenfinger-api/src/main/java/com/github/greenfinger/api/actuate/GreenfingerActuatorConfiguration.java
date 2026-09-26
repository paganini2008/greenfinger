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

package com.github.greenfinger.api.actuate;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Greenfinger's own actuator endpoints. Outside the api switch, like security is: a headless node
 * still has the port, and a node nobody can ask what it is configured with is a node nobody can
 * support.
 *
 * @Description: GreenfingerActuatorConfiguration
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(Endpoint.class)
public class GreenfingerActuatorConfiguration {

    @Bean
    public SettingsEndpoint greenfingerSettingsEndpoint(ApplicationContext applicationContext) {
        return new SettingsEndpoint(applicationContext);
    }

}
