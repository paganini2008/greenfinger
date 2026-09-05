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
 * @Description: RocksDbUrlPathFilterTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class RocksDbUrlPathFilterTest {

    @TempDir
    Path directory;

    private RocksDbUrlPathFilter filter;

    private RocksDbUrlPathFilter open(boolean normalize) throws Exception {
        filter = new RocksDbUrlPathFilter(directory.resolve("url").toString(), normalize);
        filter.afterPropertiesSet();
        return filter;
    }

    @AfterEach
    void tearDown() throws Exception {
        if (filter != null) {
            filter.destroy();
        }
    }

    @Test
    @DisplayName("the first sighting is new, the second is not")
    void recordsOnFirstSighting() throws Exception {
        RocksDbUrlPathFilter filter = open(false);
        assertThat(filter.mightExist("https://a.com/x")).isFalse();
        assertThat(filter.mightExist("https://a.com/x")).isTrue();
        assertThat(filter.mightExist("https://a.com/y")).isFalse();
    }

    @Test
    @DisplayName("normalising collapses campaign variants of one url")
    void normalizesBeforeComparing() throws Exception {
        RocksDbUrlPathFilter filter = open(true);
        assertThat(filter.mightExist("https://a.com/story")).isFalse();
        assertThat(filter.mightExist("https://a.com/story/?utm_source=news")).isTrue();
    }

    @Test
    @DisplayName("without normalising, the same variants are treated as different urls")
    void withoutNormalizationVariantsAreDistinct() throws Exception {
        RocksDbUrlPathFilter filter = open(false);
        assertThat(filter.mightExist("https://a.com/story")).isFalse();
        assertThat(filter.mightExist("https://a.com/story/?utm_source=news")).isFalse();
    }

    @Test
    @DisplayName("closing keeps the data, so an interrupted crawl can resume")
    void closingDoesNotDeleteTheStore() throws Exception {
        RocksDbUrlPathFilter first = open(false);
        first.mightExist("https://a.com/x");
        first.destroy();

        RocksDbUrlPathFilter second = open(false);
        assertThat(second.mightExist("https://a.com/x")).isTrue();
    }

    @Test
    @DisplayName("cleaning removes the data deliberately")
    void cleanRemovesEverything() throws Exception {
        RocksDbUrlPathFilter first = open(false);
        first.mightExist("https://a.com/x");
        first.clean();
        filter = null;

        RocksDbUrlPathFilter second = open(false);
        assertThat(second.mightExist("https://a.com/x")).isFalse();
    }

    @Test
    void exportsEveryStoredPath() throws Exception {
        RocksDbUrlPathFilter filter = open(false);
        filter.mightExist("https://a.com/1");
        filter.mightExist("https://a.com/2");

        java.util.List<String> exported = new java.util.ArrayList<>();
        int count = filter.export((index, path) -> exported.add(path), false);
        assertThat(count).isEqualTo(2);
        assertThat(exported).containsExactlyInAnyOrder("https://a.com/1", "https://a.com/2");
    }

    @Test
    void reportsItsName() throws Exception {
        assertThat(open(false).getName()).isEqualTo("rocksdb");
    }

}
