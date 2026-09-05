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

package com.github.greenfinger.cluster.replication;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.output.OutputChannel;
import com.github.greenfinger.core.output.OutputPayload;
import com.github.greenfinger.output.index.LuceneIndexes;
import com.github.greenfinger.output.index.LuceneOutputChannel;
import lombok.extern.slf4j.Slf4j;

/**
 * Copies index documents to the other nodes, for the index that is not shared.
 *
 * <p>
 * Elasticsearch is one cluster every node writes to and reads from, so none of this applies to it.
 * The embedded index is a directory per node, and then a page fetched on node B is missing from
 * node A's search results -- and since a url is dispatched to exactly one node, that is most of
 * the corpus missing from every node's answer.
 *
 * <h2>Why the document and not a pointer to it</h2>
 * The row and the files are already replicated, so a node could in principle be told "index
 * resource X" and build the document from its own copies. It could also be told that before either
 * of them arrived -- the three channels are independent and nothing orders them against each other
 * -- and an index that silently missed a page whose row landed a moment later would be worse than
 * the bandwidth. So the document travels whole, and needs nothing else to have arrived.
 *
 * <h2>What it costs</h2>
 * The extracted text, once per node, on top of the same text going over the blob channel as a
 * file. A crawl of a text-heavy site therefore moves roughly twice what it would with a shared
 * index, which is the price of not having one, and is why the startup report recommends
 * Elasticsearch for a cluster rather than treating the two as equivalent.
 * 
 * @Description: ReplicatedIndexChannel
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class ReplicatedIndexChannel implements OutputChannel {

    public static final byte OP_DOCUMENT = 40;

    private final OutputChannel delegate;
    private final ReplicationSink channel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private CatalogDetails catalogDetails;

    public ReplicatedIndexChannel(OutputChannel delegate, ReplicationSink channel) {
        this.delegate = delegate;
        this.channel = channel;
    }

    @Override
    public String getName() {
        return "index";
    }

    @Override
    public OutputType getType() {
        return OutputType.INDEX;
    }

    @Override
    public void open(CatalogDetails catalogDetails) throws Exception {
        this.catalogDetails = catalogDetails;
        delegate.open(catalogDetails);
    }

    @Override
    public void write(OutputPayload payload) throws Exception {
        Map<String, Object> fields =
                LuceneOutputChannel.fieldsOf(catalogDetails, payload, objectMapper);
        // written here first: a node's own index must be right even if the cluster is not
        LuceneOutputChannel.write(indexes(), indexName(), fields);
        channel.replicate(ReplicationBatch.Entry.of(OP_DOCUMENT, catalogDetails.getId(),
                indexName(), objectMapper.writeValueAsBytes(fields)));
    }

    /**
     * Applies a document from another node.
     *
     * <p>
     * Written by id, so a document that arrives twice -- delivery is at least once -- replaces
     * itself rather than becoming two hits for one page.
     */
    public static void apply(ReplicationBatch.Entry entry, LuceneIndexes indexes,
            ObjectMapper objectMapper) {
        if (entry.op() != OP_DOCUMENT) {
            log.debug("Unknown index op: {}", entry.op());
            return;
        }
        try {
            Map<String, Object> fields = objectMapper.readValue(
                    new String(entry.value(), StandardCharsets.UTF_8),
                    new TypeReference<Map<String, Object>>() {});
            LuceneOutputChannel.write(indexes, entry.key(), fields);
        } catch (Exception e) {
            log.warn("Could not apply an index document to '{}': {}", entry.key(), e.getMessage());
        }
    }

    private String indexName() {
        return ((LuceneOutputChannel) delegate).getIndexName();
    }

    private LuceneIndexes indexes() {
        return ((LuceneOutputChannel) delegate).getOpenIndexes();
    }

    @Override
    public void flush() throws Exception {
        delegate.flush();
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }

    @Override
    public boolean isRequired() {
        return delegate.isRequired();
    }

    /**
     * Whether this catalog's index is one the cluster has to copy around: only when the provider
     * is the embedded one, since Elasticsearch is shared by definition.
     */
    public static boolean shouldReplicate(String provider) {
        return "lucene".equalsIgnoreCase(provider);
    }

}
