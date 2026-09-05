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

package com.github.greenfinger.api.web;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.Image;
import com.github.greenfinger.core.model.Resource;
import com.github.greenfinger.core.model.ResourceImage;
import com.github.greenfinger.record.ImageRepository;
import com.github.greenfinger.record.ResourceImageRepository;
import com.github.greenfinger.record.ResourceRepository;
import com.github.greenfinger.service.CatalogAdminService;
import lombok.RequiredArgsConstructor;

/**
 * What a crawl stored, to be looked through rather than searched.
 *
 * <p>
 * {@code SearchApiController} answers "which page is about this", ranked, from the version being
 * served. This answers "what did I actually get", in crawl order, for any version -- including
 * one that was never published, which the search index by definition cannot show. A crawl that
 * came back wrong is diagnosed here, not there.
 *
 * <p>
 * Metadata only. The page itself is on the site it came from and the row carries its url; serving
 * a stored copy back would mean reading a file per row and would make this a mirror rather than a
 * record of what was fetched.
 *
 * @Description: ResourceApiController
 * @Author: Fred Feng
 * @Date: 05/09/2026
 * @Version 2.0.0
 */
@RestController
@RequestMapping("${greenfinger.api.prefix:/v2}/resource")
@RequiredArgsConstructor
public class ResourceApiController {

    /** A page of rows large enough to scroll and small enough that no filter is a mistake. */
    private static final int DEFAULT_SIZE = 25;
    private static final int MAX_SIZE = 200;

    private final ResourceRepository resourceRepository;
    private final ResourceImageRepository resourceImageRepository;
    private final ImageRepository imageRepository;
    private final CatalogAdminService catalogAdminService;

    /**
     * @param idOrName which catalog, by id or by name. Required: "every resource in the database"
     *        is not a question anybody asks and is a table scan for whoever tries.
     * @param version a single version, or omitted for all of them.
     * @param q matched against the url and the title together.
     * @param from crawled at or after, as {@code yyyy-MM-dd} or a full ISO instant.
     * @param to crawled at or before.
     */
    @GetMapping
    public ApiResult<ResourcePage> browse(@RequestParam("catalogId") String idOrName,
            @RequestParam(value = "version", required = false) Integer version,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "from",
                    required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date from,
            @RequestParam(value = "to",
                    required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "" + DEFAULT_SIZE) int size,
            @RequestParam(value = "sort", defaultValue = "desc") String sort) {
        Catalog catalog = catalogAdminService.require(idOrName);
        Sort.Direction direction =
                "asc".equalsIgnoreCase(sort) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(Math.max(0, page),
                Math.min(MAX_SIZE, Math.max(1, size)), Sort.by(direction, "createdAt"));
        // lowered and wrapped here rather than in the query: a blank box means no filter, and
        // pushing that decision into jpql would make the null check a string comparison
        String keyword = StringUtils.isBlank(q) ? null
                : "%" + q.trim().toLowerCase() + "%";
        Page<Resource> found = resourceRepository.browse(catalog.getId(), version, keyword, from,
                to, pageable);
        return ApiResult.ok(new ResourcePage(withImages(found.getContent()),
                found.getTotalElements(), found.getTotalPages(), pageable.getPageNumber(),
                pageable.getPageSize()));
    }

    /**
     * Attaches each row's images, in two queries for the whole page rather than two per row.
     *
     * <p>
     * Carried with the list rather than fetched when a row is opened, because the count is part of
     * what somebody is scanning the list for -- a page that was supposed to have pictures and has
     * none is the thing they came to find, and it cannot be seen if every row has to be clicked.
     *
     * <p>
     * An image is stored once per catalog and version however many pages point at it, so the two
     * urls on each row are genuinely two different things: {@code sourceUrl} is what this page
     * asked for, and the stored file may have arrived from a different page's url first.
     */
    private List<ResourceRow> withImages(List<Resource> resources) {
        if (resources.isEmpty()) {
            return List.of();
        }
        List<ResourceImage> references = resourceImageRepository
                .findByResourceIdIn(resources.stream().map(Resource::getId).toList());
        if (references.isEmpty()) {
            return resources.stream().map(resource -> new ResourceRow(resource, List.of()))
                    .toList();
        }
        Map<String, Image> images = imageRepository
                .findAllById(references.stream().map(ResourceImage::getImageId).distinct().toList())
                .stream().collect(Collectors.toMap(Image::getId, Function.identity()));
        Map<String, List<ResourceImage>> byResource =
                references.stream().collect(Collectors.groupingBy(ResourceImage::getResourceId));
        return resources.stream()
                .map(resource -> new ResourceRow(resource,
                        byResource.getOrDefault(resource.getId(), List.of()).stream()
                                .filter(reference -> images.containsKey(reference.getImageId()))
                                .map(reference -> view(images.get(reference.getImageId()),
                                        reference))
                                .toList()))
                .toList();
    }

    private ResourceImageView view(Image image, ResourceImage reference) {
        return new ResourceImageView(image.getId(), reference.getSourceUrl(),
                image.getFirstSourceUrl(), image.getImageFilePath(), image.getContentType(),
                image.getWidth(), image.getHeight(), image.getBytes(), reference.getAltText());
    }

    /** The versions this catalog actually has rows for, which is what the filter offers. */
    @GetMapping("/versions")
    public ApiResult<List<Integer>> versions(@RequestParam("catalogId") String idOrName) {
        Catalog catalog = catalogAdminService.require(idOrName);
        return ApiResult.ok(resourceRepository.findVersions(catalog.getId()));
    }

    /**
     * 
     * @Description: ResourcePage
     * @Author: Fred Feng
     * @Date: 05/09/2026
     * @Version 2.0.0
     */
    public record ResourcePage(List<ResourceRow> results, long total, int totalPages, int page,
            int pageSize) {
    }

    /**
     * A row, flattened.
     *
     * <p>
     * {@code JsonUnwrapped} so the resource's own fields sit at the top level beside
     * {@code images}, rather than under a {@code resource} key: the list is a table of resources
     * that happen to carry pictures, not a list of pairs.
     * 
     * @Description: ResourceRow
     * @Author: Fred Feng
     * @Date: 05/09/2026
     * @Version 2.0.0
     */
    public record ResourceRow(@JsonUnwrapped Resource resource, List<ResourceImageView> images) {
    }

    /**
     * One picture on one page.
     *
     * @param sourceUrl what this page pointed at.
     * @param firstSourceUrl where the bytes were first found, which is the same url unless another
     *        page got there first -- an image is stored once per catalog and version however many
     *        pages carry it.
     * @param filePath where it is in the blob store; feed it to the image endpoint to see it.
     * 
     * @Description: ResourceImageView
     * @Author: Fred Feng
     * @Date: 05/09/2026
     * @Version 2.0.0
     */
    public record ResourceImageView(String imageId, String sourceUrl, String firstSourceUrl,
            String filePath, String contentType, Integer width, Integer height, Long bytes,
            String altText) {
    }

}
