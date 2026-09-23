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

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import com.github.greenfinger.core.model.DeleteLayer;
import com.github.greenfinger.service.DeletionBroadcast;

/**
 * Sends the deletion instruction the stores cannot send themselves.
 *
 * <p>
 * A separate bean rather than another method on {@code CrawlCluster}, and the cluster is reached
 * through a provider, because the deletion service is downstream of it: asking for the bean by
 * type here would close the circle at startup rather than at the first delete.
 *
 * @Description: ClusterDeletionBroadcast
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
public class ClusterDeletionBroadcast implements DeletionBroadcast {

    private final ObjectProvider<CrawlCluster> cluster;

    public ClusterDeletionBroadcast(ObjectProvider<CrawlCluster> cluster) {
        this.cluster = cluster;
    }

    @Override
    public void purgeElsewhere(String catalogId, Integer version, Set<DeleteLayer> layers,
            boolean dropIndex) {
        cluster.getObject().announcePurge(catalogId, version, repr(layers), dropIndex);
    }

    /**
     * The command line form, which is what {@link DeleteLayer#parse} reads on the other side.
     * Sent as text rather than as a set of ordinals so that a node running an older build reads
     * the layers it knows and ignores the rest, instead of purging the wrong one.
     */
    static String repr(Set<DeleteLayer> layers) {
        return layers.stream().map(DeleteLayer::getRepr).collect(Collectors.joining(","));
    }

}
