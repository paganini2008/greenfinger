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

package com.github.greenfinger.cluster;

import com.github.greenfinger.cluster.replication.ClusterReplication;
import com.github.greenfinger.core.output.BlobStore;
import com.github.greenfinger.core.output.OutputChannel;
import com.github.greenfinger.output.vector.VectorStore;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.vector.EmbeddingProperties;

/**
 * The standard outputs, with the ones that are not shared wrapped so their writes reach every node.
 *
 * <p>
 * Which ones those are is a configuration question rather than a fixed list. Elasticsearch, Qdrant
 * and Weaviate are servers every node talks to and are left alone; a local blob directory, the
 * embedded Lucene index and the embedded vector store are one copy per node, and a page indexed on
 * one of them is missing from the others' answers until it is copied across. The database is
 * handled where the rows are written rather than here.
 * 
 * @Description: ClusterOutputFactory
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
public class ClusterOutputFactory extends OutputFactory {

    private final ClusterReplication replication;

    public ClusterOutputFactory(OutputProperties outputProperties,
            EmbeddingProperties embeddingProperties, ClusterReplication replication) {
        super(outputProperties, embeddingProperties);
        this.replication = replication;
    }

    @Override
    public BlobStore getBlobStore() {
        return replication.decorate(super.getBlobStore());
    }

    @Override
    protected OutputChannel indexChannel() {
        return replication.decorate(super.indexChannel());
    }

    @Override
    public VectorStore getVectorStore() {
        return replication.decorate(super.getVectorStore());
    }

}
