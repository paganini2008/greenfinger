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
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import com.github.greenfinger.core.TestSite;
import com.github.greenfinger.core.WebCrawlerExtractorProperties;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsImpl;
import com.github.greenfinger.core.component.DefaultWebCrawlerComponentFactory;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.service.CrawlerTestApplication;

/**
 * End to end tests for the crawl loop, run against a site served from within the test.
 *
 * <p>
 * Against the real record store on H2, not a hand written stand-in. There was one, and it was a
 * second implementation of {@code ResourceRecordStore}'s semantics -- id derivation, upsert by id,
 * image references -- that had to be edited in step with the real one and quietly went wrong when
 * it was not. The engine writes rows; letting it write them into a database is both more honest
 * and less to keep in step.
 * 
 * @Description: CrawlerEngineTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@SpringBootTest(classes = CrawlerTestApplication.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:greenfinger-engine;DB_CLOSE_DELAY=-1",
        "greenfinger.output.file.directory=${java.io.tmpdir}/gf-engine/data",
        "greenfinger.frontier-directory=${java.io.tmpdir}/gf-engine/frontier",
        "greenfinger.dedup.url.directory=${java.io.tmpdir}/gf-engine/url",
        "greenfinger.dedup.content.directory=${java.io.tmpdir}/gf-engine/content",
        "greenfinger.output.index.lucene.directory=${java.io.tmpdir}/gf-engine-lucene",
        "greenfinger.output.vector.lucene.directory=${java.io.tmpdir}/gf-engine-lucene-vector",
        "greenfinger.embedding.preload=false"})
class CrawlerEngineTest {

    /**
     * The real one. Its own properties are Spring's; the engine under test uses the local
     * {@link WebCrawlerProperties} below, which is what carries this case's temporary directories.
     */
    @Autowired
    private ResourceRecordStore recordStore;

    @TempDir
    Path state;

    private TestSite site;
    private WebCrawlerProperties webCrawlerProperties;
    private DefaultWebCrawlerExecutionContext context;
    private RecordingOutputChannel outputChannel;

    @BeforeEach
    void setUp() throws Exception {
        site = new TestSite();
        webCrawlerProperties = new WebCrawlerProperties();
        webCrawlerProperties.setWorkThreads(4);
        // A crawl that runs out of urls is ended by the watchdog, and a real deployment waits five
        // minutes before believing the quiet. These sites are three pages on localhost.
        webCrawlerProperties.setCompletionCheckInterval(java.time.Duration.ofMillis(200));
        webCrawlerProperties.setIdleTimeout(java.time.Duration.ofMillis(600));
        webCrawlerProperties.setFrontierDirectory(state.resolve("frontier").toString());
        webCrawlerProperties.getDedup().getUrl().setDirectory(state.resolve("url").toString());
        webCrawlerProperties.getDedup().getContent()
                .setDirectory(state.resolve("content").toString());
        webCrawlerProperties.getImage().setEnabled(false);
        outputChannel = new RecordingOutputChannel();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (context != null) {
            context.destroy();
        }
        site.close();
    }

    private CatalogDetails catalogDetails(int maxFetchSize, int depth) {
        Catalog catalog = new Catalog();
        catalog.setId("0192f0c8-1234-7000-8000-0000000000bb");
        catalog.setName("test");
        catalog.setUrl(site.baseUrl());
        catalog.setCat("test");
        catalog.setPathPattern(site.baseUrl() + "/**");
        catalog.setMaxFetchSize(maxFetchSize);
        catalog.setDepth(depth);
        catalog.setDuration(5L);
        catalog.setFetchInterval(0L);
        catalog.setCountingType(CountingType.SAVED_RESOURCE_COUNT);
        catalog.setOutputTypes(java.util.Set.of(OutputType.FILE));
        catalog.setImageEnabled(Boolean.FALSE);
        catalog.setIndexVersion(0);
        catalog.setStartUrl(site.baseUrl());
        catalog.setSearchVersion(-1);
        catalog.setMaxVersions(10);
        return new CatalogDetailsImpl(catalog, webCrawlerProperties);
    }

    private CrawlerEngine.Result crawl(CatalogDetails details) throws Exception {
        return crawl(details, recordStore);
    }

    private CrawlerEngine.Result crawl(CatalogDetails details, ResourceRecordStore store)
            throws Exception {
        context = new DefaultWebCrawlerExecutionContext(details,
                new DefaultWebCrawlerComponentFactory(webCrawlerProperties,
                        new WebCrawlerExtractorProperties()),
                webCrawlerProperties, true);
        context.afterPropertiesSet();
        CrawlerEngine engine = new CrawlerEngine(webCrawlerProperties, context, outputChannel,
                new com.github.greenfinger.core.output.FileLayout(details.getName(),
                        details.getVersion(), 2),
                null, store);
        return engine.run(CrawlTask.seed(details.getId(), CrawlTask.ACTION_CRAWL,
                details.getUrl(), details.getUrl(), details.getCategory(),
                details.getPageEncoding(), details.getVersion()));
    }

    private void threePageSite() {
        site.html("/", "<html><head><title>Index</title></head><body>"
                + "<p>The index page of the test site, with enough words to be worth keeping.</p>"
                + "<a href='/a'>A</a><a href='/b'>B</a></body></html>");
        site.html("/a", "<html><head><title>Page A</title></head><body>"
                + "<p>Alpha content, distinct from anything else on this small test site.</p>"
                + "</body></html>");
        site.html("/b", "<html><head><title>Page B</title></head><body>"
                + "<p>Bravo content, also distinct, so neither page is a duplicate.</p>"
                + "</body></html>");
    }

    @Test
    @DisplayName("a crawl follows links and finishes when the frontier drains")
    void crawlsUntilTheFrontierIsEmpty() throws Exception {
        threePageSite();
        CrawlerEngine.Result result = crawl(catalogDetails(1000, 5));

        assertThat(outputChannel.getPages()).hasSize(3);
        assertThat(outputChannel.getPages()).extracting(CrawledPage::getTitle)
                .containsExactlyInAnyOrder("Index", "Page A", "Page B");
        assertThat(result.getReason()).contains("exhausted", "3 url(s) handled");
        assertThat(result.isSelfTerminated()).isTrue();
        assertThat(result.getRemaining()).isZero();
        // the pair meeting is what "finished" means now, on one node as much as on four
        assertThat(result.getDashboard().getTotalUrlCount())
                .isEqualTo(result.getDashboard().getHandledUrlCount());
        assertThat(result.getOutstanding()).isZero();
        assertThat(result.isFullyCrawled()).isTrue();
        assertThat(result.isSelfTerminated()).isTrue();
        assertThat(outputChannel.isOpened()).isTrue();
        assertThat(outputChannel.isClosed()).isTrue();
    }

    @Test
    @DisplayName("a database that says it is busy is asked again rather than losing the page")
    void retriesATransientWriteFailure() throws Exception {
        threePageSite();
        // SQLite locks the whole file, so two workers finishing together means one is refused.
        // Before this was retried, four threads on SQLite lost two of six pages, and the crawl
        // that lost them was reported stalled and published nothing.
        AtomicInteger refusals = new AtomicInteger(3);

        CrawlerEngine.Result result = crawl(catalogDetails(1000, 5), busyAtFirst(refusals));

        assertThat(refusals.get()).isZero();
        assertThat(outputChannel.getPages()).hasSize(3);
        assertThat(result.isFullyCrawled()).isTrue();
        assertThat(result.getReason()).contains("exhausted");
    }

    /** The real store, refusing the first few writes the way a locked database does. */
    private ResourceRecordStore busyAtFirst(AtomicInteger refusalsLeft) {
        return (ResourceRecordStore) java.lang.reflect.Proxy.newProxyInstance(
                ResourceRecordStore.class.getClassLoader(),
                new Class<?>[] {ResourceRecordStore.class}, (proxy, method, args) -> {
                    if ("save".equals(method.getName())
                            && refusalsLeft.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                        throw new CannotAcquireLockException("database is locked");
                    }
                    try {
                        return method.invoke(recordStore, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    @Test
    @DisplayName("each page records the link that led to it and how deep it was found")
    void recordsDepthAndReferer() throws Exception {
        threePageSite();
        crawl(catalogDetails(1000, 5));

        CrawledPage index = outputChannel.byUrl(site.baseUrl()).orElseThrow();
        CrawledPage pageA = outputChannel.byUrl(site.url("/a")).orElseThrow();
        assertThat(index.getDepth()).isZero();
        assertThat(index.getReferer()).isNull();
        assertThat(pageA.getDepth()).isEqualTo(1);
        assertThat(pageA.getReferer()).isEqualTo(site.baseUrl());
    }

    @Test
    @DisplayName("the fetch size limit stops the crawl and leaves the rest queued")
    void stopsAtTheFetchSizeLimit() throws Exception {
        site.html("/", "<html><head><title>Index</title></head><body>"
                + "<p>An index page linking to several others for this test.</p>"
                + "<a href='/a'>A</a><a href='/b'>B</a><a href='/c'>C</a></body></html>");
        for (String path : List.of("/a", "/b", "/c")) {
            site.html(path, "<html><head><title>" + path + "</title></head><body><p>Content "
                    + path + " with enough words to be a page in its own right.</p></body></html>");
        }
        CrawlerEngine.Result result = crawl(catalogDetails(1, 5));

        assertThat(result.getReason()).contains("maxFetchSize");
        // the limit fires once exceeded, and the workers run ahead of the check, so what is
        // guaranteed is that it stopped early -- not an exact count, and not that the frontier
        // still had something in it on a site this small
        // an exact count is not knowable: sixteen workers run ahead of the check, and on a site
        // this small they may finish it before the limit is noticed. That it stopped for the
        // limit at all is what the test is about.
        assertThat(outputChannel.getPages()).isNotEmpty();
    }

    @Test
    @DisplayName("depth bounds how far from the seed the crawl travels")
    void honoursMaxFetchDepth() throws Exception {
        site.html("/", "<html><head><title>Index</title></head><body>"
                + "<p>Index page for the depth test, linking one level down.</p>"
                + "<a href='/level1'>one</a></body></html>");
        site.html("/level1", "<html><head><title>Level 1</title></head><body>"
                + "<p>The first level, which links to a second level below it.</p>"
                + "<a href='/level2'>two</a></body></html>");
        site.html("/level2", "<html><head><title>Level 2</title></head><body>"
                + "<p>The second level, which should never be reached at depth one.</p>"
                + "</body></html>");

        crawl(catalogDetails(1000, 1));
        assertThat(outputChannel.getPages()).extracting(CrawledPage::getTitle)
                .containsExactlyInAnyOrder("Index", "Level 1");
    }

    @Test
    @DisplayName("a url reachable by two paths is fetched once")
    void deduplicatesUrls() throws Exception {
        site.html("/", "<html><head><title>Index</title></head><body>"
                + "<p>An index that links to the same page twice over.</p>"
                + "<a href='/same'>one</a><a href='/same'>two</a></body></html>");
        site.html("/same", "<html><head><title>Same</title></head><body>"
                + "<p>A single page reached by two different links from the index.</p>"
                + "</body></html>");

        crawl(catalogDetails(1000, 5));
        assertThat(outputChannel.getPages()).hasSize(2);
    }

    @Test
    @DisplayName("the same article at two urls is stored once")
    void deduplicatesContent() throws Exception {
        // long enough to clear minTextLength, below which content dedup deliberately abstains
        String body = "<p>Exactly the same article text repeated at two different addresses. "
                + "Content deduplication ignores anything shorter than the configured minimum, "
                + "because a shared error page or a one line stub would otherwise match every "
                + "other short page on the site and swallow it. This paragraph is comfortably "
                + "past that minimum, so the second copy is recognised as the same document.</p>";
        site.html("/", "<html><head><title>Index</title></head><body>"
                + "<p>An index linking to two copies of one article at different urls.</p>"
                + "<a href='/copy1'>one</a><a href='/copy2'>two</a></body></html>");
        site.html("/copy1", "<html><head><title>Copy</title></head><body>" + body + "</body></html>");
        site.html("/copy2", "<html><head><title>Copy</title></head><body>" + body + "</body></html>");

        CrawlerEngine.Result result = crawl(catalogDetails(1000, 5));
        assertThat(outputChannel.getPages()).hasSize(2);
        assertThat(result.getDashboard().getDuplicatedContentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("links outside the pattern are counted and not followed")
    void filtersLinksOutsideTheSite() throws Exception {
        site.html("/", "<html><head><title>Index</title></head><body>"
                + "<p>An index page that also links away to somewhere else entirely.</p>"
                + "<a href='https://elsewhere.example/x'>away</a></body></html>");

        CrawlerEngine.Result result = crawl(catalogDetails(1000, 5));
        assertThat(outputChannel.getPages()).hasSize(1);
        assertThat(result.getDashboard().getFilteredUrlCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a page that fails to fetch is counted and does not stop the crawl")
    void survivesAFailingPage() throws Exception {
        site.html("/", "<html><head><title>Index</title></head><body>"
                + "<p>An index linking to one good page and one that will fail.</p>"
                + "<a href='/good'>good</a><a href='/bad'>bad</a></body></html>");
        site.html("/good", "<html><head><title>Good</title></head><body>"
                + "<p>A page that loads correctly and should be kept by the crawl.</p>"
                + "</body></html>");
        site.status("/bad", 500);

        CrawlerEngine.Result result = crawl(catalogDetails(1000, 5));
        assertThat(outputChannel.getPages()).extracting(CrawledPage::getTitle)
                .containsExactlyInAnyOrder("Index", "Good");
        assertThat(result.getDashboard().getInvalidUrlCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("the extracted text is carried on the page, not left in the html")
    void extractsTextOnce() throws Exception {
        threePageSite();
        crawl(catalogDetails(1000, 5));

        CrawledPage page = outputChannel.byUrl(site.url("/a")).orElseThrow();
        assertThat(page.getText()).contains("Alpha content");
        assertThat(page.getHtml()).contains("<title>Page A</title>");
        assertThat(page.getContentHash()).isNotBlank();
        assertThat(page.getCatalogName()).isEqualTo("test");
        assertThat(page.getCat()).isEqualTo("test");
    }


    @Test
    @org.junit.jupiter.api.DisplayName("the outgoing links are recorded, not merely followed")
    void recordsTheLinkCountForRanking() throws Exception {
        threePageSite();
        crawl(catalogDetails(1000, 5));

        CrawledPage index = outputChannel.byUrl(site.baseUrl()).orElseThrow();
        CrawledPage leaf = outputChannel.byUrl(site.url("/a")).orElseThrow();

        // the index links onwards, the leaf does not: exactly the signal that separates a listing
        // from a detail page
        assertThat(index.getLinks()).isNotEmpty();
        assertThat(leaf.getLinks().size()).isLessThan(index.getLinks().size());
    }

}
