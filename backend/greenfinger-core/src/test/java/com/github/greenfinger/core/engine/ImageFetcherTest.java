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

package com.github.greenfinger.core.engine;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.TestSite;
import com.github.greenfinger.core.WebCrawlerProperties;

/**
 * 
 * @Description: ImageFetcherTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class ImageFetcherTest {

    private TestSite site;
    private WebCrawlerProperties.Image config;

    @BeforeEach
    void setUp() throws Exception {
        site = new TestSite();
        config = new WebCrawlerProperties().getImage();
    }

    @AfterEach
    void tearDown() {
        site.close();
    }

    private CrawledPage pageWith(String... imageUrls) {
        CrawledPage page = new CrawledPage();
        page.setUrl(site.url("/page"));
        for (String imageUrl : imageUrls) {
            page.getImages().add(new ImageRef(imageUrl, "img"));
        }
        return page;
    }

    @Test
    void downloadsAndStoresAnImage() throws Exception {
        site.binary("/big.png", "image/png", TestSite.largePng());
        CrawledPage page = pageWith(site.url("/big.png"));

        new ImageFetcher(config).fetchAll(page);

        assertThat(page.getStoredImages()).hasSize(1);
        CrawledPage.StoredImage stored = page.getStoredImages().get(0);
        assertThat(stored.getWidth()).isEqualTo(200);
        assertThat(stored.getHeight()).isEqualTo(200);
        assertThat(stored.getContentType()).isEqualTo("image/png");
        assertThat(stored.getBytes()).isPositive();
        assertThat(stored.getContentHash()).hasSize(64);
        assertThat(stored.getData()).isNotEmpty();
    }

    @Test
    @DisplayName("an icon-sized image is not content and is skipped")
    void rejectsImagesBelowTheMinimumSize() throws Exception {
        site.binary("/pixel.png", "image/png", TestSite.onePixelPng());
        CrawledPage page = pageWith(site.url("/pixel.png"));

        new ImageFetcher(config).fetchAll(page);
        assertThat(page.getStoredImages()).isEmpty();
    }

    @Test
    @DisplayName("thresholds of zero keep everything")
    void keepsSmallImagesWhenThresholdsAreDisabled() throws Exception {
        config.setMinWidth(0);
        config.setMinHeight(0);
        site.binary("/pixel.png", "image/png", TestSite.onePixelPng());
        CrawledPage page = pageWith(site.url("/pixel.png"));

        new ImageFetcher(config).fetchAll(page);
        assertThat(page.getStoredImages()).hasSize(1);
    }

    @Test
    @DisplayName("the declared size rejects an icon before a request is even made")
    void rejectsOnDeclaredSizeWithoutFetching() {
        CrawledPage page = new CrawledPage();
        page.setUrl(site.url("/page"));
        ImageRef ref = new ImageRef(site.url("/never-requested.png"), "img");
        ref.setDeclaredWidth(16);
        ref.setDeclaredHeight(16);
        page.getImages().add(ref);

        int before = site.requestCount();
        new ImageFetcher(config).fetchAll(page);

        assertThat(page.getStoredImages()).isEmpty();
        assertThat(site.requestCount()).isEqualTo(before);
    }

    @Test
    void rejectsMediaTypesOutsideTheWhitelist() throws Exception {
        site.binary("/file.pdf", "application/pdf", "not an image".getBytes(StandardCharsets.UTF_8));
        CrawledPage page = pageWith(site.url("/file.pdf"));

        new ImageFetcher(config).fetchAll(page);
        assertThat(page.getStoredImages()).isEmpty();
    }

    @Test
    void rejectsImagesOverTheByteLimit() throws Exception {
        config.setMaxBytes(10L);
        site.binary("/big.png", "image/png", TestSite.largePng());
        CrawledPage page = pageWith(site.url("/big.png"));

        new ImageFetcher(config).fetchAll(page);
        assertThat(page.getStoredImages()).isEmpty();
    }

    @Test
    void skipsInlineDataUris() {
        CrawledPage page = pageWith("data:image/png;base64,iVBORw0KGgo=");
        new ImageFetcher(config).fetchAll(page);
        assertThat(page.getStoredImages()).isEmpty();
    }

    @Test
    @DisplayName("a failing image does not fail the page")
    void survivesAFailingImage() throws Exception {
        site.status("/broken.png", 500);
        site.binary("/good.png", "image/png", TestSite.largePng());
        CrawledPage page = pageWith(site.url("/broken.png"), site.url("/good.png"));

        new ImageFetcher(config).fetchAll(page);
        assertThat(page.getStoredImages()).hasSize(1);
    }

    @Test
    @DisplayName("the same picture reached twice is downloaded twice but stored once")
    void storesIdenticalBytesOnce() throws Exception {
        byte[] png = TestSite.largePng();
        site.binary("/one.png", "image/png", png);
        site.binary("/two.png", "image/png", png);
        CrawledPage page = pageWith(site.url("/one.png"), site.url("/two.png"));

        new ImageFetcher(config).fetchAll(page);

        assertThat(page.getStoredImages()).hasSize(2);
        assertThat(page.getStoredImages().get(0).getContentHash())
                .isEqualTo(page.getStoredImages().get(1).getContentHash());
    }

    @Test
    @DisplayName("the media type decides the extension the file layout will use")
    void reportsTheMediaType() throws Exception {
        config.setMimeTypes(List.of("image/jpeg", "image/png", "image/webp", "image/gif"));
        config.setMinWidth(0);
        config.setMinHeight(0);
        site.binary("/a.gif", "image/gif", TestSite.onePixelPng());
        CrawledPage page = pageWith(site.url("/a.gif"));

        new ImageFetcher(config).fetchAll(page);
        assertThat(page.getStoredImages().get(0).getContentType()).isEqualTo("image/gif");
        assertThat(com.github.greenfinger.core.output.FileLayout.extensionOf("image/gif",
                site.url("/a.gif"))).isEqualTo(".gif");
    }

    @Test
    @DisplayName("the bytes are carried, not written: the database is the gate")
    void keepsTheBytesForTheFileLayer() throws Exception {
        site.binary("/big.png", "image/png", TestSite.largePng());
        CrawledPage page = pageWith(site.url("/big.png"));

        new ImageFetcher(config).fetchAll(page);
        assertThat(page.getStoredImages().get(0).getData()).isNotEmpty();
    }

    @Test
    @DisplayName("one page cannot hold unbounded image bytes")
    void stopsAtThePageBudget() throws Exception {
        config.setMaxPageBytes(1L);
        site.binary("/one.png", "image/png", TestSite.largePng());
        site.binary("/two.png", "image/png", TestSite.largePng());
        CrawledPage page = pageWith(site.url("/one.png"), site.url("/two.png"));

        new ImageFetcher(config).fetchAll(page);
        assertThat(page.getStoredImages()).hasSize(1);
    }

}
