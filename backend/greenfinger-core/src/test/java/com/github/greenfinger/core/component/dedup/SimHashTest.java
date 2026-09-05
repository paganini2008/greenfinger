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

package com.github.greenfinger.core.component.dedup;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 
 * @Description: SimHashTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class SimHashTest {

    private static final String ARTICLE =
            "Spring Boot makes it easy to create stand-alone, production-grade Spring based "
                    + "applications that you can just run. We take an opinionated view of the "
                    + "Spring platform and third-party libraries so you can get started with "
                    + "minimum fuss. Most Spring Boot applications need very little Spring "
                    + "configuration to get going quickly.";

    @Test
    void sameTextGivesSameFingerprint() {
        assertThat(SimHash.fingerprint(ARTICLE)).isEqualTo(SimHash.fingerprint(ARTICLE));
    }

    /** An article of the length content dedup actually sees, rather than a single paragraph. */
    private static final String LONG_ARTICLE = ARTICLE + " " + ARTICLE + " " + ARTICLE;

    @Test
    @DisplayName("on a page of realistic length, added boilerplate barely moves the fingerprint")
    void nearDuplicatesAreClose() {
        String withTimestamp = LONG_ARTICLE + " Last updated 2026-08-29 14:32. Views: 1841.";
        int distance = SimHash.hammingDistance(SimHash.fingerprint(LONG_ARTICLE),
                SimHash.fingerprint(withTimestamp));
        assertThat(distance).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("the shorter the page, the further a fixed edit moves the fingerprint")
    void sensitivityDependsOnLength() {
        String edit = " Last updated 2026-08-29 14:32. Views: 1841.";
        int shortDistance = SimHash.hammingDistance(SimHash.fingerprint(ARTICLE),
                SimHash.fingerprint(ARTICLE + edit));
        int longDistance = SimHash.hammingDistance(SimHash.fingerprint(LONG_ARTICLE),
                SimHash.fingerprint(LONG_ARTICLE + edit));
        // this is why minTextLength exists, and why the default threshold stays conservative:
        // a short page perturbs far enough to escape it, and is kept rather than wrongly dropped
        assertThat(shortDistance).isGreaterThan(longDistance);
    }

    @Test
    @DisplayName("unrelated documents are far apart")
    void unrelatedTextIsFar() {
        String other = "A recipe for slow-cooked beef bourguignon with red wine, carrots, "
                + "pearl onions and a bouquet garni, simmered for three hours until tender.";
        int distance =
                SimHash.hammingDistance(SimHash.fingerprint(ARTICLE), SimHash.fingerprint(other));
        assertThat(distance).isGreaterThan(10);
    }

    @Test
    void emptyTextGivesZero() {
        assertThat(SimHash.fingerprint("")).isZero();
        assertThat(SimHash.fingerprint("   ")).isZero();
    }

    @Test
    @DisplayName("bands partition the fingerprint without overlapping")
    void bandsCoverTheFingerprint() {
        long fingerprint = SimHash.fingerprint(ARTICLE);
        long rebuilt = 0L;
        for (int i = 0; i < SimHash.BANDS; i++) {
            rebuilt |= ((long) SimHash.band(fingerprint, i)) << (i * SimHash.BAND_BITS);
        }
        assertThat(rebuilt).isEqualTo(fingerprint);
    }

    @Test
    void hammingDistanceOfIdenticalValuesIsZero() {
        assertThat(SimHash.hammingDistance(42L, 42L)).isZero();
        assertThat(SimHash.hammingDistance(0L, 1L)).isEqualTo(1);
    }

}
