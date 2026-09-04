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

package com.github.greenfinger.output.index;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.output.OutputFixtures;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.StubServer;

/**
 * 
 * @Description: ElasticsearchIndexAdminTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
class ElasticsearchIndexAdminTest {

    private StubServer server;
    private OutputProperties.Index config;

    @BeforeEach
    void setUp() throws Exception {
        server = new StubServer();
        config = new OutputProperties.Index();
        config.setUris(server.url());
        config.setPrefix("greenfinger");
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private ElasticsearchIndexAdmin admin() {
        return new ElasticsearchIndexAdmin(config);
    }

    @Test
    void reportsWhetherTheIndexIsThere() {
        server.on("GET", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa", 404, "");
        assertThat(admin().indexExists(OutputFixtures.CATALOG_ID)).isFalse();

        server.on("GET", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa", 200, "");
        assertThat(admin().indexExists(OutputFixtures.CATALOG_ID)).isTrue();
    }

    @Test
    void countsOneVersion() {
        server.on("GET", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa", 200, "");
        server.on("POST", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa/_count", 200, "{\"count\":17}");

        assertThat(admin().countByCatalogVersion(OutputFixtures.CATALOG_ID + ":3")).isEqualTo(17L);
        assertThat(server.requestsFor("POST", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa/_count").get(0).body())
                .contains("catalogVersion").contains(OutputFixtures.CATALOG_ID + ":3");
    }

    @Test
    @DisplayName("one index holds every version, so a version goes by query rather than by drop")
    void deletesOneVersionByQuery() {
        server.on("GET", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa", 200, "");
        server.on("POST", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa/_delete_by_query", 200, "{\"deleted\":9}");

        assertThat(admin().deleteByCatalogVersion(OutputFixtures.CATALOG_ID + ":3")).isEqualTo(9L);
        var request = server.requestsFor("POST", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa/_delete_by_query").get(0);
        assertThat(request.body())
                .contains("\"catalogVersion\":\"" + OutputFixtures.CATALOG_ID + ":3\"");
        assertThat(request.query()).contains("conflicts=proceed");
    }

    @Test
    @DisplayName("a whole catalog is its index, so it is dropped rather than emptied")
    void deletingAWholeCatalogDropsTheIndex() {
        server.on("GET", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa", 200, "");
        server.on("POST", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa/_count", 200, "{\"count\":40}");
        server.on("DELETE", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa", 200, "{\"acknowledged\":true}");

        assertThat(admin().deleteByCatalog(OutputFixtures.CATALOG_ID)).isEqualTo(40L);
        assertThat(server.requestsFor("DELETE", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa")).hasSize(1);
    }

    @Test
    void doesNothingWhenThereIsNoIndexYet() {
        server.on("GET", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa", 404, "");
        assertThat(admin().countByCatalogVersion(OutputFixtures.CATALOG_ID + ":0")).isZero();
        assertThat(admin().deleteByCatalogVersion(OutputFixtures.CATALOG_ID + ":0")).isZero();
        assertThat(admin().deleteByCatalog(OutputFixtures.CATALOG_ID)).isZero();
    }

    @Test
    @DisplayName("reclaiming the space is opt-in: on a large index the merge is expensive")
    void forceMergeIsOffByDefault() {
        server.on("GET", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa", 200, "");
        server.on("POST", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa/_delete_by_query", 200, "{\"deleted\":1}");
        server.on("POST", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa/_forcemerge", 200, "{}");

        admin().deleteByCatalogVersion(OutputFixtures.CATALOG_ID + ":0");
        assertThat(server.requestsFor("POST", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa/_forcemerge")).isEmpty();

        config.setForcemergeAfterDelete(true);
        admin().deleteByCatalogVersion(OutputFixtures.CATALOG_ID + ":0");
        assertThat(server.requestsFor("POST", "/greenfinger-0192f0c8-1234-7000-8000-0000000000aa/_forcemerge")).hasSize(1);
    }

    @Test
    @DisplayName("only the indices the prefix owns, since a shared cluster holds other people's")
    void listsTheIndicesUnderThePrefix() {
        server.on("GET", "/_cat/indices/greenfinger-*",
                200, "[{\"index\":\"greenfinger-b\"},{\"index\":\"greenfinger-a\"}]");

        assertThat(admin().listIndices()).containsExactly("greenfinger-a", "greenfinger-b");
    }

    @Test
    @DisplayName("a refresh is asked for after something changed, and that is not always one catalog")
    void refreshesEveryIndexUnderThePrefix() {
        server.on("POST", "/greenfinger-*/_refresh", 200, "{}");
        admin().refresh();
        assertThat(server.requestsFor("POST", "/greenfinger-*/_refresh")).hasSize(1);
    }

    @Test
    @DisplayName("the index is named from the catalog's id, which a rename cannot orphan")
    void namesTheIndexAfterTheCatalogId() {
        assertThat(admin().getName()).isEqualTo("elasticsearch");
        assertThat(admin().getIndexPrefix()).isEqualTo("greenfinger");
        assertThat(admin().indexOf(OutputFixtures.CATALOG_ID))
                .isEqualTo("greenfinger-" + OutputFixtures.CATALOG_ID);
        assertThat(admin().getLocation()).isEqualTo(server.url());
    }

}
