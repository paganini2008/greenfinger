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

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 
 * @Description: WebCrawlerExtractorProperties
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@ConfigurationProperties("greenfinger.extractor")
@Getter
@Setter
public class WebCrawlerExtractorProperties {

    private long timeout = 1L * 60 * 1000;
    private Default restTemplate = new Default();
    private Playwright playwright = new Playwright();
    private Selenium selenium = new Selenium();
    private HtmlUnit htmlunit = new HtmlUnit();
    private ObjectPool objectPool = new ObjectPool();

    @Getter
    @Setter
    @ToString
    public static class Base {

        private Map<String, String> defaultHttpHeaders = new HashMap<>();
        private String proxyHost;
        private int proxyPort;
        private long loadingTimeout;

        public String getProxyServer() {
            if (StringUtils.isNotBlank(proxyHost) && proxyPort > 0) {
                return proxyHost + ":" + proxyPort;
            }
            return "";
        }

    }

    @Getter
    @Setter
    @ToString
    public static class Default extends Base {

        private int connectionTime = 10000;
        private int readTimeout = 60000;
    }

    @Getter
    @Setter
    @ToString
    public static class ObjectPool {

        private int minIdle = 1;
        private int maxIdle = 2;
        private int maxTotal = 20;
        private int borrowTimeout = -1;

    }

    @Getter
    @Setter
    @ToString
    public static class Playwright extends Base {

        private boolean javaScriptEnabled = true;
        private int timeout = 10 * 1000;
    }

    @Getter
    @Setter
    @ToString
    public static class Selenium extends Base {

        private String webDriverExecutionPath =
                "G:\\selfEmployed\\chromedriver-win64\\chromedriver-win64\\chromedriver.exe";

    }

    @Getter
    @Setter
    @ToString
    public static class HtmlUnit extends Base {

        private int timeout = 10 * 1000;
        private boolean javaScriptEnabled = true;
        private long javaScriptTimeout = 10L * 1000;
    }

}
