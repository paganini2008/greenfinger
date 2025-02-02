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

package com.github.greenfinger;

import java.util.Arrays;
import java.util.Map;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.ConfigurationPropertiesBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import com.github.doodler.common.utils.MapUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: ConfigurationPostApplicationRunner
 * @Author: Fred Feng
 * @Date: 26/01/2025
 * @Version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigurationPostApplicationRunner implements CommandLineRunner {

    private static final String[] targetPackageNames =
            {"com.github.greenfinger", "com.github.doodler"};

    private final ApplicationContext applicationContext;

    @Override
    public void run(String... args) throws Exception {
        Map<String, ConfigurationPropertiesBean> beans =
                ConfigurationPropertiesBean.getAll(applicationContext);
        if (MapUtils.isEmpty(beans)) {
            return;
        }
        beans.values().stream()
                .filter(bean -> Arrays.stream(targetPackageNames).anyMatch(
                        pn -> bean.getInstance().getClass().getPackageName().startsWith(pn)))
                .forEach(configBean -> {
                    ConfigurationPropertiesBean propertiesBean =
                            (ConfigurationPropertiesBean) configBean;
                    String logStr = ToStringBuilder.reflectionToString(propertiesBean.getInstance(),
                            ToStringStyle.MULTI_LINE_STYLE);
                    if (log.isInfoEnabled()) {
                        log.info(logStr);
                    }
                });

    }


}
