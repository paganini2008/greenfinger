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

package com.github.greenfinger.core.utils;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 
 * @Description: UrlUtilsTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class UrlUtilsTest {

    @ParameterizedTest
    @CsvSource({"https://download.csdn.net, csdn", "https://www.msc.org, msc",
            "https://greatist.com, greatist", "https://m.3dmgame.com, 3dmgame",
            "http://localhost:8080/a, localhost"})
    @DisplayName("domain name is the label a human calls the site by")
    void getDomainName(String url, String expected) {
        assertThat(UrlUtils.getDomainName(url)).isEqualTo(expected);
    }

    @Test
    @DisplayName("site name keeps the full host so sibling sites cannot collide")
    void getSiteName() {
        assertThat(UrlUtils.getSiteName("https://books.toscrape.com"))
                .isEqualTo("books.toscrape.com");
        assertThat(UrlUtils.getSiteName("https://quotes.toscrape.com"))
                .isEqualTo("quotes.toscrape.com");
        assertThat(UrlUtils.getSiteName("https://www.msc.org")).isEqualTo("msc.org");
        assertThat(UrlUtils.getSiteName("garbage")).isEmpty();
    }

    @Test
    @DisplayName("two sibling sites share a domain name but not a site name")
    void siblingSitesDoNotCollide() {
        assertThat(UrlUtils.getDomainName("https://books.toscrape.com"))
                .isEqualTo(UrlUtils.getDomainName("https://quotes.toscrape.com"));
        assertThat(UrlUtils.getSiteName("https://books.toscrape.com"))
                .isNotEqualTo(UrlUtils.getSiteName("https://quotes.toscrape.com"));
    }

    @Test
    void getDomainNameOfGarbage() {
        assertThat(UrlUtils.getDomainName("not a url")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"https://a.com, https", "http://a.com, http", "a.com, https"})
    void getProtocol(String url, String expected) {
        assertThat(UrlUtils.getProtocol(url)).isEqualTo(expected);
    }

    @Test
    @DisplayName("host and scheme are case folded")
    void normalizeFoldsCase() {
        assertThat(UrlUtils.normalize("HTTPS://WWW.Example.COM/Path"))
                .isEqualTo("https://www.example.com/Path");
    }

    @Test
    @DisplayName("default ports are dropped so they do not split one page into two")
    void normalizeDropsDefaultPort() {
        assertThat(UrlUtils.normalize("https://a.com:443/x")).isEqualTo("https://a.com/x");
        assertThat(UrlUtils.normalize("http://a.com:80/x")).isEqualTo("http://a.com/x");
        assertThat(UrlUtils.normalize("http://a.com:8080/x")).isEqualTo("http://a.com:8080/x");
    }

    @Test
    void normalizeDropsFragmentAndTrailingSlash() {
        assertThat(UrlUtils.normalize("https://a.com/x/#section")).isEqualTo("https://a.com/x");
        assertThat(UrlUtils.normalize("https://a.com/")).isEqualTo("https://a.com/");
    }

    @Test
    @DisplayName("tracking parameters are stripped; real parameters survive and are ordered")
    void normalizeStripsTrackingParameters() {
        assertThat(UrlUtils.normalize("https://a.com/x?utm_source=nl&id=7&fbclid=abc"))
                .isEqualTo("https://a.com/x?id=7");
        assertThat(UrlUtils.normalize("https://a.com/x?b=2&a=1"))
                .isEqualTo("https://a.com/x?a=1&b=2");
    }

    @Test
    @DisplayName("the same article behind a campaign link normalises to one url")
    void normalizeCollapsesCampaignVariants() {
        String plain = UrlUtils.normalize("https://a.com/story");
        String campaign =
                UrlUtils.normalize("https://a.com/story/?utm_campaign=spring&utm_medium=email");
        assertThat(campaign).isEqualTo(plain);
    }

    @Test
    void normalizeLeavesUnparseableInputAlone() {
        assertThat(UrlUtils.normalize("::::")).isEqualTo("::::");
        assertThat(UrlUtils.normalize("  ")).isEqualTo("  ");
    }


    @Test
    @org.junit.jupiter.api.DisplayName("a query string usually decides the page, so it counts")
    void differentParametersAreDifferentPages() {
        assertThat(UrlUtils.normalize("https://a.com/x?id=1"))
                .isNotEqualTo(UrlUtils.normalize("https://a.com/x?id=2"));
        assertThat(UrlUtils.normalize("https://a.com/x"))
                .isNotEqualTo(UrlUtils.normalize("https://a.com/x?page=2"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("the same parameters in another order are the same page")
    void parameterOrderDoesNotMatter() {
        assertThat(UrlUtils.normalize("https://a.com/x?b=1&c=2"))
                .isEqualTo(UrlUtils.normalize("https://a.com/x?c=2&b=1"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("tracking parameters name a visit, not a page")
    void trackingParametersAreDropped() {
        String plain = UrlUtils.normalize("https://a.com/x");
        assertThat(UrlUtils.normalize("https://a.com/x?utm_source=news")).isEqualTo(plain);
        assertThat(UrlUtils.normalize("https://a.com/x?gclid=abc&fbclid=def")).isEqualTo(plain);
        assertThat(UrlUtils.normalize("https://a.com/x?ref=twitter")).isEqualTo(plain);
        // a real parameter alongside a tracking one survives on its own
        assertThat(UrlUtils.normalize("https://a.com/x?id=7&utm_source=news"))
                .isEqualTo(UrlUtils.normalize("https://a.com/x?id=7"));
    }

    @Test
    void trailingSlashAndCaseAndDefaultPortsAreTheSamePage() {
        String plain = UrlUtils.normalize("https://a.com/x");
        assertThat(UrlUtils.normalize("https://a.com/x/")).isEqualTo(plain);
        assertThat(UrlUtils.normalize("HTTPS://A.COM/x")).isEqualTo(plain);
        assertThat(UrlUtils.normalize("https://a.com:443/x")).isEqualTo(plain);
        assertThat(UrlUtils.normalize("http://a.com:80/x"))
                .isEqualTo(UrlUtils.normalize("http://a.com/x"));
    }


    @org.junit.jupiter.api.DisplayName("RFC 3986 folding, which the standard says is the same page")
    @Test
    void foldsRelativePathSegments() {
        assertThat(UrlUtils.normalize("https://a.com/a/./b/../c"))
                .isEqualTo(UrlUtils.normalize("https://a.com/a/c"));
    }

    @Test
    void decodesEscapesThatNeverNeededEscaping() {
        assertThat(UrlUtils.normalize("https://a.com/%7Euser/"))
                .isEqualTo(UrlUtils.normalize("https://a.com/~user"));
    }

    @Test
    void foldsRepeatedSlashes() {
        assertThat(UrlUtils.normalize("https://a.com//double//slash"))
                .isEqualTo(UrlUtils.normalize("https://a.com/double/slash"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("something unparseable comes back untouched, not as null")
    void survivesRubbish() {
        assertThat(UrlUtils.normalize("not a url at all")).isNotNull();
        assertThat(UrlUtils.normalize("")).isEmpty();
        assertThat(UrlUtils.normalize(null)).isNull();
    }

}
