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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

/**
 * 
 * @Description: UrlPathPatternsTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class UrlPathPatternsTest {

    private final PathMatcher pathMatcher = new AntPathMatcher();

    private boolean matchesAny(String pattern, String url) {
        return UrlPathPatterns.expand(pattern).stream().anyMatch(p -> pathMatcher.match(p, url));
    }

    @Test
    @DisplayName("a bare domain pattern covers the domain and its subdomains")
    void subdomainShorthand() {
        assertThat(matchesAny("**.google.com", "https://www.google.com/a/b")).isTrue();
        assertThat(matchesAny("**.google.com", "https://news.google.com/")).isTrue();
        assertThat(matchesAny("**.google.com", "https://google.com/x")).isTrue();
        assertThat(matchesAny("**.google.com", "http://google.com/x")).isTrue();
    }

    @Test
    @DisplayName("a different site is not matched")
    void subdomainShorthandRejectsOtherSites() {
        assertThat(matchesAny("**.google.com", "https://www.bing.com/a")).isFalse();
    }

    @Test
    @DisplayName("a path shorthand keeps the crawl inside that path")
    void pathShorthand() {
        assertThat(matchesAny("www.google.com/a/**", "https://www.google.com/a/b")).isTrue();
        assertThat(matchesAny("www.google.com/a/**", "https://www.google.com/z/b")).isFalse();
    }

    @Test
    @DisplayName("a fully qualified 1.x pattern is left alone and still works")
    void legacyPatternUnchanged() {
        assertThat(UrlPathPatterns.expand("https://**.msc.**/**"))
                .containsExactly("https://**.msc.**/**");
        assertThat(matchesAny("https://**.msc.**/**", "https://www.msc.org/a")).isTrue();
    }

    @Test
    void blankPatternExpandsToNothing() {
        assertThat(UrlPathPatterns.expand("  ")).isEmpty();
        assertThat(UrlPathPatterns.expandAll(null)).isEmpty();
    }

    @Test
    @DisplayName("a shorthand pattern with a path still covers the bare domain")
    void subdomainShorthandWithPath() {
        assertThat(matchesAny("**.google.com/a/**", "https://news.google.com/a/b")).isTrue();
        assertThat(matchesAny("**.google.com/a/**", "https://google.com/a/b")).isTrue();
    }

    @Test
    void expandAllDeduplicates() {
        assertThat(UrlPathPatterns.expandAll(List.of("**.a.com", "**.a.com"))).hasSize(2);
    }

    @Test
    void defaultPathPatternDropsWww() {
        assertThat(UrlPathPatterns.defaultPathPattern("https://www.google.com"))
                .isEqualTo("**.google.com");
        assertThat(UrlPathPatterns.defaultPathPattern("https://news.bbc.co.uk"))
                .isEqualTo("**.news.bbc.co.uk");
        assertThat(UrlPathPatterns.defaultPathPattern("garbage")).isEmpty();
    }

    @Test
    @DisplayName("a site on its own port keeps the port, or it would match none of its own links")
    void defaultPathPatternKeepsThePort() {
        assertThat(UrlPathPatterns.defaultPathPattern("http://localhost:18099/"))
                .isEqualTo("**.localhost:18099");
        // the default port is not written in the links either, so it is not written here
        assertThat(UrlPathPatterns.defaultPathPattern("https://www.google.com:443"))
                .isEqualTo("**.google.com");
    }

    @Test
    @DisplayName("and the pattern it produces matches the pages of that site")
    void theDefaultPatternMatchesItsOwnSite() {
        AntPathMatcher matcher = new AntPathMatcher();
        List<String> patterns =
                UrlPathPatterns.expand(UrlPathPatterns.defaultPathPattern("http://localhost:18099/"));

        assertThat(patterns).anyMatch(p -> matcher.match(p, "http://localhost:18099/a.html"));
        assertThat(patterns).anyMatch(p -> matcher.match(p, "http://localhost:18099/deep/b.html"));
        assertThat(patterns).noneMatch(p -> matcher.match(p, "http://localhost:19000/a.html"));
    }

}
