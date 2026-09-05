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

package com.github.greenfinger.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.output.BlobStore;
import com.github.greenfinger.core.output.OutputChannel;
import com.github.greenfinger.output.blob.LocalBlobStore;
import com.github.greenfinger.output.blob.MinioBlobStore;
import com.github.greenfinger.output.index.ElasticsearchIndexAdmin;
import com.github.greenfinger.output.index.ElasticsearchOutputChannel;
import com.github.greenfinger.output.index.ElasticsearchSearcher;
import com.github.greenfinger.output.index.LuceneIndexAdmin;
import com.github.greenfinger.output.index.LuceneOutputChannel;
import com.github.greenfinger.output.index.LuceneSearcher;
import com.github.greenfinger.output.vector.LuceneVectorStore;
import com.github.greenfinger.output.blob.FileOutputChannel;
import com.github.greenfinger.output.vector.EmbeddingProperties;
import com.github.greenfinger.output.vector.OllamaEmbeddingClient;
import com.github.greenfinger.output.vector.QdrantVectorStore;
import com.github.greenfinger.output.vector.VectorOutputChannel;
import com.github.greenfinger.output.vector.WeaviateVectorStore;

/**
 * 
 * @Description: OutputFactoryTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
class OutputFactoryTest {

    private final OutputProperties outputProperties = new OutputProperties();
    private final EmbeddingProperties embeddingProperties = new EmbeddingProperties();
    private final OutputFactory factory =
            new OutputFactory(outputProperties, embeddingProperties);

    @Test
    void fileOnlyByDefault() {
        CatalogDetails details = OutputFixtures.catalogDetails();
        BlobStore blobStore = factory.getBlobStore();
        CompositeOutputChannel composite =
                factory.getOutputChannel(details, blobStore, null);

        assertThat(composite.getChannels()).hasSize(1);
        assertThat(composite.getChannels().get(0)).isInstanceOf(FileOutputChannel.class);
    }

    @Test
    void outputsStackRatherThanExclude() {
        CatalogDetails details = OutputFixtures
                .catalogDetails(Set.of(OutputType.FILE, OutputType.INDEX, OutputType.VECTOR));
        embeddingProperties.setProvider("ollama");
        CompositeOutputChannel composite = factory.getOutputChannel(details,
                factory.getBlobStore(), factory.getEmbeddingClient());

        assertThat(composite.getChannels()).hasSize(3);
        assertThat(composite.getChannels().stream().map(OutputChannel::getType))
                .containsExactly(OutputType.FILE, OutputType.INDEX, OutputType.VECTOR);
    }

    @Test
    void fileIsAlwaysFirstSoTheOthersCanReadWhatItWrote() {
        CatalogDetails details =
                OutputFixtures.catalogDetails(Set.of(OutputType.INDEX, OutputType.VECTOR));
        embeddingProperties.setProvider("ollama");
        CompositeOutputChannel composite = factory.getOutputChannel(details,
                factory.getBlobStore(), factory.getEmbeddingClient());

        assertThat(composite.getChannels().get(0).getType()).isEqualTo(OutputType.FILE);
    }

    @Test
    @DisplayName("the embedded index is the default, and Elasticsearch is one setting away")
    void indexChannelIsBuiltWhenAsked() {
        CatalogDetails details =
                OutputFixtures.catalogDetails(Set.of(OutputType.FILE, OutputType.INDEX));
        assertThat(factory.getOutputChannel(details, factory.getBlobStore(), null)
                .getChannels().get(1)).isInstanceOf(LuceneOutputChannel.class);
        assertThat(factory.getSearcher()).isInstanceOf(LuceneSearcher.class);
        assertThat(factory.getIndexAdmin()).isInstanceOf(LuceneIndexAdmin.class);

        outputProperties.getIndex().setProvider("elasticsearch");
        assertThat(factory.getOutputChannel(details, factory.getBlobStore(), null)
                .getChannels().get(1)).isInstanceOf(ElasticsearchOutputChannel.class);
        assertThat(factory.getSearcher()).isInstanceOf(ElasticsearchSearcher.class);
        assertThat(factory.getIndexAdmin()).isInstanceOf(ElasticsearchIndexAdmin.class);

        outputProperties.getIndex().setProvider("solr");
        assertThatThrownBy(factory::getSearcher)
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("solr");
        assertThatThrownBy(factory::getIndexAdmin)
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("solr");
    }

    @Test
    void vectorChannelIsBuiltWhenAsked() {
        CatalogDetails details =
                OutputFixtures.catalogDetails(Set.of(OutputType.FILE, OutputType.VECTOR));
        embeddingProperties.setProvider("ollama");
        CompositeOutputChannel composite = factory.getOutputChannel(details,
                factory.getBlobStore(), factory.getEmbeddingClient());
        assertThat(composite.getChannels().get(1)).isInstanceOf(VectorOutputChannel.class);
    }

    @Test
    void localAndMinioAreTheTwoFileTargets() {
        assertThat(factory.getBlobStore()).isInstanceOf(LocalBlobStore.class);

        outputProperties.getFile().setTarget("minio");
        assertThat(factory.getBlobStore()).isInstanceOf(MinioBlobStore.class);

        outputProperties.getFile().setTarget("nowhere");
        assertThatThrownBy(factory::getBlobStore)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("nowhere");
    }

    @Test
    @DisplayName("three vector stores, and the embedded one is the default")
    void luceneQdrantAndWeaviateAreTheVectorStores() {
        assertThat(factory.getVectorStore()).isInstanceOf(LuceneVectorStore.class);

        outputProperties.getVector().setStore("qdrant");
        assertThat(factory.getVectorStore()).isInstanceOf(QdrantVectorStore.class);

        outputProperties.getVector().setStore("weaviate");
        assertThat(factory.getVectorStore()).isInstanceOf(WeaviateVectorStore.class);

        outputProperties.getVector().setStore("faiss");
        assertThatThrownBy(factory::getVectorStore)
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("faiss");
    }

    @Test
    void embeddingProvidersAreSelectedByName() {
        embeddingProperties.setProvider("ollama");
        assertThat(factory.getEmbeddingClient()).isInstanceOf(OllamaEmbeddingClient.class);

        embeddingProperties.setProvider("nowhere");
        assertThatThrownBy(factory::getEmbeddingClient)
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("nowhere");
    }

    @Test
    void layoutCarriesTheCatalogAndVersion() {
        assertThat(factory.getFileLayout(OutputFixtures.catalogDetails()).versionPrefix())
                .isEqualTo(OutputFixtures.CATALOG_ID + "/v0");
    }

}
