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

package com.github.greenfinger.api.actuate;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import com.github.greenfinger.api.actuate.SettingsEndpoint.Group;
import com.github.greenfinger.api.web.WebTestApplication;

/**
 * What the System page is shown, and what it must never be shown.
 *
 * @Description: SettingsEndpointTest
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
@SpringBootTest(classes = WebTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(
        properties = {"greenfinger.output.file.directory=${java.io.tmpdir}/gf-settings/data",
                "greenfinger.frontier-directory=${java.io.tmpdir}/gf-settings/frontier",
                "greenfinger.dedup.url.directory=${java.io.tmpdir}/gf-settings/url",
                "greenfinger.dedup.content.directory=${java.io.tmpdir}/gf-settings/content",
                "spring.datasource.url=jdbc:h2:mem:greenfinger-settings;DB_CLOSE_DELAY=-1",
                "greenfinger.security.token-secret=a-secret-nobody-should-read",
                "greenfinger.output.vector.qdrant.api-key=a-key-nobody-should-read",
                "greenfinger.output.file.minio.secret-key=another-one",
                "greenfinger.default-max-fetch-size=123"})
class SettingsEndpointTest {

    @Autowired
    private SettingsEndpoint endpoint;

    @Test
    @DisplayName("Every greenfinger group is there, and nobody else's")
    void oursAndOnlyOurs() {
        assertThat(endpoint.settings().groups()).extracting(Group::prefix).contains("greenfinger",
                "greenfinger.output", "greenfinger.embedding", "greenfinger.extractor",
                "greenfinger.security");
        assertThat(endpoint.settings().groups()).allSatisfy(group -> assertThat(group.prefix())
                .startsWith("greenfinger"));
    }

    @Test
    @DisplayName("The value in force, not the one in the yaml")
    void whatWonRatherThanWhatWasWritten() {
        assertThat(flat("greenfinger")).containsEntry("defaultMaxFetchSize", 123);
    }

    @Test
    @DisplayName("Nested settings are keyed the way the yaml keys them")
    void nestedKeysReadLikeTheYaml() {
        Map<String, Object> output = flat("greenfinger.output");
        assertThat(output).containsKey("file.minio.endpoint").containsKey("index.provider");
        // a Duration or a File is a value, not a group to walk into
        assertThat(output.keySet()).noneSatisfy(key -> assertThat(key).contains(".class"));
    }

    @Test
    @DisplayName("Secrets are masked; settings that merely sound like one are not")
    void secretsAreMasked() {
        assertThat(flat("greenfinger.security")).containsEntry("tokenSecret",
                SettingsEndpoint.MASK);
        assertThat(flat("greenfinger.output")).containsEntry("vector.qdrant.apiKey",
                SettingsEndpoint.MASK).containsEntry("file.minio.secretKey", SettingsEndpoint.MASK);
        // tokenValidity and maxTextTokens are readable settings, and a word-contains rule would
        // have hidden both
        assertThat(flat("greenfinger.security").get("tokenValidity")).isNotEqualTo(
                SettingsEndpoint.MASK);
        assertThat(flat("greenfinger.embedding").get("openai.maxTextTokens"))
                .isNotEqualTo(SettingsEndpoint.MASK);
    }

    @Test
    @DisplayName("A setting nobody filled in reads as unset rather than as hidden")
    void unsetIsNotTheSameAsHidden() {
        assertThat(flat("greenfinger.output")).containsEntry("vector.weaviate.apiKey", null);
    }

    private Map<String, Object> flat(String prefix) {
        return endpoint.settings().groups().stream().filter(g -> prefix.equals(g.prefix()))
                .findFirst().orElseThrow().properties();
    }

}
