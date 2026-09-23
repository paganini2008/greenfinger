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
 * Most of a delete replicates itself: rows, blobs and vectors all go through a store that copies
 * its writes, so removing them here removes them everywhere. Two things do not, and they are the
 * two that are not behind a store at all -- the embedded index, which
 * {@code ClusterOutputFactory} hands out undecorated, and the three RocksDB directories under the
 * data directory, which are plain files that {@code DeletionService} walks itself.
 *
 * <p>
 * Both were written as though every node would get round to its own copy. Nothing made that
 * happen: a delete ran where it was asked and nowhere else, so two nodes out of three kept a
 * Lucene directory and a frontier for a version whose rows had gone. That is what this exists to
 * say out loud.
 *
 * <p>
 * Deliberately not a "replicate" sink. What travels is not the write -- there is no write, only a
 * removal each node has to perform against its own paths -- so what is sent is the instruction,
 * and each node carries it out locally.
 *
 * @Description: DeletionBroadcast
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
@FunctionalInterface
public interface DeletionBroadcast {

    /**
     * Asks every other node to remove its own copy of the layers that are not replicated.
     *
     * <p>
     * Never throws and never blocks the delete that prompted it: a version removed here is removed
     * whether or not the others have heard yet, and the alternative -- failing a delete because a
     * peer is slow -- would leave the caller with no idea what did happen.
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
