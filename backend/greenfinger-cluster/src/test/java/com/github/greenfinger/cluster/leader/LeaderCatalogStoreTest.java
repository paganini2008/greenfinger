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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.cluster.replication.ReplicatedCatalogStore;
import com.github.greenfinger.cluster.support.CapturingSink;
import com.github.greenfinger.cluster.support.FakeCatalogStore;
import com.github.greenfinger.core.model.Catalog;

/**
 * Which calls leave this node and which are answered where they stand.
 *
 * <p>
 * The gateway is a double that performs everything locally and writes down what it was asked for.
 * That is exactly what the real one does when this node is the leader, so the leader's path is
 * covered for real here; what the double stands in for is the network, which
 * {@code LeaderChannelTest} covers on its own.
 *
 * @Description: LeaderCatalogStoreTest
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
class LeaderCatalogStoreTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FakeCatalogStore local;
    private CapturingSink sink;
    private RecordingGateway gateway;
    private LeaderCatalogStore store;

    @BeforeEach
    void setUp() throws Exception {
        local = new FakeCatalogStore("here");
        sink = new CapturingSink();
        gateway = new RecordingGateway();
        store = new LeaderCatalogStore(local, new ReplicatedCatalogStore(local, sink), gateway);
        store.afterPropertiesSet();
    }

    private static Catalog catalog(String id, String name) {
        Catalog catalog = new Catalog();
        catalog.setId(id);
        catalog.setName(name);
        catalog.setUrl("https://" + name.toLowerCase() + ".example");
        catalog.setCat("tech");
        catalog.setIndexVersion(0);
        catalog.setSearchVersion(-1);
        return catalog;
    }

    // ---- writes leave ---------------------------------------------------------------------------

    @Test
    @DisplayName("every write goes to the leader, named")
    void everyWriteIsRouted() {
        Catalog saved = store.save(catalog("c1", "Alpha"));
        store.incrementIndexVersion(saved.getId());
        store.publishSearchVersion(saved.getId(), 1);
        store.setRunningState(saved.getId(), "crawl");
        store.resetVersions(saved.getId());
        store.deleteById(saved.getId());

        assertThat(gateway.asked).containsExactly(LeaderCatalogStore.SAVE,
                LeaderCatalogStore.INCREMENT_INDEX_VERSION,
                LeaderCatalogStore.PUBLISH_SEARCH_VERSION, LeaderCatalogStore.SET_RUNNING_STATE,
                LeaderCatalogStore.RESET_VERSIONS, LeaderCatalogStore.DELETE);
    }

    @Test
    @DisplayName("the leader performs it and tells the others, in that order")
    void theLeaderPerformsAndAnnounces() {
        Catalog saved = store.save(catalog("c1", "Alpha"));

        assertThat(local.findById("c1")).isPresent();
        assertThat(sink.ops()).containsExactly(ReplicatedCatalogStore.OP_CATALOG);
        assertThat(saved.getName()).isEqualTo("Alpha");
    }

    @Test
    @DisplayName("what the leader answers is what the caller gets back")
    void returnsWhatTheLeaderDid() {
        Catalog saved = store.save(catalog("c1", "Alpha"));

        assertThat(store.incrementIndexVersion(saved.getId())).isEqualTo(1);
        assertThat(store.deleteById(saved.getId())).isTrue();
        assertThat(store.deleteById("never-existed")).isFalse();
    }

    @Test
    @DisplayName("a version and a running state survive the trip")
    void argumentsSurviveTheTrip() {
        Catalog saved = store.save(catalog("c1", "Alpha"));

        store.publishSearchVersion(saved.getId(), 4);
        store.setRunningState(saved.getId(), "update");

        Catalog back = local.findById("c1").orElseThrow();
        assertThat(back.getSearchVersion()).isEqualTo(4);
        assertThat(back.getRunningState()).isEqualTo("update");
    }

    @Test
    @DisplayName("the whole table is what a node catching up asks for")
    void fromLeaderIsTheWholeTable() {
        store.save(catalog("c1", "Alpha"));
        store.save(catalog("c2", "Beta"));

        assertThat(store.fromLeader()).extracting(Catalog::getId).containsExactly("c1", "c2");
    }

    // ---- read your own write ---------------------------------------------------------------

    @Test
    @DisplayName("a follower can read back what it just asked the leader to write")
    void readsItsOwnWrite() throws Exception {
        FakeCatalogStore follower = new FakeCatalogStore("follower");
        LeaderCatalogStore store = new LeaderCatalogStore(follower,
                new ReplicatedCatalogStore(local, sink), new RecordingGateway(false));
        store.afterPropertiesSet();

        Catalog saved = store.save(catalog("c1", "Alpha"));

        // the broadcast has not been delivered; this node has it anyway
        assertThat(follower.findById("c1")).isPresent();
        assertThat(store.findById(saved.getId())).isPresent();
    }

    @Test
    @DisplayName("a version published through a follower is visible on it at once")
    void readsItsOwnVersionChange() throws Exception {
        FakeCatalogStore follower = new FakeCatalogStore("follower");
        LeaderCatalogStore store = new LeaderCatalogStore(follower,
                new ReplicatedCatalogStore(local, sink), new RecordingGateway(false));
        store.afterPropertiesSet();
        store.save(catalog("c1", "Alpha"));

        store.publishSearchVersion("c1", 2);

        assertThat(follower.findById("c1").orElseThrow().getSearchVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("a delete through a follower is gone from it at once")
    void forgetsItsOwnDelete() throws Exception {
        FakeCatalogStore follower = new FakeCatalogStore("follower");
        LeaderCatalogStore store = new LeaderCatalogStore(follower,
                new ReplicatedCatalogStore(local, sink), new RecordingGateway(false));
        store.afterPropertiesSet();
        store.save(catalog("c1", "Alpha"));

        assertThat(store.deleteById("c1")).isTrue();
        assertThat(follower.findById("c1")).isEmpty();
    }

    @Test
    @DisplayName("on the leader nothing is applied twice")
    void theLeaderDoesNotWriteTwice() {
        int writesBefore = local.writes();

        store.save(catalog("c1", "Alpha"));

        // one write, by the leader side; not a second one on the way back
        assertThat(local.writes()).isEqualTo(writesBefore + 1);
    }

    // ---- reads stay -----------------------------------------------------------------------------

    @Test
    @DisplayName("reads are answered here, without asking anybody")
    void readsAreLocal() {
        store.save(catalog("c1", "Alpha"));
        gateway.asked.clear();

        assertThat(store.getName()).isEqualTo("leader:here");
        assertThat(store.findById("c1")).isPresent();
        assertThat(store.findByName("alpha")).isPresent();
        assertThat(store.findAll()).hasSize(1);
        assertThat(store.findAllCategories()).containsExactly("tech");
        assertThat(store.findRunning()).isEmpty();

        assertThat(gateway.asked).isEmpty();
    }

    /** A gateway that performs everything here and writes down what it was asked for. */
    private static final class RecordingGateway implements LeaderGateway {

        private final Map<String, Handler> handlers = new LinkedHashMap<>();
        private final List<String> asked = new ArrayList<>();
        private final boolean leader;

        RecordingGateway() {
            this(true);
        }

        RecordingGateway(boolean leader) {
            this.leader = leader;
        }

        private record Handler(Class<?> argType, Function<Object, ?> action) {}

        @SuppressWarnings("unchecked")
        @Override
        public <A, R> void handle(String operation, Class<A> argType, Function<A, R> handler) {
            handlers.put(operation, new Handler(argType, (Function<Object, ?>) handler));
        }

        @Override
        public boolean isLeader() {
            return leader;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T onLeader(String operation, Object request, Class<T> type) {
            asked.add(operation);
            Handler handler = handlers.get(operation);
            try {
                // through json, because that is what the real one does and it is where a record
                // with the wrong shape would be found out
                Object argument = request == null ? null
                        : OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(request),
                                handler.argType());
                Object result = handler.action().apply(argument);
                if (result == null || Void.class.equals(type)) {
                    return null;
                }
                return (T) OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(result), type);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

}
