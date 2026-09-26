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

package com.github.greenfinger.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import com.github.greenfinger.api.security.TokenStore;
import com.github.greenfinger.service.CatalogAdminService;

/**
 * A node with no api on it.
 *
 * <p>
 * What an installation with no front end runs: it crawls, it gossips, it answers
 * {@code /actuator/health}, and it is driven from the prompt over the cluster. Nothing else is on
 * the port.
 *
 * @Description: HeadlessNodeTest
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
@SpringBootTest(classes = WebTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(
        properties = {"greenfinger.output.file.directory=${java.io.tmpdir}/gf-headless/data",
                "greenfinger.frontier-directory=${java.io.tmpdir}/gf-headless/frontier",
                "greenfinger.dedup.url.directory=${java.io.tmpdir}/gf-headless/url",
                "greenfinger.dedup.content.directory=${java.io.tmpdir}/gf-headless/content",
                "spring.datasource.url=jdbc:h2:mem:greenfinger-headless;DB_CLOSE_DELAY=-1",
                "greenfinger.api.web.enabled=false"})
class HeadlessNodeTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("with the api off, no endpoint is registered -- and the node still starts")
    void noEndpointsAtAll() {
        // starting at all is half the assertion: the controllers are component-scanned and their
        // helpers are not, so a switch that only turned off the configuration left a controller
        // asking for a bean that had just been withdrawn
        assertThat(context.getBeansWithAnnotation(ApiEndpoint.class)).isEmpty();
        assertThat(context.getBeanNamesForType(SinglePageAppConfiguration.class)).isEmpty();
    }

    @Test
    @DisplayName("what the node is for is still there: the crawler, and the door on the port")
    void theCrawlerAndTheDoorRemain() {
        assertThat(context.getBean(CatalogAdminService.class)).isNotNull();
        // security is not part of the switch: the http port exists either way, and what is left on
        // it -- health, and the actuator -- is not for anybody who can reach it
        assertThat(context.getBean(TokenStore.class)).isNotNull();
    }

}
