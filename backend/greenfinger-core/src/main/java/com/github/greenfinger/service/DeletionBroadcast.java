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

import java.util.Set;
import com.github.greenfinger.core.model.DeleteLayer;

/**
 * Tells the other nodes to repeat the half of a deletion that only removed this node's own copy.
 *
 * <p>
 * Rows, blobs and vectors go through replicating stores, so removing them here removes them
 * everywhere. Two layers are not behind a store: the embedded index, handed out undecorated by
 * {@code ClusterOutputFactory}, and the RocksDB directories that {@code DeletionService} walks as
 * plain files. Without this, peers keep a Lucene directory and a frontier for a version whose rows
 * are gone.
 *
 * <p>
 * Not a "replicate" sink: there is no write to copy, only a removal each node performs against its
 * own paths, so what travels is the instruction.
 *
 * @Description: DeletionBroadcast
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
@FunctionalInterface
public interface DeletionBroadcast {

    /**
     * Asks every other node to remove its own copy of the unreplicated layers. Never throws and
     * never blocks the delete that prompted it -- failing a delete because a peer is slow would
     * leave the caller with no idea what actually happened.
     *
     * @param catalogId the catalog
     * @param version   the version, or null for every version of it
     * @param layers    what was asked for. {@code INDEX} carries the index, {@code DB} carries the
     *                  crawl state directories, and the other two are already replicated
     * @param dropIndex true when the whole index is being dropped rather than emptied, which is
     *                  the one way deleting a catalog differs from cleaning it
     */
    void purgeElsewhere(String catalogId, Integer version, Set<DeleteLayer> layers,
            boolean dropIndex);

    /** One process is a cluster of one, and has nobody to tell. */
    DeletionBroadcast NONE = (catalogId, version, layers, dropIndex) -> {};

}
