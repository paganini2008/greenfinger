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

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import com.github.greenfinger.core.model.Resource;

/**
 * The Resources page's query, filter by filter.
 *
 * <p>
 * Four optional filters make sixteen combinations and one statement, which is the point of
 * writing it that way -- and the reason it needs covering as a whole rather than at its edges.
 * Every case here goes to a real database, because what the query has to survive is not Java: it
 * is what each server makes of a parameter that may be null, and that only shows up when the
 * statement is prepared.
 *
 * @Description: ResourceBrowseTest
 * @Author: Fred Feng
 * @Date: 23/09/2026
 * @Version 2.0.0
 */
@DataJpaTest
@EntityScan(basePackages = "com.github.greenfinger.core.model")
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
class ResourceBrowseTest {

    private static final String CATALOG = "catalog-1";

    @Autowired
    private ResourceRepository resourceRepository;

    private Date early;
    private Date late;

    @BeforeEach
    void setUp() {
        resourceRepository.deleteAll();
        early = new Date(1_600_000_000_000L);
        late = new Date(1_700_000_000_000L);
        save(0, "https://example.com/rust/memory", "Memory safety", early);
        save(0, "https://example.com/rust/threads", "Fearless concurrency", early);
        save(1, "https://example.com/rust/memory", "Memory safety", late);
        save(1, "https://elsewhere.test/news", "Something else", late);
    }

    private void save(int version, String url, String title, Date at) {
        Resource resource = new Resource();
        resource.setId(UUID.randomUUID().toString());
        resource.setCatalogId(CATALOG);
        resource.setVersion(version);
        resource.setUrl(url);
        resource.setUrlHash(UUID.randomUUID().toString());
        resource.setTitle(title);
        resource.setCat("tech");
        resource.setCreatedAt(at);
        resource.setUpdatedAt(at);
        resourceRepository.save(resource);
    }

    private Page<Resource> browse(Integer version, String keyword, Date from, Date to) {
        return resourceRepository.browse(CATALOG, version, keyword, from, to,
                PageRequest.of(0, 50));
    }

    @Test
    @DisplayName("no filter at all is the whole catalog")
    void everything() {
        assertThat(browse(null, null, null, null).getTotalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("a version on its own")
    void byVersion() {
        assertThat(browse(0, null, null, null).getContent()).hasSize(2)
                .allMatch(one -> one.getVersion() == 0);
    }

    @Test
    @DisplayName("a keyword matches the url or the title, either case")
    void byKeyword() {
        assertThat(browse(null, "%memory%", null, null).getContent()).hasSize(2);
        assertThat(browse(null, "%fearless%", null, null).getContent()).hasSize(1);
        assertThat(browse(null, "%nothing-like-this%", null, null).getContent()).isEmpty();
    }

    @Test
    @DisplayName("a date at either end, or both")
    void byDate() {
        assertThat(browse(null, null, new Date(late.getTime() - 1), null).getContent()).hasSize(2);
        assertThat(browse(null, null, null, new Date(early.getTime() + 1)).getContent())
                .hasSize(2);
        assertThat(browse(null, null, new Date(early.getTime() - 1),
                new Date(late.getTime() + 1)).getContent()).hasSize(4);
    }

    @Test
    @DisplayName("all four at once, which is where a null parameter has no company to be typed by")
    void allFour() {
        Page<Resource> found = browse(1, "%memory%", new Date(late.getTime() - 1),
                new Date(late.getTime() + 1));

        assertThat(found.getContent()).hasSize(1);
        assertThat(found.getContent().get(0).getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("another catalog's rows are not in it")
    void onlyThisCatalog() {
        Resource other = new Resource();
        other.setId(UUID.randomUUID().toString());
        other.setCatalogId("catalog-2");
        other.setVersion(0);
        other.setUrl("https://example.com/rust/memory");
        other.setUrlHash(UUID.randomUUID().toString());
        other.setTitle("Memory safety");
        other.setCat("tech");
        other.setCreatedAt(early);
        other.setUpdatedAt(early);
        resourceRepository.save(other);

        assertThat(browse(null, null, null, null).getTotalElements()).isEqualTo(4);
    }

}
