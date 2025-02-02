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

import org.springframework.context.ApplicationEvent;

/**
 * 
 * @Description: WebCrawlerEvent
 * @Author: Fred Feng
 * @Date: 24/01/2025
 * @Version 1.0.0
 */
public class WebCrawlerEvent extends ApplicationEvent {

    private static final long serialVersionUID = -6385533273344685642L;

    public WebCrawlerEvent(Object source, CatalogDetails catalogDetails) {
        super(source);
        this.catalogDetails = catalogDetails;
    }

    private final CatalogDetails catalogDetails;

    public CatalogDetails getCatalogDetails() {
        return catalogDetails;
    }

}
