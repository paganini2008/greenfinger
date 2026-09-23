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

package com.github.greenfinger.matrix;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.vector.EmbeddingClient;
import com.github.greenfinger.output.vector.EmbeddingProperties;
import com.github.greenfinger.service.EnableGreenfingerCrawler;

/**
 * The whole crawler, with the embedding model replaced and nothing else.
 *
 * <p>
 * Primary rather than same-named: the crawler's own configuration is imported, so a bean declared
 * here under the same name would be a duplicate definition rather than an override. This is the
 * shape every override in this project takes.
 * 
 * @Description: MatrixTestApplication
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
@EnableGreenfingerCrawler
@SpringBootApplication
public class MatrixTestApplication {

    @Bean
    @Primary
    public OutputFactory matrixOutputFactory(OutputProperties outputProperties,
            EmbeddingProperties embeddingProperties) {
        return new OutputFactory(outputProperties, embeddingProperties) {

            @Override
            public EmbeddingClient getEmbeddingClient() {
                return new StubEmbeddingClient();
            }
        };
    }

}
