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

/**
 * 
 * @Description: WebCrawlerRunningException
 * @Author: Fred Feng
 * @Date: 20/01/2025
 * @Version 1.0.0
 */
public class WebCrawlerRunningException extends WebCrawlerException {

    private static final long serialVersionUID = -3946759450484990969L;

    public WebCrawlerRunningException(String msg) {
        super(msg);
    }

    public WebCrawlerRunningException(String msg, Throwable e) {
        super(msg, e);
    }

    public WebCrawlerRunningException(Throwable e) {
        super(e);
    }

}
