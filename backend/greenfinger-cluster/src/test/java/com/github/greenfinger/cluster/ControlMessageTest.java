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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.cluster.channel.ControlMessage;

/**
 * The three things the cluster says to itself about a crawl.
 * 
 * @Description: ControlMessageTest
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
class ControlMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void startedCarriesWhatAJoiningNodeNeeds() {
        ControlMessage message = ControlMessage.started("cat-1", "update", 3, true);

        assertThat(message.type()).isEqualTo(ControlMessage.Type.STARTED);
        assertThat(message.action()).isEqualTo("update");
        assertThat(message.version()).isEqualTo(3);
        assertThat(message.refresh()).isTrue();
    }

    @Test
    @DisplayName("restoreFiles carries the node that asked, so it does not repair itself twice")
    void restoreFilesCarriesTheOrigin() {
        ControlMessage message = ControlMessage.restoreFiles("cat-1", 3, "node-a");
        assertThat(message.type()).isEqualTo(ControlMessage.Type.RESTORE_FILES);
        assertThat(message.version()).isEqualTo(3);
        assertThat(message.reason()).isEqualTo("node-a");
    }

    @Test
    @DisplayName("round trips as json, because that is what goes on the wire")
    void roundTrips() throws Exception {
        ControlMessage message = ControlMessage.started("cat-1", "crawl", 0, false);
        assertThat(objectMapper.readValue(objectMapper.writeValueAsString(message),
                ControlMessage.class)).isEqualTo(message);
    }

    @Test
    @DisplayName("an unknown field is ignored, so a newer node can add one without breaking an older")
    void toleratesUnknownFields() throws Exception {
        ControlMessage decoded = objectMapper.readValue(
                "{\"type\":\"STARTED\",\"catalogId\":\"c\",\"reason\":\"r\",\"somethingNew\":1}",
                ControlMessage.class);
        assertThat(decoded.type()).isEqualTo(ControlMessage.Type.STARTED);
        assertThat(decoded.reason()).isEqualTo("r");
    }

    @Test
    @DisplayName("a file restore carries the node that asked, so it does not do its own twice")
    void restoreFilesCarriesItsOrigin() {
        ControlMessage message = ControlMessage.restoreFiles("cat-1", 3, "node-a");

        assertThat(message.type()).isEqualTo(ControlMessage.Type.RESTORE_FILES);
        assertThat(message.catalogId()).isEqualTo("cat-1");
        // the version matters here in a way it does not for the others: a restore is asked for a
        // particular version, not for whatever is currently being crawled
        assertThat(message.version()).isEqualTo(3);
        assertThat(message.reason()).isEqualTo("node-a");
    }

}
