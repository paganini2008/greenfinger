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

package com.github.greenfinger.cluster.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * What the control channel carries.
 *
 * @param type       what happened
 * @param catalogId  which crawl it happened to
 * @param action     crawl or update, so a node joining knows what it is joining
 * @param version    the version being written, so a late node does not write into the wrong one
 * @param refresh    whether this run revisits pages it already has
 * @param reason     why it stopped, for the log and for the run summary
 * @param layers     which stores a purge covers, in the {@code DeleteLayer} command line form
 * @param dropIndex  whether a purge drops the index or merely empties it
 * 
 * @Description: ControlMessage
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ControlMessage(Type type, String catalogId, String action, int version,
        boolean refresh, String reason, boolean interrupted, String layers, boolean dropIndex) {

    /**
     * 
     * @Description: Type
     * @Author: Fred Feng
     * @Date: 02/09/2026
     * @Version 2.0.0
     */
    public enum Type {

        /**
         * A crawl has begun somewhere. Every other node opens its own half of it.
         */
        STARTED,

        /**
         * A crawl is over. Not a second copy of the shared flag -- nothing acts on this except
         * to hand it to whatever is listening. What it adds is a moment, which a flag in a cache
         * does not have. Sent once, by the node winding the run down, heard by every node.
         */
        COMPLETED,

        /**
         * Put back any file of this version that is missing here. Files are not one shared
         * thing -- each node has its own gaps -- so slicing is wrong and everybody repairs
         * itself. A node with nothing missing sends no requests, which makes asking all cheap.
         */
        RESTORE_FILES,

        /**
         * Remove your own copy of the layers a delete cannot replicate: the embedded index and
         * the three RocksDB directories, which are plain local files. Without this, two nodes out
         * of three keep a frontier for a version whose rows have gone -- invisible until the next
         * crawl finds a frontier it did not write.
         *
         * <p>
         * The instruction travels rather than the removal, because the paths differ per node.
         */
        PURGE_LOCAL
    }

    public static ControlMessage started(String catalogId, String action, int version,
            boolean refresh) {
        return new ControlMessage(Type.STARTED, catalogId, action, version, refresh, null, false,
                null, false);
    }

    public static ControlMessage completed(String catalogId, int version, String reason,
            boolean interrupted) {
        return new ControlMessage(Type.COMPLETED, catalogId, null, version, false, reason,
                interrupted, null, false);
    }

    /**
     * @param version null for every version of the catalog
     * @param origin  the node that has already done this locally
     */
    public static ControlMessage purgeLocal(String catalogId, Integer version, String layers,
            boolean dropIndex, String origin) {
        return new ControlMessage(Type.PURGE_LOCAL, catalogId, null,
                version != null ? version : EVERY_VERSION, false, origin, false, layers,
                dropIndex);
    }

    /** What {@link #version()} reads as when a purge covers the whole catalog. */
    public static final int EVERY_VERSION = -1;

    /**
     * @param origin the node that has already done this locally, so it does not do it twice on
     *               hearing its own announcement
     */
    public static ControlMessage restoreFiles(String catalogId, int version, String origin) {
        return new ControlMessage(Type.RESTORE_FILES, catalogId, null, version, false, origin,
                false, null, false);
    }

}
