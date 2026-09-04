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

package com.github.greenfinger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.test.context.TestPropertySource;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.catalog.CatalogDetailsNotFoundException;
import java.util.ArrayList;
import java.util.List;
import com.github.greenfinger.core.engine.CrawlRegistry;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.ContentMode;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.output.vector.EmbeddingProperties;
import com.github.greenfinger.record.CatalogRepository;
import com.github.greenfinger.record.JpaCatalogStore;

/**
 * The service that turns "a url" into a complete catalog definition.
 *
 * <p>
 * Against the real store, on H2. There is one {@link CatalogStore} implementation and there will
 * only ever be one -- a catalog always lives in the database -- so a hand written in-memory double
 * would only be a second set of rules to keep in step, and the two had already drifted: the double
 * filled in three of the defaults the JPA store fills in, and quietly not the rest.
 * 
 * @Description: CatalogAdminServiceTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@DataJpaTest
@EntityScan(basePackages = "com.github.greenfinger.core.model")
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
class CatalogAdminServiceTest {

    @Autowired
    private CatalogRepository catalogRepository;

    private CatalogStore catalogStore;
    private CatalogAdminService service;
    private CrawlRegistry crawlRegistry;

    @BeforeEach
    void setUp() {
        catalogStore = new JpaCatalogStore(catalogRepository);
        OutputProperties outputProperties = new OutputProperties();
        crawlRegistry = new CrawlRegistry();
        service = new CatalogAdminService(catalogStore, new WebCrawlerProperties(),
                outputProperties,
                new OutputFactory(outputProperties, new EmbeddingProperties()), crawlRegistry);
    }

    @Test
    @DisplayName("deleting a definition stops its crawl first, so no permit is left held")
    void deleteStopsARunningCrawl() {
        // a crawl reads what to fetch next from the frontier, not from the catalog table, so
        // removing the row underneath it does not stop it: it keeps fetching for a catalog that no
        // longer exists, holding this process's one permit, invisible in the running list because
        // the row it was listed by is gone. Every later crawl on the node is then refused by
        // something nobody can see or name.
        Catalog saved = service.save(withUrl("https://a.example.com"));
        List<String> interrupted = new ArrayList<>();
        CatalogAdminService withRegistry = new CatalogAdminService(catalogStore,
                new WebCrawlerProperties(), new OutputProperties(),
                new OutputFactory(new OutputProperties(), new EmbeddingProperties()),
                new CrawlRegistry() {
                    @Override
                    public boolean interrupt(String catalogId) {
                        interrupted.add(catalogId);
                        return true;
                    }
                });

        assertThat(withRegistry.delete(saved.getId())).isTrue();

        assertThat(interrupted).containsExactly(saved.getId());
        assertThat(catalogStore.findById(saved.getId())).isEmpty();
    }

    private Catalog withUrl(String url) {
        Catalog catalog = new Catalog();
        catalog.setUrl(url);
        return catalog;
    }

    @Test
    @DisplayName("a url alone is a complete definition")
    void fillsInEverythingElse() {
        Catalog saved = service.save(withUrl("https://books.toscrape.com"));

        assertThat(saved.getId()).isNotNull();
        assertThat(UUID.fromString(saved.getId()).version()).isEqualTo(7);
        assertThat(saved.getName()).isEqualTo("toscrape");
        assertThat(saved.getCat()).isEqualTo("default");
        assertThat(saved.getPathPattern()).isNotBlank();
        assertThat(saved.getRunningState()).isEqualTo("none");
        assertThat(saved.getMaxVersions()).isEqualTo(10);
    }

    @Test
    @DisplayName("start url is a prefix as well as a seed, so it defaults to the whole site")
    void startUrlDefaultsToTheSite() {
        Catalog saved = service.save(withUrl("https://books.toscrape.com"));
        assertThat(saved.getStartUrl()).isEqualTo("https://books.toscrape.com");
    }

    @Test
    void keepsAnExplicitStartUrl() {
        Catalog catalog = withUrl("https://example.com");
        catalog.setStartUrl("https://example.com/docs");
        assertThat(service.save(catalog).getStartUrl()).isEqualTo("https://example.com/docs");
    }

    @Test
    void defaultsToFileOutputOnly() {
        assertThat(service.save(withUrl("https://example.com")).getOutputTypes())
                .containsExactly(OutputType.FILE);
    }

    @Test
    void keepsAnExplicitOutputCombination() {
        Catalog catalog = withUrl("https://example.com");
        catalog.setOutputTypes(Set.of(OutputType.INDEX, OutputType.VECTOR));
        assertThat(service.save(catalog).getOutputTypes()).contains(OutputType.FILE,
                OutputType.INDEX, OutputType.VECTOR);
    }

    @Test
    void defaultsToCarryingImagesDownstream() {
        assertThat(service.save(withUrl("https://example.com")).getContentMode())
                .isEqualTo(ContentMode.TEXT_IMAGE);
    }

    @Test
    void refusesACatalogWithNoUrl() {
        assertThatThrownBy(() -> service.save(new Catalog()))
                .isInstanceOf(WebCrawlerException.class).hasMessageContaining("needs a url");
    }

    @Test
    @DisplayName("a name is what a person types, an id is what the machine passes around")
    void findsByEither() {
        Catalog saved = service.save(withUrl("https://example.com"));

        assertThat(service.find("example")).isPresent();
        assertThat(service.find(saved.getId())).isPresent();
        assertThat(service.find("nothing")).isEmpty();
        assertThat(service.find(null)).isEmpty();
    }

    @Test
    void requireReportsWhatIsMissing() {
        assertThatThrownBy(() -> service.require("ghost"))
                .isInstanceOf(CatalogDetailsNotFoundException.class).hasMessageContaining("ghost");
    }

    @Test
    void listsAndCategorises() {
        service.save(withUrl("https://a.com"));
        Catalog other = withUrl("https://b.com");
        other.setCat("blogs");
        service.save(other);

        assertThat(service.findAll()).hasSize(2);
        assertThat(service.findAllCategories()).containsExactly("blogs", "default");
    }

    @Test
    void deletesTheDefinition() {
        service.save(withUrl("https://example.com"));
        assertThat(service.delete("example")).isTrue();
        assertThat(service.find("example")).isEmpty();
    }

    @Test
    void runningCatalogsAreVisible() {
        Catalog saved = service.save(withUrl("https://example.com"));
        catalogStore.setRunningState(saved.getId(), "crawl");
        assertThat(service.findRunning()).hasSize(1);
    }

    @Test
    void savingTwiceUpdatesRatherThanDuplicates() {
        Catalog saved = service.save(withUrl("https://example.com"));
        saved.setMaxFetchSize(500);
        service.save(saved);

        assertThat(service.findAll()).hasSize(1);
        assertThat(service.require("example").getMaxFetchSize()).isEqualTo(500);
    }

}
