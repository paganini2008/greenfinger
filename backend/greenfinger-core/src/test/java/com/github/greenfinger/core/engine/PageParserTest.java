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
import java.util.List;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.WebCrawlerProperties;

/**
 * 
 * @Description: PageParserTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class PageParserTest {

    private final WebCrawlerProperties.Image imageConfig = new WebCrawlerProperties.Image();
    private final PageParser parser = new PageParser(imageConfig);

    @Test
    @DisplayName("relative links are resolved against the page they were found on")
    void resolvesLinksToAbsoluteForm() {
        Document document = parser.parse(
                "<html><body><a href='/a'>a</a><a href='https://other.com/b'>b</a></body></html>",
                "https://site.com/dir/page.html");
        assertThat(parser.extractLinks(document)).containsExactly("https://site.com/a",
                "https://other.com/b");
    }

    @Test
    @DisplayName("javascript, mailto and tel are addresses, not pages")
    void skipsNonHttpSchemes() {
        Document document = parser.parse("<html><body>"
                + "<a href='javascript:void(0)'>x</a><a href='mailto:a@b.c'>y</a>"
                + "<a href='tel:123'>z</a><a href='/real'>ok</a></body></html>",
                "https://site.com/");
        assertThat(parser.extractLinks(document)).containsExactly("https://site.com/real");
    }

    @Test
    void findsImagesFromImgTags() {
        Document document = parser.parse(
                "<html><body><img src='/a.jpg' alt='A' width='300' height='200'></body></html>",
                "https://site.com/");
        List<ImageRef> images = parser.extractImages(document);

        assertThat(images).hasSize(1);
        assertThat(images.get(0).getUrl()).isEqualTo("https://site.com/a.jpg");
        assertThat(images.get(0).getAlt()).isEqualTo("A");
        assertThat(images.get(0).getDeclaredWidth()).isEqualTo(300);
        assertThat(images.get(0).getDeclaredHeight()).isEqualTo(200);
        assertThat(images.get(0).getSource()).isEqualTo(PageParser.SOURCE_IMG);
    }

    @Test
    @DisplayName("a lazily loaded image keeps its real url out of src until scripts run")
    void findsLazyLoadedImages() {
        Document document = parser.parse(
                "<html><body><img data-src='/lazy.jpg'></body></html>", "https://site.com/");
        assertThat(parser.extractImages(document)).extracting(ImageRef::getUrl)
                .containsExactly("https://site.com/lazy.jpg");
    }

    @Test
    @DisplayName("srcset holds several candidates, each with a descriptor that is not part of the url")
    void findsResponsiveCandidates() {
        Document document = parser.parse(
                "<html><body><img srcset='/small.jpg 480w, /large.jpg 1024w'></body></html>",
                "https://site.com/");
        assertThat(parser.extractImages(document)).extracting(ImageRef::getUrl)
                .contains("https://site.com/small.jpg", "https://site.com/large.jpg");
    }

    @Test
    void findsSocialPreviewImages() {
        Document document = parser.parse("<html><head>"
                + "<meta property='og:image' content='https://site.com/og.png'>"
                + "<meta name='twitter:image' content='https://site.com/tw.png'>"
                + "</head><body></body></html>", "https://site.com/");
        assertThat(parser.extractImages(document)).extracting(ImageRef::getUrl)
                .contains("https://site.com/og.png", "https://site.com/tw.png");
    }

    @Test
    @DisplayName("a logo repeated in header and footer is considered once")
    void deduplicatesWithinOnePage() {
        Document document = parser.parse(
                "<html><body><img src='/logo.png'><img src='/logo.png'></body></html>",
                "https://site.com/");
        assertThat(parser.extractImages(document)).hasSize(1);
    }

    @Test
    void honoursTheSourceConfiguration() {
        WebCrawlerProperties.Image onlyMeta = new WebCrawlerProperties.Image();
        onlyMeta.setSources(List.of(PageParser.SOURCE_META));
        PageParser metaOnly = new PageParser(onlyMeta);

        Document document = metaOnly.parse("<html><head>"
                + "<meta property='og:image' content='https://site.com/og.png'></head>"
                + "<body><img src='/a.jpg'></body></html>", "https://site.com/");
        assertThat(metaOnly.extractImages(document)).extracting(ImageRef::getUrl)
                .containsExactly("https://site.com/og.png");
    }

    @Test
    @DisplayName("one gallery cannot dominate a crawl")
    void capsImagesPerPage() {
        WebCrawlerProperties.Image capped = new WebCrawlerProperties.Image();
        capped.setMaxPerPage(2);
        PageParser parser = new PageParser(capped);

        StringBuilder html = new StringBuilder("<html><body>");
        for (int i = 0; i < 10; i++) {
            html.append("<img src='/image").append(i).append(".jpg'>");
        }
        Document document = parser.parse(html.append("</body></html>").toString(),
                "https://site.com/");
        assertThat(parser.extractImages(document)).hasSize(2);
    }

    @Test
    void handlesAPageWithNothingOnIt() {
        Document document = parser.parse("<html><body></body></html>", "https://site.com/");
        assertThat(parser.extractLinks(document)).isEmpty();
        assertThat(parser.extractImages(document)).isEmpty();
    }

    @Test
    @DisplayName("the files a page links to are collected, and pages are not mistaken for them")
    void collectsTheFilesAPageLinksTo() {
        Document document = parser.parse("""
                <a href="/handbook.pdf">The handbook</a>
                <a href="/prices.xlsx?v=2">Prices</a>
                <a href="/notes.md">Notes</a>
                <a href="/2020/an-article/">An article</a>
                <a href="/download?file=x.pdf">Get the pdf</a>
                <a href="/logo.png">The logo</a>
                """, "https://example.com/");

        var files = parser.extractDownloadedFiles(document);

        assertThat(files).extracting(DownloadedFile::getFileType)
                .containsExactlyInAnyOrder("pdf", "xlsx", "md");
        // the query is not the extension, in either direction
        assertThat(files).extracting(DownloadedFile::getUrl)
                .contains("https://example.com/prices.xlsx?v=2")
                .doesNotContain("https://example.com/download?file=x.pdf");
        // a picture is the image pipeline's, not a document
        assertThat(files).extracting(DownloadedFile::getFileType).doesNotContain("png");
        assertThat(files).extracting(DownloadedFile::getLinkText).contains("The handbook");
    }

    @Test
    @DisplayName("a server-side engine's extension is still a page")
    void engineExtensionsAreHtml() {
        // .php and friends put the engine's name on the url and answer html. Reading the
        // extension literally would send every one of them looking for a parser
        for (String url : new String[] {"https://x/index.php", "https://x/list.jsp",
                "https://x/page.aspx", "https://x/do/list.do", "https://x/a.htm",
                "https://x/about", "https://x/2020/a-post/", "https://x/cgi-bin/q.cgi"}) {
            assertThat(PageParser.fileTypeOf(url)).as(url).isEqualTo(CrawlTask.FILE_TYPE_HTML);
        }
        // and a real file keeps its own name
        assertThat(PageParser.fileTypeOf("https://x/handbook.pdf")).isEqualTo("pdf");
        assertThat(PageParser.fileTypeOf("https://x/prices.xlsx?v=2")).isEqualTo("xlsx");
        // a page that hands one out is a page
        assertThat(PageParser.fileTypeOf("https://x/download?file=a.pdf"))
                .isEqualTo(CrawlTask.FILE_TYPE_HTML);
    }

}
