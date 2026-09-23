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
 * H2 + Lucene index + Lucene vectors: what a fresh clone runs with nothing installed.
 * 
 * @Description: H2LuceneCrudTest
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
@SpringBootTest(classes = MatrixTestApplication.class)
@TestPropertySource(properties = {
        "greenfinger.output.file.directory=${java.io.tmpdir}/gf-mx-h2/data",
        "greenfinger.frontier-directory=${java.io.tmpdir}/gf-mx-h2/frontier",
        "greenfinger.dedup.url.directory=${java.io.tmpdir}/gf-mx-h2/url",
        "greenfinger.dedup.content.directory=${java.io.tmpdir}/gf-mx-h2/content",
        "greenfinger.output.index.lucene.directory=${java.io.tmpdir}/gf-mx-h2/lucene",
        "greenfinger.output.vector.lucene.directory=${java.io.tmpdir}/gf-mx-h2/vector",
        "greenfinger.output.index.provider=lucene",
        "greenfinger.output.vector.store=lucene",
        "greenfinger.completion-check-interval=200ms",
        "greenfinger.idle-timeout=600ms",
        "spring.datasource.url=jdbc:h2:mem:greenfinger-matrix-h2;DB_CLOSE_DELAY=-1"})
class H2LuceneCrudTest extends LocalStoresCrudMatrixTest {

}
