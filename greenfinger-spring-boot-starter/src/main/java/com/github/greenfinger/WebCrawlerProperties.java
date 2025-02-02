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

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

/**
 * 
 * @Description: WebCrawlerProperties
 * @Author: Fred Feng
 * @Date: 11/01/2025
 * @Version 1.0.0
 */
@ConfigurationProperties("greenfinger")
@Data
public class WebCrawlerProperties {

    private String defaultPageEncoding = "UTF-8";
    private int defaultMaxFetchSize = 10000;
    private int defaultMaxFetchDepth = -1;
    private long defaultFetchDuration = 5;
    private int defaultMaxRetryCount = 0;
    private long defaultFetchInterval = 1000L;
    private String defaultUrlPathFilter = "redission-bloomfilter";
    private String defaultExtractor = "resttemplate";
    private int workThreads = 8;
    private int estimatedCompletionDelayDuration = 5;
}
