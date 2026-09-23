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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import com.github.greenfinger.core.model.DeleteLayer;

/**
 * How the layers of a delete are written down for the other nodes to read back.
 * 
 * @Description: ClusterDeletionBroadcastTest
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
class ClusterDeletionBroadcastTest {

    @Test
    @DisplayName("the layers travel in the form DeleteLayer.parse reads")
    void layersRoundTrip() {
        Set<DeleteLayer> layers = EnumSet.of(DeleteLayer.DB, DeleteLayer.INDEX);

        String repr = ClusterDeletionBroadcast.repr(layers);

        assertThat(DeleteLayer.parse(repr)).isEqualTo(layers);
    }

    @Test
    @DisplayName("every layer round trips")
    void everyLayerRoundTrips() {
        Set<DeleteLayer> all = EnumSet.allOf(DeleteLayer.class);

        assertThat(DeleteLayer.parse(ClusterDeletionBroadcast.repr(all))).isEqualTo(all);
    }

    @Test
    @DisplayName("the announcement carries the catalog, the version, the layers and the drop")
    void announcesThePurge() {
        CrawlCluster cluster = mock(CrawlCluster.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<CrawlCluster> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(cluster);

        new ClusterDeletionBroadcast(provider).purgeElsewhere("cat-1", 3,
                EnumSet.of(DeleteLayer.DB, DeleteLayer.INDEX), false);

        // declaration order, which is the order a delete runs in; DeleteLayer.parse does not
        // care, and the round trip above is what that rests on
        verify(cluster).announcePurge("cat-1", 3, "index,db", false);
    }

    @Test
    @DisplayName("every version and a dropped index travel as they are")
    void announcesAWholeCatalog() {
        CrawlCluster cluster = mock(CrawlCluster.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<CrawlCluster> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(cluster);

        new ClusterDeletionBroadcast(provider).purgeElsewhere("cat-1", null,
                EnumSet.allOf(DeleteLayer.class), true);

        verify(cluster).announcePurge("cat-1", null, "vector,index,file,db", true);
    }

    @Test
    @DisplayName("no layers is not read back as all of them")
    void noLayersIsNotAll() {
        // DeleteLayer.parse reads a blank as "everything", which is right on a command line and
        // would be a catastrophe here: an announcement of nothing must not purge four stores
        assertThat(ClusterDeletionBroadcast.repr(EnumSet.noneOf(DeleteLayer.class))).isEmpty();
    }

}
