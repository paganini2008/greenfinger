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

package com.github.greenfinger.core.output;

import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.model.OutputType;

/**
 * One destination a crawl feeds. Several are active at once, and they run in a fixed order --
 * file, then index, then vector -- because the later two are built from what the earlier one wrote.
 * 
 * @Description: OutputChannel
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public interface OutputChannel extends AutoCloseable {

    String getName();

    OutputType getType();

    /**
     * Prepares the destination: a directory, an index, a collection.
     */
    void open(CatalogDetails catalogDetails) throws Exception;

    /**
     * Writes one page. Called from the crawl threads, so implementations must be thread safe.
     */
    void write(OutputPayload payload) throws Exception;

    void flush() throws Exception;

    /**
     * Finishes the destination: writes the settings file, refreshes the index, commits the
     * collection.
     */
    @Override
    void close() throws Exception;

    /**
     * Whether a failure here should stop the crawl.
     *
     * <p>
     * True for the file layer, which is the source everything else is rebuilt from. False for the
     * index and the vectors: one Elasticsearch hiccup must not destroy a whole crawl, and what was
     * missed can be replayed from the database afterwards.
     */
    default boolean isRequired() {
        return getType() == OutputType.FILE;
    }

}
