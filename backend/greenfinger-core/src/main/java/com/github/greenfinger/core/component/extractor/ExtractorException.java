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

package com.github.greenfinger.core.component.extractor;

import org.springframework.http.HttpStatusCode;
import com.github.greenfinger.core.WebCrawlerException;
import lombok.Getter;

/**
 * 
 * @Description: ExtractorException
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@Getter
public class ExtractorException extends WebCrawlerException {

    private static final long serialVersionUID = 1L;

    private final String url;
    private final HttpStatusCode httpStatus;

    public ExtractorException(String url, HttpStatusCode httpStatus) {
        super("Failed to extract '" + url + "', http status: " + httpStatus);
        this.url = url;
        this.httpStatus = httpStatus;
    }

    /** A url that answered, but with something that is not a page. */
    public ExtractorException(String url, String reason) {
        super("Refused '" + url + "': " + reason);
        this.url = url;
        this.httpStatus = null;
    }

    public ExtractorException(String url, Throwable e) {
        super("Failed to extract '" + url + "'", e);
        this.url = url;
        this.httpStatus = null;
    }

}
