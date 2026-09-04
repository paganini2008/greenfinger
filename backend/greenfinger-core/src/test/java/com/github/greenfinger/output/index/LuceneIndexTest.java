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

package com.github.greenfinger.output.index;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.github.greenfinger.core.output.IndexAdmin;
import com.github.greenfinger.core.output.SearchRequest;
import com.github.greenfinger.core.output.SearchResponse;
import com.github.greenfinger.output.OutputFixtures;
import com.github.greenfinger.output.OutputProperties;

/**
 * The embedded index, end to end: write, search, count, delete.
 * 
 * @Description: LuceneIndexTest
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
class LuceneIndexTest {

    @TempDir
    Path directory;

    private OutputProperties.Index config;
    private LuceneIndexes indexes;

    @BeforeEach
    void setUp() {
        config = new OutputProperties.Index();
        config.setProvider("lucene");
        config.getLucene().setDirectory(directory.toString());
        config.getLucene().setCommitEvery(1);
        indexes = LuceneIndexes.shared(directory.toString(),
                LuceneAnalyzers.of(config.getLucene().getAnalyzer()));
    }

    @AfterEach
    void tearDown() {
        LuceneIndexes.closeShared(directory.toString());
    }

    private String indexName() {
        return IndexAdmin.indexOf(config.getPrefix(), OutputFixtures.CATALOG_ID);
    }

    private void crawl() throws Exception {
        try (LuceneOutputChannel channel = new LuceneOutputChannel(config, indexes)) {
            channel.open(OutputFixtures.catalogDetails());
            channel.write(page("https://www.example.com/a", "Page A",
                    "Alpha content, plenty of prose and very few links at all"));
            channel.write(page("https://www.example.com/b", "Page B",
                    "Beta content, plenty of prose and very few links at all"));
        }
    }

    private com.github.greenfinger.core.output.OutputPayload page(String url, String title,
            String text) {
        return OutputFixtures.payload(OutputFixtures.catalogDetails(),
                OutputFixtures.page(url, title, text));
    }

    private SearchResponse search(String keyword) throws Exception {
        return new LuceneSearcher(config, indexes).search(SearchRequest.builder().keyword(keyword)
                .catalogVersions(List.of(OutputFixtures.CATALOG_ID + ":0")).pageSize(10).build());
    }

    @Test
    @DisplayName("what a crawl writes, a search finds")
    void writesAndFinds() throws Exception {
        crawl();

        SearchResponse response = search("alpha");
        assertThat(response.getTotal()).isEqualTo(1L);
        assertThat(response.getResults().get(0).getTitle()).isEqualTo("Page A");
        assertThat(response.getResults().get(0).getHighlights())
                .anyMatch(fragment -> fragment.contains("<em>"));
    }

    @Test
    @DisplayName("the index is named after the catalog's id, one per catalog")
    void oneIndexPerCatalog() throws Exception {
        crawl();
        assertThat(indexes.names()).containsExactly(indexName());
        assertThat(indexName()).isEqualTo("greenfinger-" + OutputFixtures.CATALOG_ID);
    }

    @Test
    @DisplayName("a page written twice is one document, which is what makes a replay safe")
    void writingTwiceReplaces() throws Exception {
        crawl();
        crawl();

        LuceneIndexAdmin admin = new LuceneIndexAdmin(config, indexes);
        assertThat(admin.countByCatalogVersion(OutputFixtures.CATALOG_ID + ":0")).isEqualTo(2L);
    }

    @Test
    void countsAndDeletesOneVersion() throws Exception {
        crawl();
        LuceneIndexAdmin admin = new LuceneIndexAdmin(config, indexes);

        assertThat(admin.getName()).isEqualTo("lucene");
        assertThat(admin.getLocation()).isEqualTo(directory.toAbsolutePath().toString());
        assertThat(admin.indexExists(OutputFixtures.CATALOG_ID)).isTrue();
        assertThat(admin.countByCatalogVersion(OutputFixtures.CATALOG_ID + ":0")).isEqualTo(2L);

        assertThat(admin.deleteByCatalogVersion(OutputFixtures.CATALOG_ID + ":0")).isEqualTo(2L);
        assertThat(admin.countByCatalogVersion(OutputFixtures.CATALOG_ID + ":0")).isZero();
        assertThat(search("alpha").getResults()).isEmpty();
    }

    @Test
    @DisplayName("a whole catalog is a directory, so it goes at once")
    void deletingACatalogDropsItsDirectory() throws Exception {
        crawl();
        LuceneIndexAdmin admin = new LuceneIndexAdmin(config, indexes);

        assertThat(admin.deleteByCatalog(OutputFixtures.CATALOG_ID)).isEqualTo(2L);
        assertThat(admin.indexExists(OutputFixtures.CATALOG_ID)).isFalse();
        assertThat(admin.listIndices()).isEmpty();
        assertThat(admin.deleteByCatalog(OutputFixtures.CATALOG_ID)).isZero();
    }

    @Test
    @DisplayName("a catalog that has never been crawled is empty, not an error")
    void searchingNothingIsEmpty() throws Exception {
        assertThat(search("alpha").getResults()).isEmpty();
        assertThat(new LuceneIndexAdmin(config, indexes)
                .countByCatalogVersion(OutputFixtures.CATALOG_ID + ":0")).isZero();
    }

    @Test
    @DisplayName("only the versions the request names")
    void filtersByCatalogVersion() throws Exception {
        crawl();

        SearchResponse other = new LuceneSearcher(config, indexes)
                .search(SearchRequest.builder().keyword("alpha")
                        .catalogVersions(List.of(OutputFixtures.CATALOG_ID + ":9")).pageSize(10)
                        .build());
        assertThat(other.getResults()).isEmpty();
    }

    @Test
    @DisplayName("naming no catalog searches every index under the prefix")
    void searchesEverythingByDefault() throws Exception {
        crawl();

        SearchResponse response = new LuceneSearcher(config, indexes)
                .search(SearchRequest.builder().keyword("content").pageSize(10).build());
        assertThat(response.getResults()).hasSize(2);
    }

    @Test
    @DisplayName("the cursor pages forward without repeating or skipping a hit")
    void pagesWithACursor() throws Exception {
        crawl();

        SearchResponse first = new LuceneSearcher(config, indexes)
                .search(SearchRequest.builder().keyword("content").pageSize(1).build());
        assertThat(first.getResults()).hasSize(1);
        assertThat(first.getNextCursor()).isNotNull();

        SearchResponse second = new LuceneSearcher(config, indexes)
                .search(SearchRequest.builder().keyword("content").pageSize(1)
                        .cursor(first.getNextCursor()).build());
        assertThat(second.getResults()).hasSize(1);
        assertThat(second.getResults().get(0).getId())
                .isNotEqualTo(first.getResults().get(0).getId());
    }

    @Test
    void analyzersAreNamed() {
        assertThat(LuceneAnalyzers.of("standard").getClass().getSimpleName())
                .isEqualTo("StandardAnalyzer");
        assertThat(LuceneAnalyzers.of("smartcn").getClass().getSimpleName())
                .isEqualTo("SmartChineseAnalyzer");
        // what somebody moving from an Elasticsearch configuration will have written
        assertThat(LuceneAnalyzers.of("ik_max_word").getClass().getSimpleName())
                .isEqualTo("SmartChineseAnalyzer");
        assertThat(LuceneAnalyzers.of("cjk").getClass().getSimpleName())
                .isEqualTo("CJKAnalyzer");
        assertThat(LuceneAnalyzers.of(null).getClass().getSimpleName())
                .isEqualTo("StandardAnalyzer");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> LuceneAnalyzers.of("porter"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("porter");
    }

}
