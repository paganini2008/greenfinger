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

package com.github.greenfinger.record;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawledPage;
import com.github.greenfinger.core.model.Image;
import com.github.greenfinger.core.model.Resource;
import com.github.greenfinger.core.model.ResourceImage;
import com.github.greenfinger.core.output.FileLayout;
import com.github.greenfinger.core.record.ResourceRecord;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.core.record.ResourceRecordStore.PageState;
import com.github.greenfinger.core.utils.HashUtils;
import com.github.greenfinger.core.utils.UuidUtils;
import lombok.RequiredArgsConstructor;

/**
 * The database half of the write path.
 *
 * <p>
 * Every id here is derived, never generated: the same url at the same version always produces the
 * same resource id, the same bytes always produce the same image id. That is what lets a file path
 * be computed before the file exists, and what makes replaying the index or the vectors an
 * overwrite rather than a duplication.
 * 
 * @Description: JpaResourceRecordStore
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@RequiredArgsConstructor
public class JpaResourceRecordStore implements ResourceRecordStore {

    private final ResourceRepository resourceRepository;
    private final ImageRepository imageRepository;
    private final ResourceImageRepository resourceImageRepository;

    /** Shared rows go through here, in transactions of their own. See {@link ImageWriter}. */
    private final ImageWriter imageWriter;

    @Override
    @Transactional
    public ResourceRecord save(CatalogDetails catalogDetails, CrawledPage page, FileLayout layout) {
        UUID namespace = UUID.fromString(catalogDetails.getId());
        int version = catalogDetails.getVersion();

        String urlHash = HashUtils.sha256(page.getUrl());
        String resourceId =
                UuidUtils.nameBased(namespace, version + "|" + urlHash).toString();

        // an existing row is updated in place rather than inserted beside: the id is derived from
        // the url and the version, so a refreshed page keeps the same id, the same file paths, the
        // same document and the same vector points, and every layer is overwritten rather than
        // duplicated. That is what makes a refresh a merge.
        Resource resource = resourceRepository.findById(resourceId).orElseGet(Resource::new);
        resource.setId(resourceId);
        resource.setCatalogId(catalogDetails.getId());
        resource.setVersion(version);
        resource.setUrl(page.getUrl());
        resource.setUrlHash(urlHash);
        resource.setTitle(StringUtils.abbreviate(page.getTitle(), 1000));
        resource.setCat(page.getCat());
        resource.setContentHash(page.getContentHash());
        resource.setEtag(page.getEtag());
        resource.setHttpLastModified(page.getLastModified());
        resource.setDepth(page.getDepth());
        resource.setLinkCount(page.getLinks() != null ? page.getLinks().size() : 0);
        resource.setTextLength(page.getText() != null ? page.getText().length() : 0);
        resource.setLinkTextLength(page.getLinkTextLength());
        resource.setReferer(page.getReferer());
        resource.setHtmlFilePath(layout.html(resourceId));
        resource.setHtmlContentFilePath(layout.text(resourceId));
        Date now = page.getFetchedAt() != null ? page.getFetchedAt() : new Date();
        resource.setCreatedAt(resource.getCreatedAt() != null ? resource.getCreatedAt() : now);
        resource.setUpdatedAt(now);
        resourceRepository.save(resource);

        List<ResourceRecord.ImageRecord> images = new ArrayList<>();
        for (CrawledPage.StoredImage stored : page.getStoredImages()) {
            images.add(saveImage(catalogDetails, namespace, version, layout, resource, stored));
        }
        return new ResourceRecord(resource, images);
    }

    /**
     * The image row is deduplicated by its bytes within the catalog and version, so a logo on five
     * thousand pages is one row, one file and one vector; only the reference row repeats.
     */
    private ResourceRecord.ImageRecord saveImage(CatalogDetails catalogDetails, UUID namespace,
            int version, FileLayout layout, Resource resource, CrawledPage.StoredImage stored) {
        String imageId =
                UuidUtils.nameBased(namespace, version + "|" + stored.getContentHash()).toString();

        Image image = imageWriter.findOrCreate(imageId, () -> {
            Image fresh = new Image();
            fresh.setId(imageId);
            fresh.setCatalogId(catalogDetails.getId());
            fresh.setVersion(version);
            fresh.setContentHash(stored.getContentHash());
            fresh.setFirstSourceUrl(StringUtils.abbreviate(stored.getSourceUrl(), 1000));
            fresh.setImageFilePath(
                    layout.image(imageId, stored.getContentType(), stored.getSourceUrl()));
            fresh.setContentType(stored.getContentType());
            fresh.setWidth(stored.getWidth());
            fresh.setHeight(stored.getHeight());
            fresh.setBytes(stored.getBytes());
            fresh.setCreatedAt(new Date());
            fresh.setUpdatedAt(fresh.getCreatedAt());
            return fresh;
        });

        String referenceId = UuidUtils
                .nameBased(namespace, version + "|" + resource.getId() + "|" + imageId).toString();
        ResourceImage reference = imageWriter.findOrCreateReference(referenceId, () -> {
            ResourceImage fresh = new ResourceImage();
            fresh.setId(referenceId);
            fresh.setCatalogId(catalogDetails.getId());
            fresh.setVersion(version);
            fresh.setResourceId(resource.getId());
            fresh.setImageId(imageId);
            fresh.setSourceUrl(StringUtils.abbreviate(stored.getSourceUrl(), 1000));
            fresh.setAltText(StringUtils.abbreviate(stored.getAlt(), 1000));
            fresh.setTitleText(StringUtils.abbreviate(stored.getTitle(), 1000));
            fresh.setContextText(StringUtils.abbreviate(stored.getContext(), 2000));
            fresh.setCreatedAt(new Date());
            fresh.setUpdatedAt(fresh.getCreatedAt());
            return fresh;
        });
        return new ResourceRecord.ImageRecord(image, reference);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findContentHash(String catalogId, int version, String urlHash) {
        return resourceRepository.findByCatalogIdAndVersionAndUrlHash(catalogId, version, urlHash)
                .map(Resource::getContentHash);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PageState> findPageState(String catalogId, int version, String urlHash) {
        return resourceRepository.findByCatalogIdAndVersionAndUrlHash(catalogId, version, urlHash)
                .map(resource -> new PageState(resource.getContentHash(), resource.getEtag(),
                        resource.getHttpLastModified()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResourceRecord> load(String resourceId) {
        return resourceRepository.findById(resourceId).map(this::attachImages);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResourceRecord> load(String catalogId, int version, int offset, int limit) {
        List<Resource> resources = resourceRepository
                .findByCatalogIdAndVersionOrderByCreatedAtAsc(catalogId, version,
                        PageRequest.of(offset / Math.max(1, limit), Math.max(1, limit)));
        if (resources.isEmpty()) {
            return List.of();
        }
        // one query for the references and one for the images, rather than two per page
        List<ResourceImage> references = resourceImageRepository
                .findByResourceIdIn(resources.stream().map(Resource::getId).toList());
        Map<String, Image> images = imageRepository
                .findAllById(references.stream().map(ResourceImage::getImageId).distinct().toList())
                .stream().collect(Collectors.toMap(Image::getId, Function.identity()));
        Map<String, List<ResourceImage>> byResource =
                references.stream().collect(Collectors.groupingBy(ResourceImage::getResourceId));

        return resources.stream().map(resource -> new ResourceRecord(resource,
                byResource.getOrDefault(resource.getId(), List.of()).stream()
                        .filter(ref -> images.containsKey(ref.getImageId()))
                        .map(ref -> new ResourceRecord.ImageRecord(images.get(ref.getImageId()),
                                ref))
                        .toList()))
                .toList();
    }

    private ResourceRecord attachImages(Resource resource) {
        List<ResourceImage> references = resourceImageRepository.findByResourceId(resource.getId());
        List<ResourceRecord.ImageRecord> images = references.stream()
                .map(ref -> imageRepository.findById(ref.getImageId())
                        .map(image -> new ResourceRecord.ImageRecord(image, ref)).orElse(null))
                .filter(java.util.Objects::nonNull).toList();
        return new ResourceRecord(resource, images);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> getLatestReferencePath(String catalogId, int version) {
        return resourceRepository
                .findByCatalogIdAndVersionOrderByCreatedAtDesc(catalogId, version,
                        PageRequest.of(0, 1))
                .stream().findFirst().map(Resource::getUrl);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByCatalog(String catalogId, int version) {
        return resourceRepository.countByCatalogIdAndVersion(catalogId, version);
    }

    @Override
    @Transactional(readOnly = true)
    public long countImagesByCatalog(String catalogId, int version) {
        return imageRepository.countByCatalogIdAndVersion(catalogId, version);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Integer> findVersions(String catalogId) {
        return resourceRepository.findVersions(catalogId);
    }

    @Override
    @Transactional
    public long deleteByCatalogAndVersion(String catalogId, int version) {
        // references first: they point at both of the other two
        long removed = resourceImageRepository.deleteByCatalogIdAndVersion(catalogId, version);
        removed += imageRepository.deleteByCatalogIdAndVersion(catalogId, version);
        removed += resourceRepository.deleteByCatalogIdAndVersion(catalogId, version);
        return removed;
    }

    @Override
    @Transactional
    public long deleteByCatalog(String catalogId) {
        // references first: they point at both of the other two
        long removed = resourceImageRepository.deleteByCatalogId(catalogId);
        removed += imageRepository.deleteByCatalogId(catalogId);
        removed += resourceRepository.deleteByCatalogId(catalogId);
        return removed;
    }

}
