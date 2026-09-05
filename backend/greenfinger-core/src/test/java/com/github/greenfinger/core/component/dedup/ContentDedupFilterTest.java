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
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 
 * @Description: ContentDedupFilterTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class ContentDedupFilterTest {

    private static final String ARTICLE =
            "Spring Boot makes it easy to create stand-alone, production-grade Spring based "
                    + "applications that you can just run. We take an opinionated view of the "
                    + "Spring platform and third-party libraries so you can get started with "
                    + "minimum fuss, and most applications need very little configuration.";

    @TempDir
    Path directory;

    private ContentDedupFilter filter;

    @AfterEach
    void tearDown() throws Exception {
        if (filter != null) {
            filter.destroy();
        }
    }

    private Sha256ContentDedupFilter sha256() throws Exception {
        Sha256ContentDedupFilter created =
                new Sha256ContentDedupFilter(directory.resolve("sha").toString(), 50);
        created.afterPropertiesSet();
        filter = created;
        return created;
    }

    private SimHashContentDedupFilter simhash() throws Exception {
        SimHashContentDedupFilter created =
                new SimHashContentDedupFilter(directory.resolve("sim").toString(), 3, 50);
        created.afterPropertiesSet();
        filter = created;
        return created;
    }

    @Test
    @DisplayName("the same article under a second url is recognised")
    void sha256CatchesExactDuplicates() throws Exception {
        Sha256ContentDedupFilter filter = sha256();
        assertThat(filter.isDuplicate(ARTICLE)).isFalse();
        assertThat(filter.isDuplicate(ARTICLE)).isTrue();
    }

    @Test
    @DisplayName("an exact hash misses a page that differs only in a view counter")
    void sha256MissesNearDuplicates() throws Exception {
        Sha256ContentDedupFilter filter = sha256();
        assertThat(filter.isDuplicate(ARTICLE)).isFalse();
        assertThat(filter.isDuplicate(ARTICLE + " Views: 1841.")).isFalse();
    }

    @Test
    @DisplayName("simhash catches exactly that case, which is why it exists")
    void simhashCatchesNearDuplicates() throws Exception {
        SimHashContentDedupFilter filter = simhash();
        String article = ARTICLE + " " + ARTICLE + " " + ARTICLE;
        assertThat(filter.isDuplicate(article)).isFalse();
        assertThat(filter.isDuplicate(article + " Views: 1841. Updated 2026-08-29.")).isTrue();
    }

    @Test
    @DisplayName("a short page escapes the threshold and is kept rather than wrongly dropped")
    void simhashIsConservativeOnShortPages() throws Exception {
        SimHashContentDedupFilter filter = simhash();
        assertThat(filter.isDuplicate(ARTICLE)).isFalse();
        assertThat(filter.isDuplicate(ARTICLE + " Views: 1841. Updated 2026-08-29.")).isFalse();
    }

    @Test
    void simhashKeepsUnrelatedDocuments() throws Exception {
        SimHashContentDedupFilter filter = simhash();
        assertThat(filter.isDuplicate(ARTICLE)).isFalse();
        assertThat(filter.isDuplicate(
                "A recipe for slow-cooked beef bourguignon with red wine, carrots, pearl "
                        + "onions and a bouquet garni, simmered gently for three hours."))
                                .isFalse();
    }

    @Test
    @DisplayName("short text is never deduplicated; a shared error page would swallow the site")
    void shortTextIsNeverDuplicate() throws Exception {
        Sha256ContentDedupFilter filter = sha256();
        assertThat(filter.isDuplicate("Not found")).isFalse();
        assertThat(filter.isDuplicate("Not found")).isFalse();
    }

    @Test
    void fingerprintsAreStableAndNamed() throws Exception {
        Sha256ContentDedupFilter sha = sha256();
        assertThat(sha.fingerprint(ARTICLE)).isEqualTo(sha.fingerprint(ARTICLE)).hasSize(64);
        assertThat(sha.getName()).isEqualTo("sha256");
        assertThat(sha.fingerprint("  ")).isNull();
        sha.destroy();

        SimHashContentDedupFilter sim = simhash();
        assertThat(sim.fingerprint(ARTICLE)).isEqualTo(sim.fingerprint(ARTICLE));
        assertThat(sim.getName()).isEqualTo("simhash");
    }

    @Test
    void noOpNeverRejects() {
        ContentDedupFilter noOp = new ContentDedupFilter.NoOp();
        assertThat(noOp.isDuplicate(ARTICLE)).isFalse();
        assertThat(noOp.isDuplicate(ARTICLE)).isFalse();
        assertThat(noOp.fingerprint(ARTICLE)).isNull();
        assertThat(noOp.getName()).isEqualTo("none");
    }

}
