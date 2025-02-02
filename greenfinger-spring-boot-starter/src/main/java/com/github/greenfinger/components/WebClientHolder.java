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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import com.github.doodler.common.utils.Coookie;

/**
 * 
 * @Description: WebClientHolder
 * @Author: Fred Feng
 * @Date: 11/01/2025
 * @Version 1.0.0
 */
public interface WebClientHolder<T> extends Supplier<T> {

    T createNew();

    default void setExtraHttpHeaders(Map<String, String> headerMap) {}

    default void setExtraHttpHeader(String headerName, String headerValue) {
        setExtraHttpHeaders(Map.of(headerName, headerValue));
    }

    default void setExtraCookies(String domain, Map<String, String> cookieMap) {
        List<Coookie> coookies = cookieMap.entrySet().stream().collect(ArrayList::new,
                (l, e) -> l.add(new Coookie(domain, e.getKey(), e.getValue())), ArrayList::addAll);
        setExtraCookies(coookies);
    }

    default void setExtraCookie(String domain, String cookieName, String cookieValue) {
        setExtraCookies(domain, Map.of(cookieName, cookieValue));
    }

    default void setExtraCookies(List<Coookie> coookies) {}

}
