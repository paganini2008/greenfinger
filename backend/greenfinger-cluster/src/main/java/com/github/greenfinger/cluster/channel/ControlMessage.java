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
 * 
 * @Description: ControlMessage
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ControlMessage(Type type, String catalogId, String action, int version,
        boolean refresh, String reason, boolean interrupted) {

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
         * A crawl is over. Every node publishes a completion event locally.
         *
         * <p>
         * The state of it was already shared and still is: the completion flag and the reason sit
         * beside the counters, and any node can read them whenever it likes. This is not a second
         * copy of that fact to be kept in step with it -- nothing acts on this message except to
         * hand it to whatever is listening. What it adds is a moment, which a flag in a cache does
         * not have: an application that wants to do something when a crawl finishes had to poll
         * for it, arriving up to one interval late with nothing to hang the work on.
         *
         * <p>
         * Sent once, by the node winding the run down, and heard by every node including that one.
         */
        COMPLETED,

        /**
         * Put back any file of this version that is missing here.
         *
         * <p>
         * Unlike everything else a replay rebuilds, files are not one shared thing: every node
         * keeps its own full copy, so "what is missing" has a different answer on each of them.
         * That makes slicing the work across nodes wrong -- a node handed a slice would check its
         * own files, which are fine, and report nothing to do while the node that actually lost
         * them was never asked. So this goes to everybody, and each node repairs itself. A node
         * with nothing missing sends no requests at all, which is what makes asking all of them
         * cheap.
         */
        RESTORE_FILES
    }

    public static ControlMessage started(String catalogId, String action, int version,
            boolean refresh) {
        return new ControlMessage(Type.STARTED, catalogId, action, version, refresh, null, false);
    }

    public static ControlMessage completed(String catalogId, int version, String reason,
            boolean interrupted) {
        return new ControlMessage(Type.COMPLETED, catalogId, null, version, false, reason,
                interrupted);
    }

    /**
     * @param origin the node that has already done this locally, so it does not do it twice on
     *               hearing its own announcement
     */
    public static ControlMessage restoreFiles(String catalogId, int version, String origin) {
        return new ControlMessage(Type.RESTORE_FILES, catalogId, null, version, false, origin,
                false);
    }

}
