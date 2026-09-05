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

package com.github.greenfinger.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.test.context.TestPropertySource;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsImpl;
import com.github.greenfinger.core.catalog.CatalogDetailsNotFoundException;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.engine.CrawledPage;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.output.FileLayout;
import com.github.greenfinger.core.record.ResourceRecord;

/**
 * The database half of the write path, against a real H2.
 * 
 * @Description: JpaStoreTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@DataJpaTest
@EntityScan(basePackages = "com.github.greenfinger.core.model")
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
class JpaStoreTest {

    @Autowired
    private CatalogRepository catalogRepository;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private ResourceImageRepository resourceImageRepository;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private JpaCatalogStore catalogStore() {
        return new JpaCatalogStore(catalogRepository);
    }

    private JpaResourceRecordStore recordStore() {
        return new JpaResourceRecordStore(resourceRepository, imageRepository,
                resourceImageRepository,
                new ImageWriter(imageRepository, resourceImageRepository,
                        transactionManager));
    }

    private Catalog catalog(String name) {
        Catalog catalog = new Catalog();
        catalog.setName(name);
        catalog.setUrl("https://" + name + ".com");
        catalog.setStartUrl("https://" + name + ".com");
        catalog.setCat("news");
        catalog.setPathPattern("**." + name + ".com");
        catalog.setOutputTypes(Set.of(OutputType.FILE));
        catalog.setCountingType(CountingType.SAVED_RESOURCE_COUNT);
        return catalog;
    }

    private CatalogDetails detailsOf(Catalog catalog) {
        return new CatalogDetailsImpl(catalog, new WebCrawlerProperties());
    }

    private CrawledPage page(String url, String title) {
        CrawledPage page = new CrawledPage();
        page.setUrl(url);
        page.setTitle(title);
        page.setText("The text of " + title);
        page.setHtml("<html><body>" + title + "</body></html>");
        page.setCat("news");
        page.setVersion(0);
        page.setDepth(1);
        page.setReferer("https://site.com");
        page.setContentHash("hash-" + title);
        page.setFetchedAt(new Date());
        return page;
    }

    private CrawledPage pageWithImage(String url, String title, String imageHash) {
        CrawledPage page = page(url, title);
        CrawledPage.StoredImage image = new CrawledPage.StoredImage();
        image.setSourceUrl(url + "/pic.jpg");
        image.setContentHash(imageHash);
        image.setContentType("image/jpeg");
        image.setAlt("alt of " + title);
        image.setContext("words near " + title);
        image.setWidth(200);
        image.setHeight(150);
        image.setBytes(3L);
        image.setData(new byte[] {1, 2, 3});
        page.getStoredImages().add(image);
        return page;
    }

    private FileLayout layout(CatalogDetails details) {
        return FileLayout.of(details, 2);
    }

    @Test
    void savingAssignsAUuidV7() {
        Catalog saved = catalogStore().save(catalog("alpha"));
        assertThat(saved.getId()).isNotNull().hasSize(36);
        assertThat(UUID.fromString(saved.getId()).version()).isEqualTo(7);
    }

    @Test
    void newCatalogsStartUnpublished() {
        Catalog saved = catalogStore().save(catalog("beta"));
        assertThat(saved.getIndexVersion()).isZero();
        // -1, not 0: nothing has finished, so there is no version for search to serve
        assertThat(saved.getSearchVersion()).isEqualTo(-1);
    }

    @Test
    void findsByNameIgnoringCase() {
        catalogStore().save(catalog("gamma"));
        assertThat(catalogStore().findByName("GAMMA")).isPresent();
    }

    @Test
    @DisplayName("rebuild moves the write version but leaves the one search is serving")
    void incrementingTheVersionDoesNotDisturbSearch() {
        JpaCatalogStore store = catalogStore();
        Catalog saved = store.save(catalog("delta"));
        store.publishSearchVersion(saved.getId(), 0);

        int next = store.incrementIndexVersion(saved.getId());

        assertThat(next).isEqualTo(1);
        Catalog reloaded = store.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getIndexVersion()).isEqualTo(1);
        assertThat(reloaded.getSearchVersion()).isZero();
    }

    @Test
    void publishingMovesSearchToTheFinishedVersion() {
        JpaCatalogStore store = catalogStore();
        Catalog saved = store.save(catalog("epsilon"));
        store.incrementIndexVersion(saved.getId());
        store.publishSearchVersion(saved.getId(), 1);

        assertThat(store.findById(saved.getId()).orElseThrow().getSearchVersion()).isEqualTo(1);
    }

    @Test
    void runningStateIsTracked() {
        JpaCatalogStore store = catalogStore();
        Catalog saved = store.save(catalog("zeta"));
        store.setRunningState(saved.getId(), "crawl");

        assertThat(store.findRunning()).extracting(Catalog::getName).contains("zeta");

        store.setRunningState(saved.getId(), "none");
        assertThat(store.findRunning()).isEmpty();
    }

    @Test
    void categoriesAreDistinctAndSorted() {
        JpaCatalogStore store = catalogStore();
        store.save(catalog("eta"));
        Catalog other = catalog("theta");
        other.setCat("tech");
        store.save(other);

        // the store still answers "which are in use", which is not the same question as
        // "which are allowed" -- that one is the enum, and CatalogAdminService answers it
        assertThat(store.findAllCategories()).containsExactly("news", "tech");
    }

    @Test
    @DisplayName("a category nothing recognises is stored as other rather than as itself")
    void anUnknownCategoryBecomesOther() {
        JpaCatalogStore store = catalogStore();
        Catalog stray = catalog("kappa");
        stray.setCat("whatever somebody typed");
        Catalog saved = store.save(stray);

        assertThat(saved.getCat()).isEqualTo("other");
    }

    @Test
    void deleteReportsWhetherAnythingWent() {
        JpaCatalogStore store = catalogStore();
        Catalog saved = store.save(catalog("iota"));
        assertThat(store.deleteById(saved.getId())).isTrue();
        assertThat(store.deleteById(saved.getId())).isFalse();
    }

    @Test
    void missingCatalogsAreReportedClearly() {
        assertThatThrownBy(() -> catalogStore().incrementIndexVersion("no-such-id"))
                .isInstanceOf(CatalogDetailsNotFoundException.class);
    }

    @Test
    @DisplayName("the resource id is derived, so the same url at the same version repeats it")
    void resourceIdsAreDeterministic() {
        Catalog saved = catalogStore().save(catalog("kappa"));
        CatalogDetails details = detailsOf(saved);

        ResourceRecord first = recordStore().save(details, page("https://kappa.com/a", "A"),
                layout(details));
        assertThat(first.resource().getId()).hasSize(36);
        assertThat(UUID.fromString(first.resource().getId()).version()).isEqualTo(5);

        // the file paths follow from the id, so they are settled before anything is written
        assertThat(first.resource().getHtmlFilePath())
                .isEqualTo(layout(details).html(first.resource().getId()));
        assertThat(first.resource().getHtmlContentFilePath()).endsWith(".txt");
    }

    @Test
    void theBodyIsNotStoredInTheDatabase() {
        Catalog saved = catalogStore().save(catalog("lambda"));
        CatalogDetails details = detailsOf(saved);
        ResourceRecord record =
                recordStore().save(details, page("https://lambda.com/a", "A"), layout(details));

        assertThat(resourceRepository.findById(record.resource().getId()).orElseThrow())
                // the validators are the site's to send: plenty of sites publish neither, and a
                // page rendered by a browser engine never sees a response to read them from
                .hasNoNullFieldsOrPropertiesExcept("etag", "httpLastModified");
        assertThat(record.resource().getHtmlContentFilePath()).isNotBlank();
    }

    @Test
    @DisplayName("one image shared by two pages is one row and one file")
    void imagesAreDeduplicatedWithinAVersion() {
        Catalog saved = catalogStore().save(catalog("mu"));
        CatalogDetails details = detailsOf(saved);
        JpaResourceRecordStore store = recordStore();

        ResourceRecord first = store.save(details,
                pageWithImage("https://mu.com/a", "A", "same-bytes"), layout(details));
        ResourceRecord second = store.save(details,
                pageWithImage("https://mu.com/b", "B", "same-bytes"), layout(details));

        assertThat(first.images().get(0).image().getId())
                .isEqualTo(second.images().get(0).image().getId());
        assertThat(store.countImagesByCatalog(saved.getId(), 0)).isEqualTo(1);
        // but the reference rows differ, since the two pages describe it differently
        assertThat(first.images().get(0).reference().getId())
                .isNotEqualTo(second.images().get(0).reference().getId());
        assertThat(resourceImageRepository.findByResourceIdIn(
                List.of(first.resource().getId(), second.resource().getId()))).hasSize(2);
    }

    @Test
    void referencesCarryTheWordingAroundTheImage() {
        Catalog saved = catalogStore().save(catalog("nu"));
        CatalogDetails details = detailsOf(saved);
        ResourceRecord record = recordStore().save(details,
                pageWithImage("https://nu.com/a", "A", "bytes"), layout(details));

        assertThat(record.images().get(0).reference().getAltText()).isEqualTo("alt of A");
        assertThat(record.images().get(0).reference().getContextText()).isEqualTo("words near A");
    }

    @Test
    void loadsBackWhatItWrote() {
        Catalog saved = catalogStore().save(catalog("xi"));
        CatalogDetails details = detailsOf(saved);
        JpaResourceRecordStore store = recordStore();
        ResourceRecord written =
                store.save(details, pageWithImage("https://xi.com/a", "A", "b"), layout(details));

        ResourceRecord read = store.load(written.resource().getId()).orElseThrow();
        assertThat(read.resource().getUrl()).isEqualTo("https://xi.com/a");
        assertThat(read.images()).hasSize(1);
        assertThat(read.images().get(0).image().getContentHash()).isEqualTo("b");
    }

    @Test
    void pagesThroughAVersionForReplay() {
        Catalog saved = catalogStore().save(catalog("omicron"));
        CatalogDetails details = detailsOf(saved);
        JpaResourceRecordStore store = recordStore();
        for (int i = 0; i < 5; i++) {
            store.save(details, page("https://omicron.com/" + i, "P" + i), layout(details));
        }

        assertThat(store.load(saved.getId(), 0, 0, 2)).hasSize(2);
        assertThat(store.load(saved.getId(), 0, 4, 2)).hasSize(1);
        assertThat(store.countByCatalog(saved.getId(), 0)).isEqualTo(5);
    }

    @Test
    @DisplayName("update picks up where the last run stopped")
    void latestReferencePathIsTheMostRecentUrl() throws Exception {
        Catalog saved = catalogStore().save(catalog("pi"));
        CatalogDetails details = detailsOf(saved);
        JpaResourceRecordStore store = recordStore();

        CrawledPage first = page("https://pi.com/first", "First");
        first.setFetchedAt(new Date(1_000L));
        store.save(details, first, layout(details));

        CrawledPage last = page("https://pi.com/last", "Last");
        last.setFetchedAt(new Date(9_000L));
        store.save(details, last, layout(details));

        assertThat(store.getLatestReferencePath(saved.getId(), 0))
                .contains("https://pi.com/last");
    }

    @Test
    void versionsAreListedForTheDeleteCommand() {
        Catalog saved = catalogStore().save(catalog("rho"));
        CatalogDetails details = detailsOf(saved);
        recordStore().save(details, page("https://rho.com/a", "A"), layout(details));

        assertThat(recordStore().findVersions(saved.getId())).containsExactly(0);
    }

    @Test
    void deletingAVersionClearsAllThreeTables() {
        Catalog saved = catalogStore().save(catalog("sigma"));
        CatalogDetails details = detailsOf(saved);
        JpaResourceRecordStore store = recordStore();
        store.save(details, pageWithImage("https://sigma.com/a", "A", "b"), layout(details));

        long removed = store.deleteByCatalogAndVersion(saved.getId(), 0);

        assertThat(removed).isEqualTo(3);
        // scoped to this catalog: image rows are written in their own committed transactions, so
        // a global count would also see what the other tests in this class left behind
        assertThat(store.countByCatalog(saved.getId(), 0)).isZero();
        assertThat(store.countImagesByCatalog(saved.getId(), 0)).isZero();
        assertThat(resourceImageRepository.findByResourceId(saved.getId())).isEmpty();
    }

    @Test
    void countsImagesSeparately() {
        Catalog saved = catalogStore().save(catalog("tau"));
        CatalogDetails details = detailsOf(saved);
        recordStore().save(details, pageWithImage("https://tau.com/a", "A", "b"),
                layout(details));

        assertThat(recordStore().countImagesByCatalog(saved.getId(), 0)).isEqualTo(1);
    }

}
