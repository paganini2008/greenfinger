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

package com.github.greenfinger.core.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.CatalogFixtures;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.ContentMode;
import com.github.greenfinger.core.model.ExtractorType;
import com.github.greenfinger.core.model.OutputType;

/**
 * 
 * @Description: CatalogDetailsImplTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
class CatalogDetailsImplTest {

    @Test
    void exposesTheStoredValues() {
        CatalogDetails details = CatalogFixtures.details();
        assertThat(details.getId()).isEqualTo(CatalogFixtures.CATALOG_ID);
        assertThat(details.getName()).isEqualTo("example");
        assertThat(details.getUrl()).isEqualTo("https://www.example.com");
        assertThat(details.getCategory()).isEqualTo("tech");
        assertThat(details.getMaxFetchSize()).isEqualTo(100);
        assertThat(details.getMaxFetchDepth()).isEqualTo(2);
    }

    @Test
    void fallsBackToTheConfiguredDefaults() {
        Catalog catalog = CatalogFixtures.catalog();
        catalog.setMaxFetchSize(null);
        catalog.setDepth(null);
        catalog.setPageEncoding(null);
        catalog.setExtractorType(null);
        catalog.setMaxRetryCount(null);
        catalog.setFetchInterval(null);
        catalog.setDuration(null);
        catalog.setMaxVersions(null);

        WebCrawlerProperties properties = new WebCrawlerProperties();
        CatalogDetails details = new CatalogDetailsImpl(catalog, properties);

        assertThat(details.getMaxFetchSize()).isEqualTo(properties.getDefaultMaxFetchSize());
        assertThat(details.getMaxFetchDepth()).isEqualTo(properties.getDefaultMaxFetchDepth());
        assertThat(details.getPageEncoding()).isEqualTo(properties.getDefaultPageEncoding());
        assertThat(details.getExtractor())
                .isEqualTo(ExtractorType.of(properties.getDefaultExtractor()));
        assertThat(details.getMaxRetryCount()).isEqualTo(properties.getDefaultMaxRetryCount());
        assertThat(details.getFetchInterval()).isEqualTo(properties.getDefaultFetchInterval());
        assertThat(details.getFetchDuration()).isEqualTo(properties.getDefaultFetchDuration());
        assertThat(details.getMaxVersions()).isEqualTo(properties.getDefaultMaxVersions());
    }

    @Test
    void splitsThePatternLists() {
        Catalog catalog = CatalogFixtures.catalog();
        catalog.setPathPattern("**.example.com,**.example.org");
        catalog.setExcludedPathPattern("**/admin/**");
        CatalogDetails details = CatalogFixtures.details(catalog);

        assertThat(details.getPathPatterns()).containsExactly("**.example.com",
                "**.example.org");
        assertThat(details.getExcludedPathPatterns()).containsExactly("**/admin/**");
    }

    @Test
    void emptyPatternListsRatherThanNull() {
        Catalog catalog = CatalogFixtures.catalog();
        catalog.setExcludedPathPattern(null);
        catalog.setUrlPathAcceptor(null);
        CatalogDetails details = CatalogFixtures.details(catalog);

        assertThat(details.getExcludedPathPatterns()).isEmpty();
        assertThat(details.getUrlPathAcceptors()).isEmpty();
    }

    @Test
    void fileOutputIsAlwaysPresent() {
        Catalog catalog = CatalogFixtures.catalog();
        catalog.setOutputTypes(Set.of(OutputType.INDEX));
        CatalogDetails details = CatalogFixtures.details(catalog);

        assertThat(details.getOutputTypes()).contains(OutputType.FILE, OutputType.INDEX);
        assertThat(details.hasOutput(OutputType.FILE)).isTrue();
        assertThat(details.hasOutput(OutputType.VECTOR)).isFalse();
    }

    @Test
    void catalogVersionJoinsTheIdAndTheVersion() {
        Catalog catalog = CatalogFixtures.catalog();
        catalog.setIndexVersion(7);
        assertThat(CatalogFixtures.details(catalog).getCatalogVersion())
                .isEqualTo(CatalogFixtures.CATALOG_ID + ":7");
    }

    @Test
    void searchVersionIsMinusOneUntilSomethingFinishes() {
        assertThat(CatalogFixtures.details().getSearchVersion()).isEqualTo(-1);
    }

    @Test
    void contentModeDefaultsToTextAndImage() {
        Catalog catalog = CatalogFixtures.catalog();
        catalog.setContentMode(null);
        assertThat(CatalogFixtures.details(catalog).getContentMode())
                .isEqualTo(ContentMode.TEXT_IMAGE);
    }

    @Test
    void countingTypeFallsBackToSavedResources() {
        Catalog catalog = CatalogFixtures.catalog();
        catalog.setCountingType(null);
        assertThat(CatalogFixtures.details(catalog).getCountingType())
                .isEqualTo(CountingType.SAVED_RESOURCE_COUNT);
    }

}
