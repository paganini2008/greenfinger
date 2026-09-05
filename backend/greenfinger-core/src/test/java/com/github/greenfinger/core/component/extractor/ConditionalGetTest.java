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

package com.github.greenfinger.core.component.extractor;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.TestSite;
import com.github.greenfinger.core.WebCrawlerExtractorProperties;
import com.github.greenfinger.core.utils.BeanLifeCycleUtils;

/**
 * Asking the site whether a page has changed, instead of downloading it to find out.
 *
 * <p>
 * This only ever applies to a merge, and what it saves is the download and the parse -- an
 * unchanged page was already being written nowhere. On a site of any size that is the difference
 * between a merge that transfers the whole site and one that transfers what moved.
 *
 * @Description: ConditionalGetTest
 * @Author: Fred Feng
 * @Date: 01/09/2026
 * @Version 2.0.0
 */
class ConditionalGetTest {

    private static final String PAGE = "<html><body><p>The page as it stood.</p></body></html>";

    private TestSite site;
    private RestClientExtractor extractor;

    @BeforeEach
    void setUp() throws Exception {
        site = new TestSite();
        site.conditional("/page", PAGE, "\"v1\"");
        extractor = new RestClientExtractor(new WebCrawlerExtractorProperties());
        BeanLifeCycleUtils.afterPropertiesSet(extractor);
    }

    @AfterEach
    void tearDown() {
        BeanLifeCycleUtils.destroyQuietly(extractor);
        site.close();
    }

    private FetchedPage fetch(ConditionalGet conditions) throws Exception {
        return extractor.fetch(null, null, site.url("/page"), StandardCharsets.UTF_8, null,
                conditions);
    }

    @Test
    @DisplayName("a first fetch asks nothing and comes back with the validator to use next time")
    void carriesTheValidatorBack() throws Exception {
        FetchedPage fetched = fetch(ConditionalGet.NONE);

        assertThat(fetched.notModified()).isFalse();
        assertThat(fetched.html()).contains("The page as it stood");
        assertThat(fetched.etag()).isEqualTo("\"v1\"");
        assertThat(site.lastIfNoneMatch()).isEmpty();
    }

    @Test
    @DisplayName("offering it back gets a 304 and no body at all")
    void asksAndIsToldNothingChanged() throws Exception {
        String etag = fetch(ConditionalGet.NONE).etag();

        FetchedPage again = fetch(ConditionalGet.of(etag, null));

        assertThat(site.lastIfNoneMatch()).isEqualTo("\"v1\"");
        assertThat(again.notModified()).isTrue();
        assertThat(again.html()).isEmpty();
        // carried forward, so the merge after this one can ask the same question
        assertThat(again.etag()).isEqualTo("\"v1\"");
    }

    @Test
    @DisplayName("a stale validator gets the page, which is the point of asking rather than assuming")
    void aChangedPageStillArrives() throws Exception {
        FetchedPage fetched = fetch(ConditionalGet.of("\"v0\"", null));

        assertThat(fetched.notModified()).isFalse();
        assertThat(fetched.html()).contains("The page as it stood");
    }

    @Test
    @DisplayName("a site that publishes no validator is fetched as it always was")
    void aSiteWithoutValidatorsIsUnaffected() throws Exception {
        site.html("/plain", PAGE);

        FetchedPage fetched = extractor.fetch(null, null, site.url("/plain"),
                StandardCharsets.UTF_8, null, ConditionalGet.NONE);

        assertThat(fetched.notModified()).isFalse();
        assertThat(fetched.etag()).isNull();
        assertThat(fetched.html()).contains("The page as it stood");
    }

    @Test
    @DisplayName("the plain call still works, since three engines and every first crawl use it")
    void theUnconditionalCallIsUnchanged() throws Exception {
        assertThat(extractor.extractHtml(null, null, site.url("/page"), StandardCharsets.UTF_8,
                null)).contains("The page as it stood");
    }

    @Test
    void blankValidatorsAreNoValidators() {
        assertThat(ConditionalGet.of(null, null)).isSameAs(ConditionalGet.NONE);
        assertThat(ConditionalGet.of("  ", "")).isSameAs(ConditionalGet.NONE);
        assertThat(ConditionalGet.NONE.isEmpty()).isTrue();
        assertThat(ConditionalGet.of("\"v1\"", null).isEmpty()).isFalse();
    }

    @Test
    @DisplayName("an engine that cannot ask reports no validators rather than inventing them")
    void anEngineThatCannotAskDegrades() throws Exception {
        Extractor plain = (catalogDetails, referUrl, url, encoding, task) -> PAGE;

        FetchedPage fetched = plain.fetch(null, null, "https://example.com",
                StandardCharsets.UTF_8, null, ConditionalGet.of("\"v1\"", null));

        assertThat(fetched.notModified()).isFalse();
        assertThat(fetched.html()).isEqualTo(PAGE);
        assertThat(fetched.etag()).isNull();
    }

}
