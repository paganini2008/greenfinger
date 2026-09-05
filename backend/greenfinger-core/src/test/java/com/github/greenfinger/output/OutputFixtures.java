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

package com.github.greenfinger.output;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsImpl;
import com.github.greenfinger.core.engine.CrawledPage;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.ContentMode;
import com.github.greenfinger.core.model.Image;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.model.Resource;
import com.github.greenfinger.core.model.ResourceImage;
import com.github.greenfinger.core.output.FileLayout;
import com.github.greenfinger.core.output.OutputPayload;
import com.github.greenfinger.core.record.ResourceRecord;
import com.github.greenfinger.core.utils.HashUtils;
import com.github.greenfinger.core.utils.UuidUtils;

/**
 * Builds the objects the output channels consume. The ids are derived exactly as the record store
 * derives them, so a test asserting on a file path is asserting on the real thing.
 * 
 * @Description: OutputFixtures
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public abstract class OutputFixtures {

    public static final String CATALOG_ID = "0192f0c8-1234-7000-8000-0000000000aa";

    public static CatalogDetails catalogDetails() {
        return catalogDetails(Set.of(OutputType.FILE));
    }

    public static CatalogDetails catalogDetails(Set<OutputType> outputTypes) {
        return catalogDetails(outputTypes, ContentMode.TEXT_IMAGE);
    }

    public static CatalogDetails catalogDetails(Set<OutputType> outputTypes, ContentMode mode) {
        Catalog catalog = new Catalog();
        catalog.setId(CATALOG_ID);
        catalog.setName("example");
        catalog.setUrl("https://www.example.com");
        catalog.setStartUrl("https://www.example.com");
        catalog.setCat("tech");
        catalog.setPathPattern("**.example.com");
        catalog.setOutputTypes(outputTypes);
        catalog.setContentMode(mode);
        catalog.setImageEnabled(true);
        catalog.setIndexVersion(0);
        catalog.setSearchVersion(-1);
        catalog.setMaxVersions(10);
        return new CatalogDetailsImpl(catalog, new WebCrawlerProperties());
    }

    public static FileLayout layout() {
        return new FileLayout(CATALOG_ID, 0, 2);
    }

    public static CrawledPage page(String url, String title, String text) {
        CrawledPage page = new CrawledPage();
        page.setCatalogId(CATALOG_ID);
        page.setCatalogName("example");
        page.setCat("test");
        page.setVersion(0);
        page.setUrl(url);
        page.setTitle(title);
        page.setText(text);
        page.setHtml("<html><head><title>" + title + "</title></head><body>" + text
                + "</body></html>");
        page.setDepth(1);
        page.setReferer("https://www.example.com");
        page.setContentHash("abc123");
        page.setFetchedAt(new Date());
        return page;
    }

    public static CrawledPage pageWithImage(String url, String title, String text) {
        CrawledPage page = page(url, title, text);
        CrawledPage.StoredImage image = new CrawledPage.StoredImage();
        image.setSourceUrl("https://www.example.com/a.jpg");
        image.setContentHash("imagehash");
        image.setContentType("image/jpeg");
        image.setAlt("a picture");
        image.setTitle("picture title");
        image.setContext("words around the picture");
        image.setWidth(400);
        image.setHeight(300);
        image.setBytes(4L);
        image.setData(new byte[] {1, 2, 3, 4});
        page.getStoredImages().add(image);
        return page;
    }

    /**
     * The record the database would have handed back for this page.
     */
    public static ResourceRecord record(CrawledPage page) {
        UUID namespace = UUID.fromString(CATALOG_ID);
        FileLayout layout = layout();
        String urlHash = HashUtils.sha256(page.getUrl());
        String resourceId =
                UuidUtils.nameBased(namespace, page.getVersion() + "|" + urlHash).toString();

        Resource resource = new Resource();
        resource.setId(resourceId);
        resource.setCatalogId(CATALOG_ID);
        resource.setVersion(page.getVersion());
        resource.setUrl(page.getUrl());
        resource.setUrlHash(urlHash);
        resource.setTitle(page.getTitle());
        resource.setCat(page.getCat());
        resource.setContentHash(page.getContentHash());
        resource.setDepth(page.getDepth());
        resource.setLinkCount(page.getLinks() != null ? page.getLinks().size() : 0);
        resource.setTextLength(page.getText() != null ? page.getText().length() : 0);
        resource.setLinkTextLength(page.getLinkTextLength());
        resource.setReferer(page.getReferer());
        resource.setHtmlFilePath(layout.html(resourceId));
        resource.setHtmlContentFilePath(layout.text(resourceId));
        resource.setCreatedAt(page.getFetchedAt());
        resource.setUpdatedAt(page.getFetchedAt());

        List<ResourceRecord.ImageRecord> images = page.getStoredImages().stream().map(stored -> {
            String imageId = UuidUtils
                    .nameBased(namespace, page.getVersion() + "|" + stored.getContentHash())
                    .toString();
            Image image = new Image();
            image.setId(imageId);
            image.setCatalogId(CATALOG_ID);
            image.setVersion(page.getVersion());
            image.setContentHash(stored.getContentHash());
            image.setFirstSourceUrl(stored.getSourceUrl());
            image.setImageFilePath(
                    layout.image(imageId, stored.getContentType(), stored.getSourceUrl()));
            image.setContentType(stored.getContentType());
            image.setWidth(stored.getWidth());
            image.setHeight(stored.getHeight());
            image.setBytes(stored.getBytes());

            ResourceImage reference = new ResourceImage();
            reference.setId(UuidUtils
                    .nameBased(namespace, page.getVersion() + "|" + resourceId + "|" + imageId)
                    .toString());
            reference.setCatalogId(CATALOG_ID);
            reference.setVersion(page.getVersion());
            reference.setResourceId(resourceId);
            reference.setImageId(imageId);
            reference.setSourceUrl(stored.getSourceUrl());
            reference.setAltText(stored.getAlt());
            reference.setTitleText(stored.getTitle());
            reference.setContextText(stored.getContext());
            return new ResourceRecord.ImageRecord(image, reference);
        }).toList();

        return new ResourceRecord(resource, images);
    }

    public static OutputPayload payload(CatalogDetails catalogDetails, CrawledPage page) {
        return new OutputPayload(catalogDetails, record(page), page);
    }

}
