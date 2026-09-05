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

package com.github.greenfinger.core.component.dedup;

import com.github.greenfinger.core.ManagedBeanLifeCycle;
import com.github.greenfinger.core.component.WebCrawlerComponent;

/**
 * The first of the two dedup passes: has this url been seen before? Consulted before a fetch, so a
 * hit costs nothing but a lookup.
 *
 * @Description: ExistingUrlPathFilter
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public interface ExistingUrlPathFilter extends WebCrawlerComponent, ManagedBeanLifeCycle {

    /**
     * Records the path and reports whether it was already present. The check and the insert are one
     * operation on purpose; splitting them would let two threads both decide a url is new.
     */
    boolean mightExist(String path);

    /** Drops everything this filter holds, and the files behind it. */
    default void clean() throws Exception {}

    default long size() throws Exception {
        return -1;
    }

    default int export(UrlPathFilterExporter exporter, boolean deleted) throws Exception {
        throw new UnsupportedOperationException("export");
    }

}
