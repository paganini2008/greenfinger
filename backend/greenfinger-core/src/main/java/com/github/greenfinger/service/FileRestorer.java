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

package com.github.greenfinger.service;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.WebCrawlerComponentFactory;
import com.github.greenfinger.core.component.extractor.ConditionalGet;
import com.github.greenfinger.core.component.extractor.Extractor;
import com.github.greenfinger.core.component.extractor.FetchedPage;
import com.github.greenfinger.core.engine.CrawlTask;
import com.github.greenfinger.core.engine.ContentExtractor;
import com.github.greenfinger.core.engine.ImageFetcher;
import com.github.greenfinger.core.engine.PageParser;
import com.github.greenfinger.core.engine.CrawledPage;
import com.github.greenfinger.core.model.Image;
import com.github.greenfinger.core.output.BlobStore;
import com.github.greenfinger.core.record.ResourceRecord;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.core.utils.BeanLifeCycleUtils;
import com.github.greenfinger.core.utils.CharsetUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Puts back the files a version's pages were saved as, by fetching their urls again.
 *
 * <p>
 * The other layers are rebuilt from the database, which holds everything they need. Files are not:
 * the database keeps a page's metadata and the path its bytes were written to, but never the bytes
 * themselves. What it does keep is the {@code url} each page came from, and the {@code source_url}
 * of every image on it -- which is enough to go and get them again.
 *
 * <p>
 * So this is a crawl in the narrow sense and not in the wide one: it fetches, but it discovers
 * nothing. No links are followed, no rows are written, no page is visited that is not already in
 * the table. The set of urls is fixed before the first request, which is what makes it a repair
 * rather than a second crawl.
 *
 * <p>
 * <b>What comes back is the site as it is today.</b> A page that has changed since the crawl will
 * be restored with today's text, which no longer matches the index and the vectors that were built
 * from the old text -- so replaying the file layer is usually followed by replaying the other two.
 * A page that has been taken down cannot be restored at all. Both are counted and reported rather
 * than passed over in silence, because a restore that quietly left holes would be worse than one
 * that failed.
 *
 * <p>
 * A page whose files are all present is skipped without a request. That is what makes the operation
 * safe to run twice, and cheap the second time.
 *
 * @Description: FileRestorer
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class FileRestorer {

    private static final int PAGE_SIZE = 100;

    private final WebCrawlerComponentFactory componentFactory;
    private final WebCrawlerProperties webCrawlerProperties;
    private final ResourceRecordStore recordStore;

    /**
     * What a restore did, and what it could not do.
     *
     * @param checked    pages looked at
     * @param intact     pages whose files were all there already, so nothing was fetched
     * @param pages      pages written back
     * @param images     images written back
     * @param unreachable pages whose url no longer answers with anything usable
     * @param changed    pages that came back a different length than the row records -- the site
     *                   has moved on, so the index and the vectors for them are now stale
     */
    public record Result(long checked, long intact, long pages, long images, long unreachable,
            long changed) {

        public Result plus(Result other) {
            return new Result(checked + other.checked, intact + other.intact, pages + other.pages,
                    images + other.images, unreachable + other.unreachable,
                    changed + other.changed);
        }
    }

    public Result restore(CatalogDetails catalogDetails, int version, BlobStore blobStore,
            int offset, int limit) throws Exception {
        WebCrawlerProperties.Content content = webCrawlerProperties.getContent();
        ContentExtractor contentExtractor = new ContentExtractor(content.isExtractArticle(),
                content.getMinBlockLength(), content.getMinContentLength());
        PageParser pageParser = new PageParser(webCrawlerProperties.getImage());
        ImageFetcher imageFetcher = catalogDetails.isImageEnabled()
                ? new ImageFetcher(webCrawlerProperties.getImage())
                : null;
        // the catalog's own extractor, so the restore waits between requests exactly as the crawl
        // did. A repair is no reason to be rude to a site that is still up
        Extractor extractor = componentFactory.getExtractor(catalogDetails);
        // it holds an http client, and one that was never started fails every fetch with the same
        // "failed to extract" it reports for a site that is genuinely down
        BeanLifeCycleUtils.afterPropertiesSet(extractor);

        Result result = new Result(0, 0, 0, 0, 0, 0);
        try {
            long done = 0;
            for (int cursor = offset; done < limit; cursor += PAGE_SIZE) {
                int size = (int) Math.min(PAGE_SIZE, limit - done);
                List<ResourceRecord> batch =
                        recordStore.load(catalogDetails.getId(), version, cursor, size);
                if (batch.isEmpty()) {
                    break;
                }
                for (ResourceRecord record : batch) {
                    result = result.plus(restoreOne(catalogDetails, record, blobStore, extractor,
                            pageParser, contentExtractor, imageFetcher));
                    done++;
                }
            }
        } finally {
            BeanLifeCycleUtils.destroyQuietly(extractor);
        }
        return result;
    }

    private Result restoreOne(CatalogDetails catalogDetails, ResourceRecord record,
            BlobStore blobStore, Extractor extractor, PageParser pageParser,
            ContentExtractor contentExtractor, ImageFetcher imageFetcher) throws Exception {
        String url = record.resource().getUrl();
        List<ResourceRecord.ImageRecord> missingImages = record.images().stream()
                .filter(image -> notThere(blobStore, image.image().getImageFilePath())).toList();
        boolean pageMissing = notThere(blobStore, record.resource().getHtmlFilePath())
                || notThere(blobStore, record.resource().getHtmlContentFilePath());

        if (!pageMissing && missingImages.isEmpty()) {
            return new Result(1, 1, 0, 0, 0, 0);
        }

        Charset charset = CharsetUtils.toCharset(catalogDetails.getPageEncoding());
        CrawlTask task = CrawlTask.seed(catalogDetails.getId(), CrawlTask.ACTION_CRAWL,
                record.resource().getReferer(), url, record.resource().getCat(),
                catalogDetails.getPageEncoding(), record.resource().getVersion());
        FetchedPage fetched;
        try {
            // unconditionally: a conditional get would be answered "not modified" against an
            // etag from the last crawl, and a restore wants the bytes, not the answer
            fetched = extractor.fetch(catalogDetails, record.resource().getReferer(), url, charset,
                    task, ConditionalGet.NONE);
        } catch (Exception e) {
            log.warn("Could not fetch '{}' to restore it: {}", url, e.getMessage());
            return new Result(1, 0, 0, 0, 1, 0);
        }
        if (fetched == null || StringUtils.isBlank(fetched.html())) {
            log.warn("Nothing came back from '{}'; its files stay missing", url);
            return new Result(1, 0, 0, 0, 1, 0);
        }

        long pages = 0;
        long changed = 0;
        if (pageMissing) {
            Document document;
            try {
                document = pageParser.parse(fetched.html(), url);
            } catch (Exception e) {
                return new Result(1, 0, 0, 0, 1, 0);
            }
            String text = contentExtractor.extract(document);
            blobStore.write(record.resource().getHtmlFilePath(),
                    document.html().getBytes(StandardCharsets.UTF_8),
                    "text/html; charset=utf-8");
            blobStore.write(record.resource().getHtmlContentFilePath(),
                    text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8),
                    "text/plain; charset=utf-8");
            pages = 1;
            // a hint rather than a proof: a page that changed without changing length is not
            // caught. It is free, though, and a difference here is certain to be a real one
            Integer was = record.resource().getTextLength();
            if (was != null && text != null && was != text.length()) {
                changed = 1;
            }
        }

        long images = 0;
        if (imageFetcher != null) {
            for (ResourceRecord.ImageRecord image : missingImages) {
                if (restoreImage(blobStore, imageFetcher, image.image(),
                        image.reference().getSourceUrl(), url)) {
                    images++;
                }
            }
        }
        return new Result(1, 0, pages, images, 0, changed);
    }

    private boolean restoreImage(BlobStore blobStore, ImageFetcher imageFetcher, Image image,
            String sourceUrl, String pageUrl) {
        String from = StringUtils.defaultIfBlank(sourceUrl, image.getFirstSourceUrl());
        if (StringUtils.isBlank(from)) {
            return false;
        }
        try {
            Optional<CrawledPage.StoredImage> stored = imageFetcher.fetchOne(from, pageUrl);
            if (stored.isEmpty() || stored.get().getData() == null) {
                return false;
            }
            // written at the path the row records, not at one derived from what came back: the
            // path is how every other layer refers to this image, so a restore has to land on it
            blobStore.write(image.getImageFilePath(), stored.get().getData(),
                    StringUtils.defaultIfBlank(image.getContentType(),
                            stored.get().getContentType()));
            return true;
        } catch (Exception e) {
            log.warn("Could not restore image '{}': {}", from, e.getMessage());
            return false;
        }
    }

    private boolean notThere(BlobStore blobStore, String path) {
        if (StringUtils.isBlank(path)) {
            return false;
        }
        try {
            return !blobStore.exists(path);
        } catch (Exception e) {
            // unreadable is not the same as absent, but for a restore it has the same answer:
            // write it again rather than assume it is fine
            return true;
        }
    }

}
