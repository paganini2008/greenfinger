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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsService;
import com.github.greenfinger.core.model.DeleteLayer;

/**
 * A delete, on its way to the leader.
 *
 * <p>
 * What is asserted is the instruction: which operation, which catalog, which versions, which
 * layers, and that the report survives the trip. Performing it is {@code DeletionService}'s own
 * test, and running it on every node is the cluster's.
 * 
 * @Description: LeaderDeletionServiceTest
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
class LeaderDeletionServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final List<String> asked = new ArrayList<>();
    private LeaderDeletionService service;

    @BeforeEach
    void setUp() {
        service = new LeaderDeletionService(null, null, null, null, null, null, null, null,
                new RecordingGateway(), mock(CatalogDetailsService.class));
    }

    /** Only an id is needed: the leader loads the details itself. */
    private static CatalogDetails details(String id) {
        CatalogDetails details = mock(CatalogDetails.class);
        when(details.getId()).thenReturn(id);
        return details;
    }

    @Test
    @DisplayName("named versions travel as themselves")
    void routesNamedVersions() {
        service.delete(details("c1"), List.of(0, 2),
                EnumSet.of(DeleteLayer.DB, DeleteLayer.INDEX), false, true);

        assertThat(asked).containsExactly(
                LeaderDeletionService.VERSIONS + " c1 versions=[0, 2] layers=index,db dry=false"
                        + " force=true");
    }

    @Test
    @DisplayName("emptying a catalog names no version")
    void routesAClean() {
        service.cleanCatalog(details("c1"), EnumSet.allOf(DeleteLayer.class), true, false);

        assertThat(asked).containsExactly(LeaderDeletionService.CLEAN
                + " c1 versions=null layers=vector,index,file,db dry=true force=false");
    }

    @Test
    @DisplayName("deleting a catalog is its own operation")
    void routesACatalogDelete() {
        service.deleteCatalog(details("c1"), EnumSet.of(DeleteLayer.FILE), false, false);

        assertThat(asked).containsExactly(LeaderDeletionService.CATALOG
                + " c1 versions=null layers=file dry=false force=false");
    }

    @Test
    @DisplayName("the report comes back as its lines")
    void bringsTheReportBack() {
        var report = service.delete(details("c1"), List.of(0),
                EnumSet.of(DeleteLayer.INDEX), false, false);

        assertThat(report.getLines()).hasSize(1);
        assertThat(report.getLines().get(0).count()).isEqualTo(7L);
        assertThat(report.getLines().get(0).layer()).isEqualTo(DeleteLayer.INDEX);
    }

    /** Writes down the request and answers with one line, through json as the real one does. */
    private final class RecordingGateway implements LeaderGateway {

        @Override
        public <A, R> void handle(String operation, Class<A> argType, Function<A, R> handler) {
            // the handlers are the leader's half; this test is the caller's
        }

        @Override
        public boolean isLeader() {
            return false;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T onLeader(String operation, Object request, Class<T> type) {
            try {
                LeaderDeletionService.Request asRequest = OBJECT_MAPPER.readValue(
                        OBJECT_MAPPER.writeValueAsString(request),
                        LeaderDeletionService.Request.class);
                asked.add(operation + " " + asRequest.catalogId() + " versions="
                        + asRequest.versions() + " layers=" + asRequest.layers() + " dry="
                        + asRequest.dryRun() + " force=" + asRequest.force());
                String answer = "[{\"version\":0,\"layer\":\"INDEX\",\"count\":7,\"bytes\":0,"
                        + "\"error\":null}]";
                return (T) OBJECT_MAPPER.readValue(answer, type);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

}
