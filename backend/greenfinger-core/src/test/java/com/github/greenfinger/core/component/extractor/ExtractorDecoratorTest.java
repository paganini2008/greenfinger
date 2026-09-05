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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.CatalogFixtures;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawlTask;
import com.github.greenfinger.core.model.Catalog;

/**
 * 
 * @Description: ExtractorDecoratorTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class ExtractorDecoratorTest {

    /**
     * An extractor that fails a fixed number of times before succeeding.
     */
    private static class FlakyExtractor implements Extractor {

        private final AtomicInteger attempts = new AtomicInteger(0);
        private final int failuresBeforeSuccess;

        FlakyExtractor(int failuresBeforeSuccess) {
            this.failuresBeforeSuccess = failuresBeforeSuccess;
        }

        @Override
        public String getName() {
            return "flaky";
        }

        @Override
        public String extractHtml(CatalogDetails catalogDetails, String referUrl, String url,
                Charset pageEncoding, CrawlTask task) {
            if (attempts.incrementAndGet() <= failuresBeforeSuccess) {
                throw new IllegalStateException("boom");
            }
            return "<html><body>ok</body></html>";
        }

        int attempts() {
            return attempts.get();
        }
    }

    @Test
    @DisplayName("a transient failure is retried and the page still arrives")
    void retriesUntilSuccess() throws Exception {
        FlakyExtractor flaky = new FlakyExtractor(2);
        RetryableExtractor extractor = new RetryableExtractor(flaky, 3, 1L);

        assertThat(extractor.extractHtml(null, null, "https://a.com", StandardCharsets.UTF_8, null))
                .contains("ok");
        assertThat(flaky.attempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("a persistent failure gives up after the configured attempts")
    void stopsAfterMaxAttempts() {
        FlakyExtractor flaky = new FlakyExtractor(99);
        RetryableExtractor extractor = new RetryableExtractor(flaky, 2, 1L);

        assertThatThrownBy(() -> extractor.extractHtml(null, null, "https://a.com",
                StandardCharsets.UTF_8, null)).isInstanceOf(ExtractorException.class);
        assertThat(flaky.attempts()).isEqualTo(2);
    }

    @Test
    void retryPassesTheNameThrough() {
        assertThat(new RetryableExtractor(new FlakyExtractor(0), 1).getName()).isEqualTo("flaky");
    }

    @Test
    @DisplayName("the fetch interval is honoured between requests")
    void threadWaitPacesRequests() throws Exception {
        Catalog catalog = CatalogFixtures.catalog();
        catalog.setFetchInterval(120L);
        CatalogDetails details = CatalogFixtures.details(catalog);

        ThreadWaitExtractor extractor =
                new ThreadWaitExtractor(new FlakyExtractor(0), ThreadWait.SLEEP);

        long start = System.currentTimeMillis();
        extractor.extractHtml(details, null, "https://a.com", StandardCharsets.UTF_8, null);
        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(100L);
        assertThat(extractor.getName()).isEqualTo("flaky");
    }

    @Test
    void threadWaitModesBehaveAsNamed() {
        long start = System.currentTimeMillis();
        ThreadWait.NONE.doWait(200L);
        assertThat(System.currentTimeMillis() - start).isLessThan(100L);

        start = System.currentTimeMillis();
        ThreadWait.SLEEP.doWait(60L);
        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(50L);

        start = System.currentTimeMillis();
        ThreadWait.RANDOM_SLEEP.doWait(150L);
        assertThat(System.currentTimeMillis() - start).isLessThan(200L);
    }

    @Test
    void defaultHtmlIsEmptyUnlessOverridden() {
        assertThat(new FlakyExtractor(0).defaultHtml(null, null, "https://a.com",
                StandardCharsets.UTF_8, null, new RuntimeException())).isEmpty();
    }

}
