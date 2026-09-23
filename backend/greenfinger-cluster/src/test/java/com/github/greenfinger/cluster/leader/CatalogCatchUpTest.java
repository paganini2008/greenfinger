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

package com.github.greenfinger.cluster.leader;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.cluster.support.FakeCatalogStore;
import com.github.greenfinger.cluster.support.TestCluster;
import com.github.greenfinger.core.model.Catalog;

/**
 * Taking the leader's table, which is the whole of the repair when one node does the writing.
 * 
 * @Description: CatalogCatchUpTest
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
class CatalogCatchUpTest {

    private FakeCatalogStore local;
    private CatalogCatchUp catchUp;

    @BeforeEach
    void setUp() {
        local = new FakeCatalogStore("here");
        // no cluster: what is under test is what the leader's answer does to this table
        catchUp = new CatalogCatchUp(null, null, local, Long.MAX_VALUE);
    }

    private static Catalog catalog(String id, String name) {
        Catalog catalog = new Catalog();
        catalog.setId(id);
        catalog.setName(name);
        catalog.setUrl("https://" + name.toLowerCase().replace(' ', '-') + ".example");
        catalog.setCat("tech");
        catalog.setIndexVersion(0);
        catalog.setSearchVersion(-1);
        return catalog;
    }

    @Test
    @DisplayName("a row this node never received is taken")
    void takesWhatItIsMissing() {
        assertThat(catchUp.align(List.of(catalog("c1", "Alpha")))).isEqualTo(1);

        assertThat(local.findById("c1")).isPresent();
        assertThat(catchUp.copiedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a row the leader does not have was deleted while this node was away")
    void removesWhatTheLeaderDoesNotHave() {
        local.save(catalog("c1", "Alpha"));

        assertThat(catchUp.align(List.of())).isEqualTo(1);

        assertThat(local.findAll()).isEmpty();
        assertThat(catchUp.removedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a row that differs is overwritten with the leader's")
    void takesTheLeadersVersion() {
        local.save(catalog("c1", "Alpha"));
        Catalog published = catalog("c1", "Alpha");
        published.setSearchVersion(3);

        catchUp.align(List.of(published));

        assertThat(local.findById("c1").orElseThrow().getSearchVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("a table that agrees is left alone, round after round")
    void quietWhenItAgrees() {
        Catalog mine = local.save(catalog("c1", "Alpha"));
        int writesBefore = local.writes();

        assertThat(catchUp.align(List.of(mine))).isZero();
        assertThat(catchUp.align(List.of(mine))).isZero();

        assertThat(local.writes()).isEqualTo(writesBefore);
    }

    @Test
    @DisplayName("removals come first: the row on its way out is holding the name")
    void removesBeforeCopying() {
        // what a node that missed a delete looks like: the old row, under the name the
        // replacement wants. Copying first walks into the unique index that caused all this
        local.save(catalog("old-id", "Rust Blog"));

        long changed = catchUp.align(List.of(catalog("new-id", "Rust Blog")));

        assertThat(changed).isEqualTo(2);
        assertThat(local.rejections()).isZero();
        assertThat(local.findById("old-id")).isEmpty();
        assertThat(local.findByName("Rust Blog").orElseThrow().getId()).isEqualTo("new-id");
    }

    @Test
    @DisplayName("several at once, in both directions")
    void handlesAMixture() {
        local.save(catalog("keep", "Keep"));
        local.save(catalog("gone", "Gone"));

        long changed = catchUp.align(List.of(catalog("keep", "Keep"), catalog("new", "New")));

        assertThat(changed).isEqualTo(2);
        assertThat(local.findAll()).extracting(Catalog::getId).containsExactlyInAnyOrder("keep",
                "new");
    }

    @Test
    @DisplayName("nothing on either side is nothing to do")
    void nothingToDo() {
        assertThat(catchUp.align(List.of())).isZero();
        assertThat(catchUp.roundCount()).isZero();
    }

    // ---- against a real node ------------------------------------------------------------------

    @Test
    @DisplayName("the leader has nobody to catch up with, and alone there is nobody to ask")
    void asksNobodyWhenThereIsNobody() throws Exception {
        try (TestCluster cluster = TestCluster.start(1)) {
            CatalogCatchUp running = new CatalogCatchUp(cluster.node(0).cluster(), null, local,
                    Long.MAX_VALUE);
            running.afterPropertiesSet();
            try {
                local.save(catalog("c1", "Alpha"));

                // this node holds the cluster port, so it is the one that performs the writes
                assertThat(running.catchUp()).isZero();
                assertThat(running.roundCount()).isZero();

                // and the membership callbacks are safe to fire whatever the shape of the cluster
                running.onNodeJoined(cluster.node(0).cluster().self());
                running.onClusterJoined(cluster.node(0).cluster().self(), true);
                running.onLeaderChanged(null, cluster.node(0).cluster().self(), true);

                assertThat(local.findById("c1")).isPresent();
            } finally {
                running.destroy();
            }
        }
    }

}
