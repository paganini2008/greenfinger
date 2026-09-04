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

package com.github.greenfinger.core.component.acceptor;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.CatalogFixtures;
import com.github.greenfinger.core.TestSite;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawlTask;
import com.github.greenfinger.core.model.Catalog;

/**
 * 
 * @Description: UrlPathAcceptorTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class UrlPathAcceptorTest {

    private CrawlTask taskAtDepth(int depth) {
        CrawlTask task = CrawlTask.seed("cat-1", CrawlTask.ACTION_CRAWL, "https://www.example.com",
                "https://www.example.com", "test", "UTF-8", 0);
        task.setDepth(depth);
        return task;
    }

    @Test
    @DisplayName("links inside the configured pattern are followed, others are not")
    void pathMatcherKeepsTheCrawlInsideTheSite() {
        CatalogDetails details = CatalogFixtures.details();
        PathMatcherUrlPathAcceptor acceptor = new PathMatcherUrlPathAcceptor(details);

        assertThat(acceptor.accept(details, details.getUrl(), "https://www.example.com/a",
                taskAtDepth(0))).isTrue();
        assertThat(acceptor.accept(details, details.getUrl(), "https://news.example.com/a",
                taskAtDepth(0))).isTrue();
        assertThat(acceptor.accept(details, details.getUrl(), "https://evil.com/a",
                taskAtDepth(0))).isFalse();
    }

    @Test
    @DisplayName("an exclusion beats an inclusion")
    void exclusionsWin() {
        Catalog catalog = CatalogFixtures.catalog();
        catalog.setExcludedPathPattern("**.example.com/private/**");
        CatalogDetails details = CatalogFixtures.details(catalog);
        PathMatcherUrlPathAcceptor acceptor = new PathMatcherUrlPathAcceptor(details);

        assertThat(acceptor.accept(details, details.getUrl(), "https://www.example.com/public/a",
                taskAtDepth(0))).isTrue();
        assertThat(acceptor.accept(details, details.getUrl(), "https://www.example.com/private/a",
                taskAtDepth(0))).isFalse();
    }

    @Test
    @DisplayName("with no pattern at all, the crawl stays under the site url")
    void withoutPatternsFallsBackToThePrefix() {
        Catalog catalog = CatalogFixtures.catalog();
        catalog.setPathPattern(null);
        CatalogDetails details = CatalogFixtures.details(catalog);
        PathMatcherUrlPathAcceptor acceptor = new PathMatcherUrlPathAcceptor(details);

        assertThat(acceptor.accept(details, "https://www.example.com",
                "https://www.example.com/a", taskAtDepth(0))).isTrue();
        assertThat(acceptor.accept(details, "https://www.example.com", "https://other.com/a",
                taskAtDepth(0))).isFalse();
    }

    @Test
    @DisplayName("depth is the real link distance, not a count of slashes in the url")
    void maxFetchDepthUsesTheTaskDepth() {
        CatalogDetails details = CatalogFixtures.details();
        MaxFetchDepthUrlPathAcceptor acceptor = new MaxFetchDepthUrlPathAcceptor();

        // the fixture allows two levels, so a link found at depth 1 is the last one followed
        assertThat(acceptor.accept(details, details.getUrl(), "https://www.example.com/deep/a/b/c",
                taskAtDepth(0))).isTrue();
        assertThat(acceptor.accept(details, details.getUrl(), "https://www.example.com/a",
                taskAtDepth(1))).isTrue();
        assertThat(acceptor.accept(details, details.getUrl(), "https://www.example.com/a",
                taskAtDepth(2))).isFalse();
    }

    @Test
    void negativeDepthMeansUnlimited() {
        Catalog catalog = CatalogFixtures.catalog();
        catalog.setDepth(-1);
        CatalogDetails details = CatalogFixtures.details(catalog);

        assertThat(new MaxFetchDepthUrlPathAcceptor().accept(details, details.getUrl(),
                "https://www.example.com/a", taskAtDepth(99))).isTrue();
    }

    @Test
    @DisplayName("a disallowed path is refused, an allowed one is not")
    void robotRulesAreHonoured() throws Exception {
        try (TestSite site = new TestSite()) {
            site.text("/robots.txt", "text/plain", "User-agent: *\nDisallow: /private/\n");
            RobotRuleUrlPathAcceptor acceptor = new RobotRuleUrlPathAcceptor(site.baseUrl());
            acceptor.afterPropertiesSet();

            assertThat(acceptor.accept(null, site.baseUrl(), site.url("/public/a"),
                    taskAtDepth(0))).isTrue();
            assertThat(acceptor.accept(null, site.baseUrl(), site.url("/private/a"),
                    taskAtDepth(0))).isFalse();
        }
    }

    @Test
    @DisplayName("a missing robots.txt permits the crawl, as the protocol says it should")
    void missingRobotsAllowsEverything() throws Exception {
        try (TestSite site = new TestSite()) {
            RobotRuleUrlPathAcceptor acceptor = new RobotRuleUrlPathAcceptor(site.baseUrl());
            acceptor.afterPropertiesSet();
            assertThat(acceptor.accept(null, site.baseUrl(), site.url("/anything"),
                    taskAtDepth(0))).isTrue();
        }
    }

    @Test
    @DisplayName("robots runs first, then depth, then the path pattern")
    void acceptorsAreOrderedCheapestFirst() {
        List<Integer> orders =
                List.of(new RobotRuleUrlPathAcceptor("https://www.example.com").getOrder(),
                        new MaxFetchDepthUrlPathAcceptor().getOrder(),
                        new PathMatcherUrlPathAcceptor(CatalogFixtures.details()).getOrder());
        assertThat(orders).isSorted();
    }

}
