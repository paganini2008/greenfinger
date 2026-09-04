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

import java.util.Locale;
import org.springframework.beans.factory.SmartInitializingSingleton;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.vector.EmbeddingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads the local models at startup rather than partway through a crawl.
 *
 * <p>
 * Only when they will actually be used: the local provider selected, and a vector output actually
 * configured. The default configuration asks for files alone, so the quick start still downloads
 * nothing and starts instantly -- but a run that is going to want the models pays for them before
 * it fetches its first page, instead of stalling several minutes in to download half a gigabyte.
 *
 * <p>
 * A failure here is logged, not thrown. A model that will not load should not stop the application
 * from starting; it should stop the crawl that needs it, with the reason in front of the person
 * who asked for it.
 * 
 * @Description: EmbeddingWarmUp
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class EmbeddingWarmUp implements SmartInitializingSingleton {

    private final EmbeddingProperties embeddingProperties;
    private final OutputProperties outputProperties;
    private final OutputFactory outputFactory;
    private final CatalogStore catalogStore;

    @Override
    public void afterSingletonsInstantiated() {
        if (!shouldWarmUp()) {
            return;
        }
        try {
            // and kept: this used to load the models and immediately close them, so the first
            // crawl built the same three onnx sessions all over again. The download was warmed;
            // the sessions were not
            outputFactory.sharedEmbeddingClient();
        } catch (Exception e) {
            log.warn("Could not preload the embedding model: {}", e.getMessage());
        }
    }

    private boolean shouldWarmUp() {
        return embeddingProperties.isPreload()
                && "local".equalsIgnoreCase(
                        embeddingProperties.getProvider().toLowerCase(Locale.ROOT))
                && vectorIsUsedSomewhere();
    }

    /**
     * The default output types are not the whole answer. A catalog carries its own, and the
     * shipped default is files alone -- so a setup where every catalog asks for vectors would
     * never have warmed up, and every crawl paid the model load instead. Asking the catalogs is
     * what makes the preload actually happen where it is needed.
     */
    private boolean vectorIsUsedSomewhere() {
        if (OutputType.parse(outputProperties.getTypes()).contains(OutputType.VECTOR)) {
            return true;
        }
        try {
            return catalogStore.findAll().stream().map(Catalog::getOutputTypes)
                    .anyMatch(types -> types != null && types.contains(OutputType.VECTOR));
        } catch (RuntimeException e) {
            // the catalogs cannot be read yet: not a reason to fail, only a reason not to preload
            log.debug("Could not check the catalogs for a vector output: {}", e.getMessage());
            return false;
        }
    }

}
