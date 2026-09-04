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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.vector.EmbeddingProperties;

/**
 * The one embedding client the server keeps.
 *
 * <p>
 * What is worth asserting is the failure: a model that could not be loaded must leave nothing
 * behind, or the next search would find a half-built searcher and fail in some less obvious way.
 *
 * @Description: VectorSearchSupportTest
 * @Author: Fred Feng
 * @Date: 31/08/2026
 * @Version 2.0.0
 */
class VectorSearchSupportTest {

    @Test
    @DisplayName("a provider that cannot be built is raised, and nothing half-built is kept")
    void keepsNothingWhenTheClientCannotBeBuilt() {
        EmbeddingProperties embedding = new EmbeddingProperties();
        embedding.setProvider("not-a-provider");
        VectorSearchSupport support =
                new VectorSearchSupport(new OutputFactory(new OutputProperties(), embedding));

        assertThatThrownBy(support::getVectorSearcher)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("not-a-provider");
        // the second call fails the same way rather than returning something from the first
        assertThatThrownBy(support::getVectorSearcher)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("shutting down with nothing ever built is not an error")
    void destroysCleanlyWithoutHavingBeenUsed() {
        VectorSearchSupport support = new VectorSearchSupport(
                new OutputFactory(new OutputProperties(), new EmbeddingProperties()));

        support.destroy();
        support.destroy();

        assertThat(support).isNotNull();
    }

}
