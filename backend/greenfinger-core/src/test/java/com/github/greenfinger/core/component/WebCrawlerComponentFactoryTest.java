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

package com.github.greenfinger.core.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.github.greenfinger.core.CatalogFixtures;
import com.github.greenfinger.core.WebCrawlerExtractorProperties;
import com.github.greenfinger.core.WebCrawlerConstants;
import com.github.greenfinger.core.component.extractor.Extractor;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.acceptor.MaxFetchDepthUrlPathAcceptor;
import com.github.greenfinger.core.component.acceptor.PathMatcherUrlPathAcceptor;
import com.github.greenfinger.core.component.acceptor.RobotRuleUrlPathAcceptor;
import com.github.greenfinger.core.component.dedup.ContentDedupFilter;
import com.github.greenfinger.core.component.dedup.RocksDbUrlPathFilter;
import com.github.greenfinger.core.component.dedup.Sha256ContentDedupFilter;
import com.github.greenfinger.core.component.dedup.SimHashContentDedupFilter;
import com.github.greenfinger.core.component.extractor.RetryableExtractor;
import com.github.greenfinger.core.component.extractor.ThreadWaitExtractor;
import com.github.greenfinger.core.component.completion.FetchDurationCompletionChecker;
import com.github.greenfinger.core.component.completion.MaxFetchSizeCompletionChecker;
import com.github.greenfinger.core.engine.RocksDbCrawlFrontier;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.ExtractorType;

/**
 * 
 * @Description: WebCrawlerComponentFactoryTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class WebCrawlerComponentFactoryTest {

    @TempDir
    Path state;

    private WebCrawlerProperties webCrawlerProperties;
    private DefaultWebCrawlerComponentFactory factory;

    @BeforeEach
    void setUp() {
        webCrawlerProperties = new WebCrawlerProperties();
        webCrawlerProperties.setFrontierDirectory(state.resolve("frontier").toString());
        webCrawlerProperties.getDedup().getUrl().setDirectory(state.resolve("url").toString());
        webCrawlerProperties.getDedup().getContent()
                .setDirectory(state.resolve("content").toString());
        factory = new DefaultWebCrawlerComponentFactory(webCrawlerProperties,
                new WebCrawlerExtractorProperties());
    }

    private CatalogDetails withExtractor(String extractor) {
        Catalog catalog = CatalogFixtures.catalog();
        catalog.setExtractorType(ExtractorType.of(extractor));
        return CatalogFixtures.details(catalog);
    }

    @Test
    void buildsBothCompletionCheckers() {
        assertThat(factory.getCompletionCheckers(CatalogFixtures.details()))
                .hasAtLeastOneElementOfType(FetchDurationCompletionChecker.class)
                .hasAtLeastOneElementOfType(MaxFetchSizeCompletionChecker.class);
    }

    @Test
    @DisplayName("robots is always in the chain, whatever the catalog says")
    void alwaysIncludesRobotRules() {
        assertThat(factory.getUrlPathAcceptors(CatalogFixtures.details()))
                .hasAtLeastOneElementOfType(RobotRuleUrlPathAcceptor.class)
                .hasAtLeastOneElementOfType(MaxFetchDepthUrlPathAcceptor.class)
                .hasAtLeastOneElementOfType(PathMatcherUrlPathAcceptor.class);
    }

    @Test
    void ignoresACustomAcceptorThatCannotBeLoaded() {
        Catalog catalog = CatalogFixtures.catalog();
        catalog.setUrlPathAcceptor("com.example.NoSuchAcceptor");
        // the two boundary acceptors, robots, depth and the path matcher; the bad one is dropped
        assertThat(factory.getUrlPathAcceptors(CatalogFixtures.details(catalog))).hasSize(5);
    }

    @Test
    @DisplayName("every engine name the catalog can carry builds something")
    void buildsEveryExtractor() {
        for (String name : new String[] {"default", "restclient", "resttemplate", "htmlunit",
                "playwright", "selenium"}) {
            assertThat(factory.getExtractor(withExtractor(name))).isNotNull();
        }
    }

    @Test
    @DisplayName("an unknown extractor is refused where it is typed, not where it is built")
    void rejectsAnUnknownExtractor() {
        // it used to reach here as a string and fail when the crawl went to build its extractor,
        // which is after the catalog was saved and the run launched
        assertThatThrownBy(() -> withExtractor("lynx"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lynx")
                .hasMessageContaining("adaptive").hasMessageContaining("selenium");
    }

    @Test
    @DisplayName("pacing always wraps the engine, retry only when the catalog asks for it")
    void wrapsWithDecorators() {
        Catalog noRetry = CatalogFixtures.catalog();
        noRetry.setMaxRetryCount(0);
        assertThat(factory.getExtractor(CatalogFixtures.details(noRetry)))
                .isInstanceOf(ThreadWaitExtractor.class);

        Catalog withRetry = CatalogFixtures.catalog();
        withRetry.setMaxRetryCount(3);
        assertThat(factory.getExtractor(CatalogFixtures.details(withRetry)))
                .isInstanceOf(RetryableExtractor.class);
    }

    @Test
    void buildsTheRocksDbUrlFilter() {
        assertThat(factory.getExistingUrlPathFilter(CatalogFixtures.details()))
                .isInstanceOf(RocksDbUrlPathFilter.class);
    }

    @Test
    void rejectsAnUnknownUrlFilter() {
        Catalog catalog = CatalogFixtures.catalog();
        catalog.setUrlPathFilter("bloomfilter");
        assertThatThrownBy(
                () -> factory.getExistingUrlPathFilter(CatalogFixtures.details(catalog)))
                        .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void buildsEitherContentDedupFilter() {
        webCrawlerProperties.getDedup().getContent().setType("sha256");
        assertThat(factory.getContentDedupFilter(CatalogFixtures.details()))
                .isInstanceOf(Sha256ContentDedupFilter.class);

        webCrawlerProperties.getDedup().getContent().setType("simhash");
        assertThat(factory.getContentDedupFilter(CatalogFixtures.details()))
                .isInstanceOf(SimHashContentDedupFilter.class);

        webCrawlerProperties.getDedup().getContent().setEnabled(false);
        assertThat(factory.getContentDedupFilter(CatalogFixtures.details()))
                .isInstanceOf(ContentDedupFilter.NoOp.class);
    }

    @Test
    void rejectsAnUnknownContentDedupFilter() {
        webCrawlerProperties.getDedup().getContent().setType("minhash");
        assertThatThrownBy(() -> factory.getContentDedupFilter(CatalogFixtures.details()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void buildsTheStateManagerAndFrontier() {
        assertThat(factory.getGlobalStateManager(CatalogFixtures.details(), true)).isNotNull();
        assertThat(factory.getCrawlFrontier(CatalogFixtures.details()))
                .isInstanceOf(RocksDbCrawlFrontier.class);
    }

    @Test
    @DisplayName("stores are scoped per catalog and version, so a rebuild starts clean")
    void scopesStoresByCatalogAndVersion() {
        Catalog v1 = CatalogFixtures.catalog();
        v1.setIndexVersion(1);
        RocksDbUrlPathFilter versionZero =
                (RocksDbUrlPathFilter) factory.getExistingUrlPathFilter(CatalogFixtures.details());
        RocksDbUrlPathFilter versionOne = (RocksDbUrlPathFilter) factory
                .getExistingUrlPathFilter(CatalogFixtures.details(v1));
        assertThat(versionZero).isNotSameAs(versionOne);
    }


    @Test
    @DisplayName("adaptive is the default, and is safe even with no browser on the classpath")
    void adaptiveIsTheDefault() {
        assertThat(new WebCrawlerProperties().getDefaultExtractor())
                .isEqualTo(WebCrawlerConstants.ENGINE_ADAPTIVE);

        Catalog catalog = CatalogFixtures.catalog();
        catalog.setExtractorType(ExtractorType.ADAPTIVE);

        // the browser engines are optional dependencies; whichever way this build was assembled,
        // asking for adaptive must produce something that can crawl rather than an exception
        Extractor extractor = factory.getExtractor(CatalogFixtures.details(catalog));
        assertThat(extractor).isNotNull();
    }

    @Test
    @DisplayName("adaptive needs a browser to fall back to, so restclient is not one")
    void adaptiveRefusesRestClientAsItsBrowser() {
        com.github.greenfinger.core.WebCrawlerExtractorProperties properties =
                new com.github.greenfinger.core.WebCrawlerExtractorProperties();
        properties.getAdaptive().setBrowser(WebCrawlerConstants.ENGINE_RESTCLIENT);

        Catalog catalog = CatalogFixtures.catalog();
        catalog.setExtractorType(ExtractorType.ADAPTIVE);

        assertThatThrownBy(() -> new DefaultWebCrawlerComponentFactory(new WebCrawlerProperties(),
                properties).getExtractor(CatalogFixtures.details(catalog)))
                        .isInstanceOf(UnsupportedOperationException.class)
                        .hasMessageContaining("needs a browser");
    }

}
