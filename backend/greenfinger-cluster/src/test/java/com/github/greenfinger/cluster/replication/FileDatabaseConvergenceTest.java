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

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.cluster.leader.CatalogCatchUp;
import com.github.greenfinger.cluster.support.CapturingSink;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.ContentMode;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.record.CatalogRepository;
import com.github.greenfinger.record.JpaCatalogStore;

/**
 * One node's real catalog table, told things by another node, against a database that is a file.
 *
 * <p>
 * Subclassed once per {@link com.github.greenfinger.cluster.StoreType} that is replicated: H2 and
 * SQLite. Both give every process its own copy, both therefore have to be kept in agreement by
 * this module, and the two differ enough in how they report a constraint violation that testing
 * one and assuming the other is how this was got wrong the first time.
 *
 * <h2>Not inside the test's transaction</h2>
 * {@code @DataJpaTest} rolls its transaction back, and a store call that joins it does not commit
 * -- which would defer the unique index violation to the end of the test rather than raising it
 * inside the apply, and the whole point here is what happens when a row is refused. Each call
 * commits on its own and the table is emptied between tests instead.
 *
 * @Description: FileDatabaseConvergenceTest
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
abstract class FileDatabaseConvergenceTest {

    @Autowired
    private CatalogRepository catalogRepository;

    private CatalogStore store;
    private CapturingSink sink;

    @BeforeEach
    void setUp() {
        store = new JpaCatalogStore(catalogRepository);
        sink = new CapturingSink();
        catalogRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        catalogRepository.deleteAll();
    }

    private Catalog catalog(String id, String name) {
        Catalog catalog = new Catalog();
        catalog.setId(id);
        catalog.setName(name);
        catalog.setUrl("https://" + name.toLowerCase() + ".example");
        catalog.setCat("tech");
        catalog.setPathPattern("**");
        catalog.setOutputTypes(Set.of(OutputType.FILE));
        catalog.setContentMode(ContentMode.TEXT);
        return catalog;
    }

    private ReplicationBatch.Entry rowFor(Catalog catalog) throws Exception {
        return ReplicationBatch.Entry.of(ReplicatedCatalogStore.OP_CATALOG, catalog.getId(),
                catalog.getId(),
                new ObjectMapper().writeValueAsBytes(catalog));
    }

    // ---- the ordinary path ----------------------------------------------------------------------

    @Test
    @DisplayName("a row from another node is written here")
    void appliesANewRow() throws Exception {
        ReplicatedCatalogStore.apply(rowFor(catalog("c1", "Alpha")), store);

        assertThat(store.findById("c1")).isPresent();
    }

    @Test
    @DisplayName("the same row twice is written once: delivery is at least once")
    void isIdempotent() throws Exception {
        // As it arrives in production: what travels is a row that has already been through a
        // store, so its defaults are filled in. A raw object with nulls where the defaults go
        // differs from what any store would hold and would be written every time it arrived
        Catalog incoming = store.save(catalog("c1", "Alpha"));
        catalogRepository.deleteAll();

        ReplicatedCatalogStore.apply(rowFor(incoming), store);
        Catalog first = store.findById("c1").orElseThrow();
        ReplicatedCatalogStore.apply(rowFor(incoming), store);
        Catalog second = store.findById("c1").orElseThrow();

        // the write stamp is what would change if the second one had been written
        assertThat(second.getUpdatedAt()).isEqualTo(first.getUpdatedAt());
    }

    @Test
    @DisplayName("a published search version arriving from elsewhere is taken")
    void appliesAPublishedVersion() throws Exception {
        ReplicatedCatalogStore.apply(rowFor(catalog("c1", "Alpha")), store);
        Catalog published = catalog("c1", "Alpha");
        published.setIndexVersion(1);
        published.setSearchVersion(1);

        ReplicatedCatalogStore.apply(rowFor(published), store);

        assertThat(store.findById("c1").orElseThrow().getSearchVersion()).isEqualTo(1);
    }

    // ---- the failure this whole mechanism exists for ---------------------------------------------

    @Test
    @DisplayName("a stale row under the same name refuses the new catalog, and says nothing")
    void aStaleRowRefusesTheNewCatalog() throws Exception {
        // On H2 this is uk_catalog_name. On SQLite the dialect creates no unique constraint at
        // all, so the store enforces it -- the point being that the two behave the same, because
        // a clash one node refuses and another accepts is divergence by construction
        // what node 3 was holding: a catalog that had been deleted everywhere else
        store.save(catalog("old-id", "Rust Blog"));

        // the replacement, created elsewhere under a new id and the same name
        ReplicatedCatalogStore.apply(rowFor(catalog("new-id", "Rust Blog")), store);

        // refused by uk_catalog_name, logged, and dropped -- this is the defect, reproduced
        assertThat(store.findById("new-id")).isEmpty();
        assertThat(store.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("catching up removes the stale row, and the row that was refused then lands")
    void catchingUpClearsTheWayForIt() throws Exception {
        store.save(catalog("old-id", "Rust Blog"));
        ReplicatedCatalogStore.apply(rowFor(catalog("new-id", "Rust Blog")), store);
        assertThat(store.findById("new-id")).isEmpty();

        // what the leader holds: the replacement, and no sign of the row it replaced
        catchUp().align(List.of(catalog("new-id", "Rust Blog")));

        assertThat(store.findById("old-id")).isEmpty();
        assertThat(store.findById("new-id")).isPresent();
        assertThat(store.findByName("Rust Blog").orElseThrow().getId()).isEqualTo("new-id");
    }

    @Test
    @DisplayName("a row the leader does not have was deleted while this node was not listening")
    void removesWhatTheLeaderDoesNotHave() {
        store.save(catalog("c1", "Alpha"));

        catchUp().align(List.of());

        assertThat(store.findAll()).isEmpty();
    }

    @Test
    @DisplayName("a row this node never received is taken from the leader")
    void takesWhatItIsMissing() {
        long changed = catchUp().align(List.of(catalog("c1", "Alpha")));

        assertThat(changed).isEqualTo(1);
        assertThat(store.findById("c1")).isPresent();
    }

    @Test
    @DisplayName("a table that already agrees is left alone, round after round")
    void quietWhenItAgrees() {
        Catalog mine = store.save(catalog("c1", "Alpha"));
        CatalogCatchUp catchUp = catchUp();

        assertThat(catchUp.align(List.of(mine))).isZero();
        assertThat(catchUp.align(List.of(mine))).isZero();
        assertThat(catchUp.align(List.of(mine))).isZero();
        assertThat(catchUp.copiedCount()).isZero();
        assertThat(catchUp.removedCount()).isZero();
    }

    @Test
    @DisplayName("a row that differs is overwritten with the leader's")
    void takesTheLeadersVersionOfARow() {
        store.save(catalog("c1", "Alpha"));
        Catalog published = catalog("c1", "Alpha");
        published.setSearchVersion(3);

        catchUp().align(List.of(published));

        assertThat(store.findById("c1").orElseThrow().getSearchVersion()).isEqualTo(3);
    }

    // ---- deletes -----------------------------------------------------------------------------

    @Test
    @DisplayName("a delete from the leader removes the row")
    void appliesADelete() throws Exception {
        store.save(catalog("c1", "Alpha"));

        ReplicatedCatalogStore.apply(
                ReplicationBatch.Entry.of(ReplicatedCatalogStore.OP_CATALOG_DELETE, "c1", "c1"),
                store);

        assertThat(store.findById("c1")).isEmpty();
    }

    @Test
    @DisplayName("a catalog made again under a deleted id simply exists again")
    void aCatalogCanBeMadeAgain() throws Exception {
        store.save(catalog("c1", "Alpha"));
        ReplicatedCatalogStore.apply(
                ReplicationBatch.Entry.of(ReplicatedCatalogStore.OP_CATALOG_DELETE, "c1", "c1"),
                store);

        ReplicatedCatalogStore.apply(rowFor(catalog("c1", "Alpha again")), store);

        assertThat(store.findById("c1")).isPresent();
    }

    // ---- every mutator is announced ---------------------------------------------------------

    @Test
    @DisplayName("every write through the decorator is told to the other nodes")
    void everyMutatorIsAnnounced() {
        ReplicatedCatalogStore replicated = new ReplicatedCatalogStore(store, sink);

        Catalog saved = replicated.save(catalog("c1", "Alpha"));
        replicated.incrementIndexVersion(saved.getId());
        replicated.publishSearchVersion(saved.getId(), 1);
        replicated.setRunningState(saved.getId(), "crawl");
        replicated.resetVersions(saved.getId());
        replicated.deleteById(saved.getId());

        assertThat(sink.ops()).containsExactly(ReplicatedCatalogStore.OP_CATALOG,
                ReplicatedCatalogStore.OP_CATALOG, ReplicatedCatalogStore.OP_CATALOG,
                ReplicatedCatalogStore.OP_CATALOG, ReplicatedCatalogStore.OP_CATALOG,
                ReplicatedCatalogStore.OP_CATALOG_DELETE);
    }

    @Test
    @DisplayName("deleting something that is not there announces nothing")
    void saysNothingAboutANonDelete() {
        ReplicatedCatalogStore replicated = new ReplicatedCatalogStore(store, sink);

        assertThat(replicated.deleteById("never-existed")).isFalse();
        assertThat(sink.entries()).isEmpty();
    }

    @Test
    @DisplayName("reads go straight to the table")
    void readsAreLocal() {
        ReplicatedCatalogStore replicated = new ReplicatedCatalogStore(store, sink);
        replicated.save(catalog("c1", "Alpha"));
        sink.clear();

        assertThat(replicated.getName()).isEqualTo("replicated:jpa");
        assertThat(replicated.findByName("Alpha")).isPresent();
        assertThat(replicated.findAll()).hasSize(1);
        assertThat(replicated.findAllCategories()).containsExactly("tech");
        assertThat(replicated.findRunning()).isEmpty();
        assertThat(sink.entries()).isEmpty();
    }

    /**
     * No cluster: what is under test is what catching up does to a real table, and fetching the
     * leader's copy is one request with its own test against real nodes.
     */
    private CatalogCatchUp catchUp() {
        return new CatalogCatchUp(null, null, store, Long.MAX_VALUE);
    }

}
