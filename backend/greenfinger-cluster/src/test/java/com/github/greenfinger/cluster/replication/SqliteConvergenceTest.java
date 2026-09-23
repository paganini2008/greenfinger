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

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.test.context.TestPropertySource;

/**
 * The same convergence, against SQLite.
 *
 * <p>
 * Not a formality. {@code StoreType} calls SQLite and H2 the same thing -- a file every process
 * has its own copy of -- and the whole of this package follows from that classification, so if the
 * classification is right then the behaviour has to hold on both. They report a unique index
 * violation differently, they stamp dates differently, and the one that is not the default is the
 * one nobody would notice breaking.
 *
 * <p>
 * The database is a real file in the temporary directory rather than {@code :memory:}, because
 * a file is what the classification is about, and because SQLite's in-memory mode gives each
 * connection its own empty database unless the url says otherwise -- which would make a pool of
 * them look exactly like the divergence this class is testing for.
 * 
 * @Description: SqliteConvergenceTest
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@EntityScan(basePackages = "com.github.greenfinger.core.model")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:${java.io.tmpdir}/greenfinger-convergence-test.db",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
class SqliteConvergenceTest extends FileDatabaseConvergenceTest {

}
