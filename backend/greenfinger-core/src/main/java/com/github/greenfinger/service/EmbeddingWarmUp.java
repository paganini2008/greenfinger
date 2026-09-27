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

package com.github.greenfinger.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.output.vector.EmbeddingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads the local models at startup rather than partway through a crawl.
 *
 * <p>
 * Only when they will be used -- local provider, vector output configured -- so the default
 * files-only quick start still downloads nothing, while a run that wants them pays before its first
 * page instead of stalling to download half a gigabyte.
 *
 * <p>
 * A failure is logged, not thrown: a model that will not load should stop the crawl that needs it,
 * with the reason in front of whoever asked, not the application.
 * 
 * @Description: EmbeddingWarmUp
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class EmbeddingWarmUp implements ApplicationListener<ApplicationReadyEvent> {

    private final EmbeddingProperties embeddingProperties;
    private final OutputFactory outputFactory;

    /**
     * After the application is up, and on a thread of its own.
     *
     * <p>
     * This used to run inside the context refresh, which put the best part of a second of model
     * loading in front of the port opening. A node is useful before its models are: it can answer
     * health, join the cluster and serve what is already indexed. So the loading starts once the
     * rest is ready and does not hold anything up while it runs. A crawl that gets there first
     * loads the model itself, which is the same synchronized call and happens once either way.
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!shouldWarmUp()) {
            return;
        }
        Thread thread = new Thread(this::load, "greenfinger-embedding-warmup");
        thread.setDaemon(true);
        thread.start();
    }

    private void load() {
        try {
            long start = System.currentTimeMillis();
            // and kept: this used to load the models and immediately close them, so the first
            // crawl built the same three onnx sessions all over again. The download was warmed;
            // the sessions were not
            outputFactory.sharedEmbeddingClient();
            log.info("Embedding models preloaded in {} ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("Could not preload the embedding model: {}", e.getMessage());
        }
    }

    /**
     * Whether to load at all. No longer conditional on anything using vectors: a model that
     * arrives partway through a crawl decides this node's memory after the node has already taken
     * work, and in a container that reads as the application exiting mid-run. Preloading is still
     * a setting, so an installation that wants nothing loaded turns it off.
     */
    private boolean shouldWarmUp() {
        // ollama and openai are http services: there is no model to load into this process
        if (!"local".equalsIgnoreCase(embeddingProperties.getProvider())) {
            return false;
        }
        EmbeddingProperties.Local local = embeddingProperties.getLocal();
        return local.isPreloadTextModel() || local.isPreloadImageModel();
    }

}
