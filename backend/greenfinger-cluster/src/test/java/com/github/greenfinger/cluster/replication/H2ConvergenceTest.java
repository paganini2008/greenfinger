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
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.test.context.TestPropertySource;

/**
 * The same convergence, against H2 -- the zero-install default, and a file per process.
 * 
 * @Description: H2ConvergenceTest
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
@DataJpaTest
@EntityScan(basePackages = "com.github.greenfinger.core.model")
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
class H2ConvergenceTest extends FileDatabaseConvergenceTest {

}
