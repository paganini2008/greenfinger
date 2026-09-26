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

package com.github.greenfinger.cluster.remote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.cluster.leader.LeaderGateway;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.DeleteLayer;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.service.DeleteReport;
import com.github.greenfinger.service.ops.CatalogSnapshot;
import com.github.greenfinger.service.ops.DashboardSnapshot;
import com.github.greenfinger.service.ops.GreenfingerOperations;

/**
 * The terminal asking, and a crawler answering, with json in between.
 *
 * <p>
 * The gateway here does what the leader channel does -- write the request out, read it back as the
 * handler's argument, and the same for the answer -- so an operation whose shape does not survive
 * that fails here rather than on somebody's laptop with a cluster running.
 *
 * @Description: RemoteOperationsTest
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
class RemoteOperationsTest {

    private RecordingOperations crawler;
    private GreenfingerOperations terminal;

    @BeforeEach
    void wire() throws Exception {
        crawler = new RecordingOperations();
        LoopbackGateway gateway = new LoopbackGateway();
        new OperationsServer(gateway, crawler).afterPropertiesSet();
        terminal = new RemoteOperations(gateway);
    }

    @Test
    @DisplayName("Every operation the terminal can ask for is one the crawler registered")
    void everyOperationIsRegistered() {
        assertThat(terminal.overview().catalogs()).isEmpty();
        assertThat(terminal.catalog("abc").getId()).isEqualTo("abc");
        assertThat(terminal.categories()).containsExactly("news", "other");
        assertThat(terminal.form("abc").getName()).isEqualTo("books");
        assertThat(terminal.saveCatalog(new Catalog()).getId()).isEqualTo("saved");
        assertThat(terminal.ensureCatalog("books", "https://books.toscrape.com").getId())
                .isEqualTo("ensured");
        assertThat(terminal.deleteCatalog("abc")).isEqualTo("books");
        assertThat(terminal.versions("abc").catalogName()).isEqualTo("books");
        assertThat(terminal.report("abc", 2)).containsEntry("version", 2);
        assertThat(terminal.start(new GreenfingerOperations.StartAsk("crawl", "abc", null, 4)))
                .isEqualTo("crawl of 'books' started");
        assertThat(terminal.interrupt("abc")).isTrue();
        assertThat(terminal.live("abc", true).dashboard().getSavedResourceCount()).isEqualTo(7L);
        assertThat(terminal.delete(new GreenfingerOperations.DeleteAsk("abc", 1, null, false,
                false, EnumSet.of(DeleteLayer.DB), true, false))).hasSize(1);
        assertThat(terminal.replay(new GreenfingerOperations.ReplayAsk("abc", 1,
                Set.of(OutputType.INDEX))).replayed()).isEqualTo(12L);
        assertThat(terminal.search(new GreenfingerOperations.SearchAsk("books", null, 10, "words"))
                .hits()).hasSize(1);
        assertThat(terminal.indexInfo().about()).hasSize(1);
        assertThat(terminal.vectorInfo().counts()).hasSize(1);
    }

    @Test
    @DisplayName("What the terminal sends is what the crawler receives")
    void argumentsArriveIntact() {
        terminal.start(new GreenfingerOperations.StartAsk("merge", "abc", "https://x/y", 8));
        assertThat(crawler.started.verb()).isEqualTo("merge");
        assertThat(crawler.started.from()).isEqualTo("https://x/y");
        assertThat(crawler.started.threads()).isEqualTo(8);

        terminal.delete(new GreenfingerOperations.DeleteAsk("abc", null, 3, false, true,
                EnumSet.of(DeleteLayer.INDEX, DeleteLayer.VECTOR), false, true));
        assertThat(crawler.deleted.keepLatest()).isEqualTo(3);
        assertThat(crawler.deleted.purge()).isTrue();
        assertThat(crawler.deleted.force()).isTrue();
        assertThat(crawler.deleted.layers()).containsExactlyInAnyOrder(DeleteLayer.INDEX,
                DeleteLayer.VECTOR);
    }

    @Test
    @DisplayName("An operation nobody registered is refused rather than ignored")
    void unknownOperationsAreRefused() {
        LoopbackGateway gateway = new LoopbackGateway();
        assertThatThrownBy(() -> gateway.onLeader("ops.nothing", null, String.class))
                .hasMessageContaining("ops.nothing");
    }

    /**
     * The leader channel, without a cluster: the same serialise-there-and-back that the real one
     * performs, so a shape that cannot make the trip fails the same way.
     */
    private static final class LoopbackGateway implements LeaderGateway {

        private final ObjectMapper objectMapper = new ObjectMapper();
        private final Map<String, Function<String, Object>> handlers = new java.util.HashMap<>();

        @Override
        public <A, R> void handle(String operation, Class<A> argType, Function<A, R> handler) {
            handlers.put(operation, payload -> {
                try {
                    A argument = payload == null ? null : objectMapper.readValue(payload, argType);
                    return handler.apply(argument);
                } catch (Exception e) {
                    throw new IllegalStateException(operation + ": " + e.getMessage(), e);
                }
            });
        }

        @Override
        public <T> T onLeader(String operation, Object request, Class<T> type) {
            Function<String, Object> handler = handlers.get(operation);
            if (handler == null) {
                throw new IllegalStateException("Unknown operation '" + operation + "'");
            }
            try {
                Object answer = handler.apply(
                        request == null ? null : objectMapper.writeValueAsString(request));
                return answer == null ? null
                        : objectMapper.readValue(objectMapper.writeValueAsString(answer), type);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public boolean isLeader() {
            return true;
        }
    }

    /** A crawler that answers with something recognisable, and keeps what it was asked. */
    private static final class RecordingOperations implements GreenfingerOperations {

        private GreenfingerOperations.StartAsk started;
        private GreenfingerOperations.DeleteAsk deleted;

        @Override
        public Overview overview() {
            return new Overview(List.of(), List.of(), Map.of());
        }

        @Override
        public CatalogSnapshot catalog(String idOrName) {
            return snapshot(idOrName);
        }

        @Override
        public List<String> categories() {
            return List.of("news", "other");
        }

        @Override
        public Catalog form(String idOrName) {
            Catalog catalog = new Catalog();
            catalog.setName("books");
            catalog.setUrl("https://books.toscrape.com");
            return catalog;
        }

        @Override
        public CatalogSnapshot saveCatalog(Catalog form) {
            return snapshot("saved");
        }

        @Override
        public CatalogSnapshot ensureCatalog(String name, String url) {
            return snapshot("ensured");
        }

        @Override
        public String deleteCatalog(String idOrName) {
            return "books";
        }

        @Override
        public Versions versions(String idOrName) {
            return new Versions(idOrName, "books", List.of(Map.of("version", 0)));
        }

        @Override
        public Map<String, Object> report(String idOrName, Integer version) {
            return Map.of("version", version);
        }

        @Override
        public String start(StartAsk ask) {
            this.started = ask;
            return "crawl of 'books' started";
        }

        @Override
        public boolean interrupt(String idOrName) {
            return true;
        }

        @Override
        public Live live(String catalogId, boolean perNode) {
            DashboardSnapshot dashboard = new DashboardSnapshot();
            dashboard.setSavedResourceCount(7L);
            dashboard.setCatalogDetails(snapshot(catalogId));
            return new Live(dashboard, 3L, Map.of("node-1", Map.of("savedResourceCount", 7L)),
                    false);
        }

        @Override
        public List<DeleteReport.Line> delete(DeleteAsk ask) {
            this.deleted = ask;
            return List.of(new DeleteReport.Line(1, DeleteLayer.DB, 5L, 0L, null));
        }

        @Override
        public ReplayAnswer replay(ReplayAsk ask) {
            return new ReplayAnswer(12L, 1, new FileLines(3L, 2L, 1L, 0L, 0L));
        }

        @Override
        public SearchAnswer search(SearchAsk ask) {
            return new SearchAnswer("1 match", true, false, 1L,
                    new ArrayList<>(List.of(new Hit(0.5d, "A title", "https://x/y"))));
        }

        @Override
        public Info indexInfo() {
            return new Info(List.of(new InfoRow("Index", "lucene")), List.of(), List.of("gf-abc"));
        }

        @Override
        public Info vectorInfo() {
            return new Info(List.of(), List.of(new CountRow("abc", "books", 0, "text_384", 9L)),
                    List.of());
        }

        private CatalogSnapshot snapshot(String id) {
            CatalogSnapshot catalog = new CatalogSnapshot();
            catalog.setId(id);
            catalog.setName("books");
            catalog.setMaxFetchSize(100);
            catalog.setVersion(0);
            catalog.setSearchVersion(-1);
            catalog.setOutputTypes(Set.of(OutputType.FILE));
            return catalog;
        }
    }

}
