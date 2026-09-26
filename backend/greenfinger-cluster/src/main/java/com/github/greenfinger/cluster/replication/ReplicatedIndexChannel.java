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
 * Copies index documents to the other nodes, for the embedded index, which is a directory per node.
 * A url goes to exactly one node, so without this most of the corpus is missing from every node's
 * answers. Elasticsearch is one shared cluster and needs none of it.
 *
 * <p>
 * The whole document travels rather than a "index resource X" pointer: the three channels are
 * independent and unordered, so the pointer could arrive before the row or the files, and an index
 * that silently skipped that page would be worse than the bandwidth.
 *
 * <p>
 * It costs the extracted text once per node, on top of the same text going over the blob channel --
 * which is why the startup report recommends Elasticsearch for a cluster.
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
