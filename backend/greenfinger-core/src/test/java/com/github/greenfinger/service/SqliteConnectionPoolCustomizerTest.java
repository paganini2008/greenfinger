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

package com.github.greenfinger.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 
 * @Description: SqliteConnectionPoolCustomizerTest
 * @Author: Fred Feng
 * @Date: 01/09/2026
 * @Version 2.0.0
 */
class SqliteConnectionPoolCustomizerTest {

    private final SqliteConnectionPoolCustomizer customizer = new SqliteConnectionPoolCustomizer();

    @Test
    void addsBothPragmasToAPlainUrl() {
        assertThat(SqliteConnectionPoolCustomizer.withPragmas("jdbc:sqlite:./greenfinger.db"))
                .isEqualTo("jdbc:sqlite:./greenfinger.db?journal_mode=WAL&busy_timeout=30000");
    }

    @Test
    @DisplayName("a url that already carries settings is extended, not rewritten")
    void keepsAnExistingQueryString() {
        String url = SqliteConnectionPoolCustomizer
                .withPragmas("jdbc:sqlite:./greenfinger.db?foreign_keys=true");

        assertThat(url).startsWith("jdbc:sqlite:./greenfinger.db?foreign_keys=true")
                .contains("journal_mode=WAL").contains("busy_timeout=30000");
    }

    @Test
    @DisplayName("what the user set is a decision, not an oversight")
    void leavesPragmasTheUserChose() {
        String url = SqliteConnectionPoolCustomizer.withPragmas(
                "jdbc:sqlite:./greenfinger.db?journal_mode=DELETE&busy_timeout=1000");

        assertThat(url).isEqualTo("jdbc:sqlite:./greenfinger.db?journal_mode=DELETE&busy_timeout=1000");
    }

    @Test
    void appliesToASqliteDataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:sqlite:./greenfinger.db");

        customizer.postProcessBeforeInitialization(dataSource, "dataSource");

        assertThat(dataSource.getJdbcUrl()).contains("journal_mode=WAL");
    }

    @Test
    @DisplayName("the other three databases are left exactly as configured")
    void leavesEveryOtherDatabaseAlone() {
        for (String url : new String[] {"jdbc:h2:file:./greenfinger;AUTO_SERVER=TRUE",
                "jdbc:postgresql://localhost:5432/greenfinger",
                "jdbc:mysql://localhost:3306/greenfinger"}) {
            HikariDataSource dataSource = new HikariDataSource();
            dataSource.setJdbcUrl(url);
            dataSource.setMaximumPoolSize(20);

            customizer.postProcessBeforeInitialization(dataSource, "dataSource");

            assertThat(dataSource.getJdbcUrl()).as(url).isEqualTo(url);
            assertThat(dataSource.getMaximumPoolSize()).as(url).isEqualTo(20);
        }
    }

    @Test
    void ignoresBeansThatAreNotDataSources() {
        Object bean = new Object();
        assertThat(customizer.postProcessBeforeInitialization(bean, "whatever")).isSameAs(bean);
    }

}
