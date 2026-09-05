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

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The frame several writes travel in.
 *
 * <p>
 * Length fields come off the network, so the decoder is given deliberately broken input as well as
 * good: a frame that claims a billion entries must not be believed.
 * 
 * @Description: ReplicationBatchTest
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
class ReplicationBatchTest {

    @Test
    void roundTripsEveryField() {
        ReplicationBatch batch = new ReplicationBatch(List.of(
                ReplicationBatch.Entry.of((byte) 1, "catalog-a", "https://example.com/a"),
                ReplicationBatch.Entry.of((byte) 20, "image/jpeg", "books/v0/images/a.jpg",
                        new byte[] {1, 2, 3, 4})));

        ReplicationBatch decoded = ReplicationBatch.decode(batch.encode());

        assertThat(decoded).isNotNull();
        assertThat(decoded.entries()).hasSize(2);
        assertThat(decoded.entries().get(0).op()).isEqualTo((byte) 1);
        assertThat(decoded.entries().get(0).scope()).isEqualTo("catalog-a");
        assertThat(decoded.entries().get(0).key()).isEqualTo("https://example.com/a");
        assertThat(decoded.entries().get(0).value()).isEmpty();
        assertThat(decoded.entries().get(1).value()).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("bytes travel as bytes: an image through json would be a third larger")
    void carriesBinaryUntouched() {
        byte[] bytes = new byte[512];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i % 256);
        }
        ReplicationBatch batch = new ReplicationBatch(
                List.of(ReplicationBatch.Entry.of((byte) 20, "", "p", bytes)));

        assertThat(ReplicationBatch.decode(batch.encode()).entries().get(0).value())
                .isEqualTo(bytes);
        // the frame is the payload plus a small header, not a base64 inflation of it
        assertThat(batch.encode().length).isLessThan(bytes.length + 64);
    }

    @Test
    void readsTextValuesBack() {
        ReplicationBatch.Entry entry = ReplicationBatch.Entry.of((byte) 2, "cat", "hash",
                "some page text".getBytes(StandardCharsets.UTF_8));
        assertThat(entry.valueAsText()).isEqualTo("some page text");
    }

    @Test
    void aNullValueBecomesAnEmptyOne() {
        assertThat(ReplicationBatch.Entry.of((byte) 1, "s", "k", null).value()).isEmpty();
    }

    @Test
    @DisplayName("a malformed frame is discarded, never trusted enough to allocate from")
    void rejectsRubbish() {
        assertThat(ReplicationBatch.decode(new byte[] {1, 2, 3})).isNull();
        assertThat(ReplicationBatch.decode(new byte[0])).isNull();
        ReplicationBatch batch = new ReplicationBatch(
                List.of(ReplicationBatch.Entry.of((byte) 1, "s", "k")));
        byte[] encoded = batch.encode();
        // a count of two with only one entry present
        encoded[3] = 2;
        assertThat(ReplicationBatch.decode(encoded)).isNull();
    }

    @Test
    void anEmptyBatchSurvivesTheRoundTrip() {
        assertThat(ReplicationBatch.decode(new ReplicationBatch(List.of()).encode()).entries())
                .isEmpty();
    }

    @Test
    @DisplayName("sizeOf counts every part, so a batch stops filling before a frame overflows")
    void sizeCountsScopeKeyAndValue() {
        assertThat(ReplicationBatch
                .sizeOf(ReplicationBatch.Entry.of((byte) 1, "scope", "key", new byte[100])))
                        .isGreaterThan(100);
    }

}
