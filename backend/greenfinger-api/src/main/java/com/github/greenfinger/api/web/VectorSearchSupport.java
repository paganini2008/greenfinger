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

package com.github.greenfinger.api.web;

import com.github.greenfinger.core.utils.BeanLifeCycleUtils;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.output.vector.EmbeddingClient;
import com.github.greenfinger.output.vector.VectorSearcher;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

/**
 * Holds the one embedding client the server needs for searching.
 *
 * <p>
 * The command line builds one per command and throws it away, which is right for a process that
 * exits straight afterwards. A server must not: loading the local model takes seconds and a few
 * hundred megabytes, and doing that per request would make every search unusable. So it is built
 * on the first search that needs it -- never at startup, since a deployment that only ever uses
 * Elasticsearch should not pay for a model it will not call -- and kept until shutdown.
 *
 * @Description: VectorSearchSupport
 * @Author: Fred Feng
 * @Date: 31/08/2026
 * @Version 2.0.0
 */
@RequiredArgsConstructor
public class VectorSearchSupport {

    private final OutputFactory outputFactory;

    private volatile EmbeddingClient embeddingClient;
    private volatile VectorSearcher vectorSearcher;

    public VectorSearcher getVectorSearcher() throws Exception {
        VectorSearcher searcher = vectorSearcher;
        if (searcher == null) {
            synchronized (this) {
                searcher = vectorSearcher;
                if (searcher == null) {
                    EmbeddingClient client = outputFactory.sharedEmbeddingClient();
                    BeanLifeCycleUtils.afterPropertiesSet(client);
                    // assigned only once initialisation has succeeded, so a failed model download
                    // leaves nothing half-built behind for the next request to trip over
                    this.embeddingClient = client;
                    searcher = outputFactory.getVectorSearcher(client);
                    this.vectorSearcher = searcher;
                }
            }
        }
        return searcher;
    }

    @PreDestroy
    public void destroy() {
        // not closed: the client belongs to the process, and closing it here would leave the
        // next search, crawl or replay talking to a shut model
        this.embeddingClient = null;
        this.vectorSearcher = null;
    }

}
