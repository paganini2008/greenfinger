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

package com.github.greenfinger.cluster.replication;

import org.springframework.transaction.annotation.Transactional;
import com.github.greenfinger.core.model.Image;
import com.github.greenfinger.core.model.Resource;
import com.github.greenfinger.core.model.ResourceImage;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.record.ImageRepository;
import com.github.greenfinger.record.ResourceImageRepository;
import com.github.greenfinger.record.ResourceRepository;
import lombok.RequiredArgsConstructor;

/**
 * Writes a replicated row, if it is not already the row that is there.
 *
 * <p>
 * Straight to the repositories, deliberately. Going back through
 * {@link com.github.greenfinger.core.record.ResourceRecordStore#save} would derive the ids from the
 * page all over again -- the same ids, since they are deterministic, but from data this node does
 * not have -- and, being the replicating decorator, would announce the row a second time.
 * 
 * @Description: JpaRowWriter
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@RequiredArgsConstructor
public class JpaRowWriter implements ReplicatedRecordStore.RowWriter {

    private final ResourceRepository resourceRepository;
    private final ImageRepository imageRepository;
    private final ResourceImageRepository resourceImageRepository;
    private final ResourceRecordStore recordStore;

    @Override
    @Transactional
    public void resource(Resource resource) {
        // absent, or saying something different. Not "absent" alone: a refresh really does change
        // a row, and skipping it would leave every other node serving the old content
        Resource stored = resourceRepository.findById(resource.getId()).orElse(null);
        if (ReplicatedRecordStore.differs(stored, resource)) {
            resourceRepository.save(resource);
        }
    }

    @Override
    @Transactional
    public void image(Image image) {
        // an image row is immutable: its id is the hash of its bytes, so present means identical
        if (!imageRepository.existsById(image.getId())) {
            imageRepository.save(image);
        }
    }

    @Override
    @Transactional
    public void reference(ResourceImage reference) {
        if (!resourceImageRepository.existsById(reference.getId())) {
            resourceImageRepository.save(reference);
        }
    }

    @Override
    public void deleteVersion(String catalogId, int version) {
        // through the store rather than the repositories: deleting a version is three deletes in
        // an order that matters, and that order is already written down once
        recordStore.deleteByCatalogAndVersion(catalogId, version);
    }

    @Override
    public void deleteCatalog(String catalogId) {
        recordStore.deleteByCatalog(catalogId);
    }

}
