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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which stores have to be copied between nodes.
 *
 * <p>
 * Getting this wrong is silent in both directions: replicating a shared database writes every row
 * twice, and failing to replicate a file-backed one leaves a node's search answering from data it
 * never received. Neither shows up as an error.
 * 
 * @Description: StoreTypeTest
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
class StoreTypeTest {

    @Test
    @DisplayName("a file per process has to be copied")
    void fileBackedDatabasesAreReplicated() {
        assertThat(StoreType.ofJdbcUrl("jdbc:sqlite:./data/.state/greenfinger.db"))
                .isEqualTo(StoreType.SQLITE);
        assertThat(StoreType.ofJdbcUrl("jdbc:h2:./data/.state/greenfinger")).isEqualTo(StoreType.H2);
        assertThat(StoreType.SQLITE.replicated()).isTrue();
        assertThat(StoreType.H2.replicated()).isTrue();
        assertThat(StoreType.ROCKSDB.replicated()).isTrue();
    }

    @Test
    @DisplayName("a server every node dials must not be")
    void sharedDatabasesAreNotReplicated() {
        assertThat(StoreType.ofJdbcUrl("jdbc:mysql://localhost:3306/greenfinger"))
                .isEqualTo(StoreType.MYSQL);
        assertThat(StoreType.ofJdbcUrl("jdbc:mariadb://localhost:3306/greenfinger"))
                .isEqualTo(StoreType.MYSQL);
        assertThat(StoreType.ofJdbcUrl("jdbc:postgresql://localhost:5432/greenfinger"))
                .isEqualTo(StoreType.POSTGRESQL);
        assertThat(StoreType.ofJdbcUrl("jdbc:sqlserver://localhost:1433;databaseName=demo"))
                .isEqualTo(StoreType.SQLSERVER);
        assertThat(StoreType.ofJdbcUrl("jdbc:oracle:thin:@//localhost:1521/demo"))
                .isEqualTo(StoreType.ORACLE);
        assertThat(StoreType.ofJdbcUrl("jdbc:oracle:oci:@demo")).isEqualTo(StoreType.ORACLE);
        assertThat(StoreType.MYSQL.shared()).isTrue();
        assertThat(StoreType.POSTGRESQL.shared()).isTrue();
        assertThat(StoreType.SQLSERVER.shared()).isTrue();
        assertThat(StoreType.ORACLE.shared()).isTrue();
    }

    @Test
    @DisplayName("h2 is both, and the url is the only thing that says which")
    void h2DependsOnTheUrlRatherThanTheProduct() {
        assertThat(StoreType.ofJdbcUrl("jdbc:h2:mem:testdb").replicated()).isTrue();
        assertThat(StoreType.ofJdbcUrl("jdbc:h2:file:./data/db").replicated()).isTrue();
        assertThat(StoreType.ofJdbcUrl("jdbc:h2:tcp://localhost:9092/~/greenfinger"))
                .isEqualTo(StoreType.H2_SERVER);
        assertThat(StoreType.ofJdbcUrl("jdbc:h2:ssl://localhost:9092/~/greenfinger").replicated())
                .isFalse();
    }

    @Test
    @DisplayName("an unknown driver is assumed shared, because the other guess writes rows twice")
    void anythingElseIsShared() {
        assertThat(StoreType.ofJdbcUrl("jdbc:db2://host:50000/sample"))
                .isEqualTo(StoreType.OTHER);
        assertThat(StoreType.ofJdbcUrl(null)).isEqualTo(StoreType.OTHER);
        assertThat(StoreType.ofJdbcUrl("")).isEqualTo(StoreType.OTHER);
        assertThat(StoreType.OTHER.replicated()).isFalse();
    }

    @Test
    void theBlobStoreSplitsTheSameWay() {
        assertThat(StoreType.ofBlobStore("local")).isEqualTo(StoreType.LOCAL_FILE);
        assertThat(StoreType.ofBlobStore("minio")).isEqualTo(StoreType.MINIO);
        assertThat(StoreType.ofBlobStore(null)).isEqualTo(StoreType.LOCAL_FILE);
        assertThat(StoreType.LOCAL_FILE.replicated()).isTrue();
        assertThat(StoreType.MINIO.shared()).isTrue();
    }

    @Test
    @DisplayName("the url is read case insensitively and with whitespace trimmed")
    void urlsAreReadLeniently() {
        assertThat(StoreType.ofJdbcUrl("  JDBC:SQLite:./x.db  ")).isEqualTo(StoreType.SQLITE);
    }

    @Test
    void toStringSaysWhichSideItIsOn() {
        assertThat(StoreType.SQLITE.toString()).contains("replicated");
        assertThat(StoreType.MINIO.toString()).contains("shared");
    }

}
