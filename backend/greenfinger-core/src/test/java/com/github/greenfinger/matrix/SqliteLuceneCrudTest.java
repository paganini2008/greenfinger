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

package com.github.greenfinger.matrix;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * The same, on SQLite.
 *
 * <p>
 * A real file rather than {@code :memory:}, because a file per process is what SQLite is
 * classified as here, and because an in-memory SQLite gives every connection its own empty
 * database -- a pool of them would look exactly like the divergence this project's replication
 * exists to repair.
 * 
 * @Description: SqliteLuceneCrudTest
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
@SpringBootTest(classes = MatrixTestApplication.class)
@TestPropertySource(properties = {
        "greenfinger.output.file.directory=${java.io.tmpdir}/gf-mx-sqlite/data",
        "greenfinger.frontier-directory=${java.io.tmpdir}/gf-mx-sqlite/frontier",
        "greenfinger.dedup.url.directory=${java.io.tmpdir}/gf-mx-sqlite/url",
        "greenfinger.dedup.content.directory=${java.io.tmpdir}/gf-mx-sqlite/content",
        "greenfinger.output.index.lucene.directory=${java.io.tmpdir}/gf-mx-sqlite/lucene",
        "greenfinger.output.vector.lucene.directory=${java.io.tmpdir}/gf-mx-sqlite/vector",
        "greenfinger.output.index.provider=lucene",
        "greenfinger.output.vector.store=lucene",
        "greenfinger.completion-check-interval=200ms",
        "greenfinger.idle-timeout=600ms",
        "spring.datasource.url=jdbc:sqlite:${java.io.tmpdir}/gf-mx-sqlite.db",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
class SqliteLuceneCrudTest extends LocalStoresCrudMatrixTest {

}
