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

package com.github.greenfinger.core;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.model.Catalog;

/**
 * One crawl at a time, asked two ways.
 *
 * <p>
 * The permit answers "is this process busy" and the catalog table answers "is any other node
 * busy". Both are needed: a permit knows nothing about the other nodes, and the table cannot tell
 * two threads in one process apart.
 * 
 * @Description: WebCrawlerSemaphoreTest
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
class WebCrawlerSemaphoreTest {

    @Test
    @DisplayName("a second crawl in this process is refused, whatever the cluster says")
    void oneAtATimeHere() {
        WebCrawlerSemaphore semaphore = new WebCrawlerSemaphore(nothingRunning());

        assertThat(semaphore.acquire("cat-1", 10, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(semaphore.acquire("cat-2", 10, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(semaphore.getCatalogId()).isEqualTo("cat-1");

        semaphore.release();
        assertThat(semaphore.isOccupied()).isFalse();
        assertThat(semaphore.acquire("cat-2", 10, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    @DisplayName("a crawl running on another node is refused here too")
    void oneAtATimeAcrossTheCluster() {
        WebCrawlerSemaphore semaphore = new WebCrawlerSemaphore(running("cat-9", "elsewhere"));

        assertThat(semaphore.acquire("cat-1", 10, TimeUnit.MILLISECONDS)).isFalse();
        // and the permit was given back, so the node is not left unable to crawl anything
        assertThat(semaphore.isOccupied()).isFalse();
        assertThat(semaphore.running()).hasSize(1);
    }

    @Test
    @DisplayName("the same catalog running elsewhere is a node joining it, not a second crawl")
    void joiningIsNotASecondCrawl() {
        WebCrawlerSemaphore semaphore = new WebCrawlerSemaphore(running("cat-1", "books"));

        assertThat(semaphore.acquire("cat-1", 10, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    @DisplayName("a database that cannot be read is not a refusal: the permit already said yes")
    void anUnreadableTableDoesNotStopACrawl() {
        WebCrawlerSemaphore semaphore = new WebCrawlerSemaphore(new CatalogStore() {

            @Override
            public String getName() {
                return "broken";
            }

            @Override
            public List<Catalog> findRunning() {
                throw new IllegalStateException("no connection");
            }

            @Override
            public Catalog save(Catalog catalog) {
                return catalog;
            }

            @Override
            public Optional<Catalog> findById(String id) {
                return Optional.empty();
            }

            @Override
            public Optional<Catalog> findByName(String name) {
                return Optional.empty();
            }

            @Override
            public List<Catalog> findAll() {
                return List.of();
            }

            @Override
            public List<String> findAllCategories() {
                return List.of();
            }

            @Override
            public boolean deleteById(String id) {
                return false;
            }

            @Override
            public int incrementIndexVersion(String id) {
                return 0;
            }

            @Override
            public void publishSearchVersion(String id, int version) {
                // not part of this question
            }

            @Override
            public void resetVersions(String id) {
                // not part of this question
            }

            @Override
            public void setRunningState(String id, String runningState) {
                // not part of this question
            }
        });

        assertThat(semaphore.acquire("cat-1", 10, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(semaphore.running()).isEmpty();
    }

    @Test
    @DisplayName("with no catalog table at all the permit is the whole rule, as it was before")
    void withoutATableThePermitIsEnough() {
        WebCrawlerSemaphore semaphore = new WebCrawlerSemaphore();

        assertThat(semaphore.acquire("cat-1", 10, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(semaphore.acquire("cat-2", 10, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(semaphore.running()).isEmpty();
    }

    private static CatalogStore nothingRunning() {
        return running(null, null);
    }

    /** Only findRunning matters here; the rest of the store is not part of the question. */
    private static CatalogStore running(String id, String name) {
        return new CatalogStore() {

            @Override
            public String getName() {
                return "test";
            }

            @Override
            public List<Catalog> findRunning() {
                if (id == null) {
                    return List.of();
                }
                Catalog catalog = new Catalog();
                catalog.setId(id);
                catalog.setName(name);
                catalog.setRunningState("crawl");
                return List.of(catalog);
            }

            @Override
            public Catalog save(Catalog catalog) {
                return catalog;
            }

            @Override
            public Optional<Catalog> findById(String catalogId) {
                return Optional.empty();
            }

            @Override
            public Optional<Catalog> findByName(String catalogName) {
                return Optional.empty();
            }

            @Override
            public List<Catalog> findAll() {
                return List.of();
            }

            @Override
            public List<String> findAllCategories() {
                return List.of();
            }

            @Override
            public boolean deleteById(String catalogId) {
                return false;
            }

            @Override
            public int incrementIndexVersion(String catalogId) {
                return 0;
            }

            @Override
            public void publishSearchVersion(String catalogId, int version) {
                // not part of this question
            }

            @Override
            public void resetVersions(String catalogId) {
                // not part of this question
            }

            @Override
            public void setRunningState(String catalogId, String runningState) {
                // not part of this question
            }
        };
    }

}
