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

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.test.context.TestPropertySource;
import com.github.greenfinger.core.model.Image;
import com.github.greenfinger.core.model.Resource;
import com.github.greenfinger.core.model.ResourceImage;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.record.ImageWriter;
import com.github.greenfinger.record.ImageRepository;
import com.github.greenfinger.record.JpaResourceRecordStore;
import com.github.greenfinger.record.ResourceImageRepository;
import com.github.greenfinger.record.ResourceRepository;

/**
 * Writing a row that arrived from another node, against a real database.
 *
 * <p>
 * Against a real one because what is under test is the check that comes before the write, and
 * "does this row exist and does it say the same thing" is not a question a double can answer
 * honestly.
 * 
 * @Description: JpaRowWriterTest
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@DataJpaTest
@EntityScan(basePackages = "com.github.greenfinger.core.model")
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
class JpaRowWriterTest {

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private ResourceImageRepository resourceImageRepository;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    /** Built by hand rather than imported: the record configuration brings a good deal with it. */
    private ResourceRecordStore recordStore() {
        return new JpaResourceRecordStore(resourceRepository, imageRepository,
                resourceImageRepository,
                new ImageWriter(imageRepository, resourceImageRepository, transactionManager));
    }

    private JpaRowWriter writer() {
        return new JpaRowWriter(resourceRepository, imageRepository, resourceImageRepository,
                recordStore());
    }

    @Test
    @DisplayName("a row that is not here is written")
    void anAbsentRowIsWritten() {
        writer().resource(resource("res-1", "hash-1"));

        assertThat(resourceRepository.findById("res-1")).isPresent();
    }

    @Test
    @DisplayName("a row that says something different is updated, because a refresh changes rows")
    void aChangedRowIsUpdated() {
        JpaRowWriter writer = writer();
        writer.resource(resource("res-2", "hash-1"));

        writer.resource(resource("res-2", "hash-2"));

        assertThat(resourceRepository.findById("res-2")).get()
                .extracting(Resource::getContentHash).isEqualTo("hash-2");
    }

    @Test
    @DisplayName("an image row is immutable, so present means identical and nothing is rewritten")
    void imagesAreWrittenOnce() {
        JpaRowWriter writer = writer();
        Image image = new Image();
        image.setId("img-1");
        image.setCatalogId("cat-1");
        image.setVersion(0);
        image.setContentHash("bytes-hash");
        image.setImageFilePath("cat-1/v0/images/ab/cd/img-1.jpg");
        image.setContentType("image/jpeg");
        image.setCreatedAt(new Date());
        writer.image(image);
        writer.image(image);

        assertThat(imageRepository.count()).isEqualTo(1);
    }

    @Test
    void referencesAreWrittenOnce() {
        JpaRowWriter writer = writer();
        ResourceImage reference = new ResourceImage();
        reference.setId("ref-1");
        reference.setCatalogId("cat-1");
        reference.setVersion(0);
        reference.setResourceId("res-1");
        reference.setImageId("img-1");
        reference.setSourceUrl("https://example.com/a.jpg");
        reference.setCreatedAt(new Date());
        writer.reference(reference);
        writer.reference(reference);

        assertThat(resourceImageRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a delete goes through the store, which knows the order the three tables need")
    void deletingAVersionGoesThroughTheStore() {
        JpaRowWriter writer = writer();
        writer.resource(resource("res-3", "hash-1"));

        writer.deleteVersion("cat-1", 0);

        assertThat(resourceRepository.findAll()).noneMatch(r -> "res-3".equals(r.getId()));
    }

    private static Resource resource(String id, String contentHash) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setCatalogId("cat-1");
        resource.setVersion(0);
        resource.setUrl("https://example.com/" + id);
        resource.setUrlHash("url-" + id);
        resource.setCat("default");
        resource.setContentHash(contentHash);
        resource.setCreatedAt(new Date());
        return resource;
    }

}
