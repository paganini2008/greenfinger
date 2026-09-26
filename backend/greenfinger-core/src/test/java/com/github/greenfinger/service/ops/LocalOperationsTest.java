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

package com.github.greenfinger.service.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.DeleteLayer;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.service.CrawlerTestApplication;
import com.github.greenfinger.service.ops.GreenfingerOperations.DeleteAsk;
import com.github.greenfinger.service.ops.GreenfingerOperations.Overview;
import com.github.greenfinger.service.ops.GreenfingerOperations.SearchAsk;

/**
 * What a face asks for, answered by the services in this process.
 *
 * <p>
 * The same calls the terminal makes over the cluster, made here directly: if these are right, what
 * the terminal renders is right, because the two ends of the leader channel are this interface.
 *
 * @Description: LocalOperationsTest
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
@SpringBootTest(classes = CrawlerTestApplication.class)
@TestPropertySource(properties = {"greenfinger.output.file.directory=${java.io.tmpdir}/gf-ops/data",
        "greenfinger.frontier-directory=${java.io.tmpdir}/gf-ops/frontier",
        "greenfinger.dedup.url.directory=${java.io.tmpdir}/gf-ops/url",
        "greenfinger.dedup.content.directory=${java.io.tmpdir}/gf-ops/content",
        "greenfinger.output.index.lucene.directory=${java.io.tmpdir}/gf-ops-lucene",
        "greenfinger.output.vector.lucene.directory=${java.io.tmpdir}/gf-ops-lucene-vector",
        "spring.datasource.url=jdbc:h2:mem:greenfinger-ops;DB_CLOSE_DELAY=-1"})
class LocalOperationsTest {

    @Autowired
    private LocalOperations operations;

    @Autowired
    private CatalogStore catalogStore;

    @AfterEach
    void clean() {
        catalogStore.findAll().forEach(catalog -> catalogStore.deleteById(catalog.getId()));
    }

    @Test
    @DisplayName("A blank form carries the configured defaults rather than empty fields")
    void formCarriesTheDefaults() {
        Catalog form = operations.form(null);
        assertThat(form.getId()).isNull();
        assertThat(form.getMaxFetchSize()).isNotNull();
        assertThat(form.getPageEncoding()).isNotBlank();
        assertThat(form.getMaxVersions()).isNotNull();
    }

    @Test
    @DisplayName("Saving answers with what was stored, defaults applied")
    void savingAnswersWithWhatWasStored() {
        CatalogSnapshot saved = save("https://books.toscrape.com", "books");
        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getName()).isEqualTo("books");
        // a snapshot is CatalogDetails, so what it reports is the catalog with its defaults in
        // force rather than the row's blanks
        assertThat(saved.getPathPatterns()).isNotEmpty();
        assertThat(saved.getOutputTypes()).isNotEmpty();
        assertThat(saved.getVersion()).isNotNull();

        assertThat(operations.form(saved.getId()).getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("A url with no catalog behind it becomes one; the second ask finds the first")
    void ensureCreatesOnceAndThenFinds() {
        CatalogSnapshot first = operations.ensureCatalog(null, "https://books.toscrape.com");
        assertThat(first.getId()).isNotBlank();
        // the name defaults to the registrable domain, which is what makes the second call find
        // the first rather than making a second catalog of the same site
        CatalogSnapshot again = operations.ensureCatalog(null, "https://books.toscrape.com");
        assertThat(again.getId()).isEqualTo(first.getId());

        CatalogSnapshot named = operations.ensureCatalog("my-books", "https://books.toscrape.com");
        assertThat(named.getId()).isNotEqualTo(first.getId());
        assertThat(named.getName()).isEqualTo("my-books");
    }

    @Test
    @DisplayName("An id, a name, or a url: three ways of saying the same catalog")
    void threeWaysToNameOne() {
        CatalogSnapshot saved = save("https://books.toscrape.com", "books");

        assertThat(operations.catalog(saved.getId()).getId()).isEqualTo(saved.getId());
        assertThat(operations.ensureCatalog("books", null).getId()).isEqualTo(saved.getId());
        assertThat(operations.ensureCatalog(null, "https://books.toscrape.com").getId())
                .isEqualTo(saved.getId());
        assertThat(operations.ensureCatalog("books", "https://books.toscrape.com").getId())
                .isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("A url two catalogs share is a question, not a coin toss")
    void aSharedUrlNeedsAName() {
        save("https://books.toscrape.com", "books-daily");
        save("https://books.toscrape.com", "books-weekly");

        assertThatThrownBy(() -> operations.ensureCatalog(null, "https://books.toscrape.com"))
                .isInstanceOf(WebCrawlerException.class).hasMessageContaining("books-daily")
                .hasMessageContaining("books-weekly");
        // named, it is not ambiguous at all
        assertThat(operations.ensureCatalog("books-weekly", "https://books.toscrape.com").getName())
                .isEqualTo("books-weekly");
    }

    @Test
    @DisplayName("A name that is not there and no url to make it from is refused")
    void ensureNeedsAUrlToCreateWith() {
        // core names no flags: the same message reaches the page, which has no --url to offer
        assertThatThrownBy(() -> operations.ensureCatalog("nothing-like-this", null))
                .isInstanceOf(WebCrawlerException.class).hasMessageContaining("url");
        assertThatThrownBy(() -> operations.ensureCatalog(null, null))
                .isInstanceOf(WebCrawlerException.class);
    }

    @Test
    @DisplayName("The overview carries the catalogs, what is running, and the last run")
    void overviewIsOneCall() {
        CatalogSnapshot saved = save("https://books.toscrape.com", "books");
        Overview overview = operations.overview();
        assertThat(overview.catalogs()).extracting(CatalogSnapshot::getId).contains(saved.getId());
        assertThat(overview.running()).isEmpty();
        assertThat(overview.lastRuns()).isNotNull();
        assertThat(operations.categories()).isNotEmpty();
    }

    @Test
    @DisplayName("One catalog, by id or by name; nothing crawling means nothing to show")
    void oneCatalogByIdOrName() {
        CatalogSnapshot saved = save("https://books.toscrape.com", "books");
        assertThat(operations.catalog(saved.getId()).getName()).isEqualTo("books");
        assertThat(operations.catalog("books").getId()).isEqualTo(saved.getId());
        assertThat(operations.catalog(null)).isNull();
        assertThatThrownBy(() -> operations.catalog("no-such-thing"))
                .isInstanceOf(WebCrawlerException.class);
    }

    @Test
    @DisplayName("Versions and reports of a catalog that has never run")
    void versionsAndReportsOfANewCatalog() {
        CatalogSnapshot saved = save("https://books.toscrape.com", "books");
        GreenfingerOperations.Versions versions = operations.versions(saved.getId());
        assertThat(versions.catalogId()).isEqualTo(saved.getId());
        assertThat(versions.catalogName()).isEqualTo("books");
        assertThat(versions.rows()).isNotNull();
        // a report is written when a crawl finishes, so a catalog that has never run has none --
        // and says so rather than answering with an empty table
        assertThatThrownBy(() -> operations.report(saved.getId(), null))
                .isInstanceOf(WebCrawlerException.class).hasMessageContaining("report");
    }

    @Test
    @DisplayName("Interrupting what is not running is false, and there is no frame to draw")
    void nothingIsRunning() {
        CatalogSnapshot saved = save("https://books.toscrape.com", "books");
        assertThat(operations.interrupt(saved.getId())).isFalse();
        assertThat(operations.live(saved.getId(), true)).isNull();
    }

    @Test
    @DisplayName("A delete has to say what to remove")
    void deleteHasToSayWhat() {
        CatalogSnapshot saved = save("https://books.toscrape.com", "books");
        assertThatThrownBy(() -> operations.delete(new DeleteAsk(saved.getId(), null, null, false,
                false, EnumSet.allOf(DeleteLayer.class), true, false)))
                        .isInstanceOf(WebCrawlerException.class)
                        .hasMessageContaining("--version");
    }

    @Test
    @DisplayName("A dry run of every version removes nothing and reports per layer")
    void dryRunRemovesNothing() {
        CatalogSnapshot saved = save("https://books.toscrape.com", "books");
        List<com.github.greenfinger.service.DeleteReport.Line> lines =
                operations.delete(new DeleteAsk(saved.getId(), null, null, true, false,
                        EnumSet.allOf(DeleteLayer.class), true, false));
        assertThat(lines).isNotNull();
        assertThat(operations.catalog(saved.getId())).isNotNull();
    }

    @Test
    @DisplayName("Searching before anything has been published answers with nothing at all")
    void searchBeforeAnythingIsPublished() {
        save("https://books.toscrape.com", "books");
        // not an empty result: no version is being served, so there is nowhere to look, and the
        // face says "nothing has finished crawling yet" rather than "no matches"
        assertThat(operations.search(new SearchAsk("anything", null, 10, "words"))).isNull();
        assertThat(operations.search(new SearchAsk(null, null, 10, "meaning"))).isNull();
    }

    @Test
    @DisplayName("The index and the vector store say what they are, even when empty")
    void storesDescribeThemselves() {
        GreenfingerOperations.Info index = operations.indexInfo();
        assertThat(index.about()).isNotEmpty();
        assertThat(index.counts()).isEmpty();

        GreenfingerOperations.Info vectors = operations.vectorInfo();
        assertThat(vectors.about()).extracting(GreenfingerOperations.InfoRow::name)
                .contains("Store", "Text collection");
    }

    @Test
    @DisplayName("Deleting a definition answers with the name it had")
    void deletingAnswersWithTheName() {
        CatalogSnapshot saved = save("https://books.toscrape.com", "books");
        assertThat(operations.deleteCatalog(saved.getId())).isEqualTo("books");
        assertThatThrownBy(() -> operations.catalog(saved.getId()))
                .isInstanceOf(WebCrawlerException.class);
    }

    private CatalogSnapshot save(String url, String name) {
        Catalog form = operations.form(null);
        form.setUrl(url);
        form.setName(name);
        form.setOutputTypes(java.util.Set.of(OutputType.FILE));
        return operations.saveCatalog(form);
    }

}
