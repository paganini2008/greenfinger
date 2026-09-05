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

package com.github.greenfinger.output;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.output.FileLayout;

/**
 * 
 * @Description: FileLayoutTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
class FileLayoutTest {

    private static final String ID = "29e24399-91e4-5f76-a644-d14d175eab77";

    @Test
    @DisplayName("the version is part of the path, so deleting one is deleting one directory")
    void versionIsInThePath() {
        FileLayout layout = new FileLayout("toscrape", 3, 2);
        assertThat(layout.versionPrefix()).isEqualTo("toscrape/v3");
        assertThat(layout.catalogPrefix()).isEqualTo("toscrape");
        assertThat(layout.settings()).isEqualTo("toscrape/v3/settings.json");
    }

    @Test
    void pagesCarryBothForms() {
        FileLayout layout = new FileLayout("site", 0, 0);
        assertThat(layout.html(ID)).isEqualTo("site/v0/pages/" + ID + ".html");
        assertThat(layout.text(ID)).isEqualTo("site/v0/pages/" + ID + ".txt");
    }

    @Test
    @DisplayName("two hex characters per level, taken from the front of a v5 id")
    void shardsOnThePrefix() {
        FileLayout layout = new FileLayout("site", 0, 2);
        assertThat(layout.html(ID)).isEqualTo("site/v0/pages/29/e2/" + ID + ".html");

        FileLayout deeper = new FileLayout("site", 0, 3);
        assertThat(deeper.html(ID)).isEqualTo("site/v0/pages/29/e2/43/" + ID + ".html");
    }

    @Test
    void shardingCanBeTurnedOff() {
        assertThat(new FileLayout("site", 0, 0).html(ID)).doesNotContain("/29/");
    }

    @Test
    void shardDepthIsBounded() {
        // four levels is the most that is useful; asking for more is clamped rather than refused
        assertThat(new FileLayout("site", 0, 99).html(ID)).isEqualTo(
                "site/v0/pages/29/e2/43/99/" + ID + ".html");
        assertThat(new FileLayout("site", 0, -1).html(ID))
                .isEqualTo("site/v0/pages/" + ID + ".html");
    }

    @Test
    void imagesAreNamedByTheirMediaType() {
        FileLayout layout = new FileLayout("site", 0, 0);
        assertThat(layout.image(ID, "image/jpeg", "https://a/x")).endsWith(".jpg");
        assertThat(layout.image(ID, "image/png", "https://a/x")).endsWith(".png");
        assertThat(layout.image(ID, "image/webp", "https://a/x")).endsWith(".webp");
        assertThat(layout.image(ID, "image/svg+xml", "https://a/x")).endsWith(".svg");
        assertThat(layout.image(ID, "image/gif", "https://a/x")).endsWith(".gif");
    }

    @Test
    @DisplayName("an unknown media type falls back to the url, then to .bin")
    void extensionFallsBackToTheUrl() {
        assertThat(FileLayout.extensionOf("application/octet-stream", "https://a/pic.avif"))
                .isEqualTo(".avif");
        assertThat(FileLayout.extensionOf(null, "https://a/no-extension")).isEqualTo(".bin");
        assertThat(FileLayout.extensionOf("", null)).isEqualTo(".bin");
        // a query string must not end up in the file name
        assertThat(FileLayout.extensionOf("", "https://a/pic.jpg?v=2")).isEqualTo(".jpg");
    }

    @Test
    void mediaTypeParametersAreIgnored() {
        assertThat(FileLayout.extensionOf("image/jpeg; charset=binary", "https://a/x"))
                .isEqualTo(".jpg");
    }

    @Test
    @DisplayName("a catalog name reaches the file system, so anything unusual becomes an underscore")
    void namesAreMadeSafe() {
        assertThat(FileLayout.safeName("books.toscrape.com")).isEqualTo("books.toscrape.com");
        assertThat(FileLayout.safeName("a b/c:d")).isEqualTo("a_b_c_d");
        assertThat(FileLayout.safeName(null)).isEqualTo("catalog");
        assertThat(new FileLayout("my catalog", 1, 0).versionPrefix()).isEqualTo("my_catalog/v1");
    }

    @Test
    void buildsFromACatalogById() {
        // the id, not the name: a rename must not move anything on disk
        assertThat(FileLayout.of(OutputFixtures.catalogDetails(), 2).versionPrefix())
                .isEqualTo(OutputFixtures.CATALOG_ID + "/v0");
    }

}
