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

package com.github.greenfinger.service;

import java.util.Map;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.ConfigurationPropertiesBean;
import org.springframework.context.ApplicationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes every setting that is actually in force into the log at startup.
 *
 * <p>
 * Configuration reaches this application from four places -- the packaged yaml, the copy beside
 * the launcher, {@code .env}, and the command line -- and which of them won is the first question
 * of most support conversations. Reading the yaml answers it wrongly whenever something overrode
 * it. This prints the objects the code will actually read, after everything has been merged, which
 * is the only version of the answer worth having. 1.x did the same thing and for the same reason.
 *
 * <p>
 * Only greenfinger's own properties: Spring's are numerous, unchanged, and would bury the ones
 * that matter.
 *
 * @Description: ConfigurationReport
 * @Author: Fred Feng
 * @Date: 04/09/2026
 * @Version 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class ConfigurationReport implements SmartInitializingSingleton {

    private static final String PACKAGE_PREFIX = "com.github.greenfinger";

    private final ApplicationContext applicationContext;

    @Override
    public void afterSingletonsInstantiated() {
        if (!log.isInfoEnabled()) {
            return;
        }
        Map<String, ConfigurationPropertiesBean> beans =
                ConfigurationPropertiesBean.getAll(applicationContext);
        beans.values().stream()
                .filter(bean -> bean.getInstance().getClass().getPackageName()
                        .startsWith(PACKAGE_PREFIX))
                .forEach(bean -> log.info("Configuration in force:{}{}", System.lineSeparator(),
                        ToStringBuilder.reflectionToString(bean.getInstance(),
                                ToStringStyle.MULTI_LINE_STYLE)));
    }

}
