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

import java.nio.charset.Charset;
import org.springframework.http.HttpStatus;
import com.github.doodler.common.transmitter.Packet;
import com.github.greenfinger.CatalogDetails;

/**
 * 
 * @Description: Extractor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public interface Extractor extends WebCrawlerComponent {

    default String test(String url, Charset pageEncoding) throws Exception {
        return extractHtml(null, url, url, pageEncoding, null);
    }

    String extractHtml(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, Packet packet) throws Exception;

    default String defaultHtml(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, Packet packet, Throwable e) {
        return "";
    }

    /**
     * 
     * @Description: Result
     * @Author: Fred Feng
     * @Date: 30/01/2025
     * @Version 1.0.0
     */
    interface Result {

        HttpStatus getHttpStatus();

        String getContent();

        long getElasped();
    }

}
