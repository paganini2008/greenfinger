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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 
 * @Description: CrawlTaskTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class CrawlTaskTest {

    @Test
    void seedStartsAtDepthZeroWithNoReferer() {
        CrawlTask seed = CrawlTask.seed("cat-7", CrawlTask.ACTION_CRAWL, "https://a.com",
                "https://a.com/start", "news", "UTF-8", 3);

        assertThat(seed.getCatalogId()).isEqualTo("cat-7");
        assertThat(seed.getReferUrl()).isEqualTo("https://a.com");
        assertThat(seed.getUrl()).isEqualTo("https://a.com/start");
        assertThat(seed.getCat()).isEqualTo("news");
        assertThat(seed.getVersion()).isEqualTo(3);
        assertThat(seed.getDepth()).isZero();
        assertThat(seed.getReferer()).isNull();
        assertThat(seed.getTimestamp()).isPositive();
    }

    @Test
    @DisplayName("a link found on a page is one level deeper and remembers where it came from")
    void childInheritsTheCrawlAndDescends() {
        CrawlTask seed = CrawlTask.seed("cat-7", CrawlTask.ACTION_CRAWL, "https://a.com",
                "https://a.com/start", "news", "UTF-8", 3);
        CrawlTask child = seed.child("https://a.com/next");

        assertThat(child.getDepth()).isEqualTo(1);
        assertThat(child.getReferer()).isEqualTo("https://a.com/start");
        assertThat(child.getUrl()).isEqualTo("https://a.com/next");
        assertThat(child.getCatalogId()).isEqualTo(seed.getCatalogId());
        assertThat(child.getReferUrl()).isEqualTo(seed.getReferUrl());
        assertThat(child.getCat()).isEqualTo(seed.getCat());
        assertThat(child.getVersion()).isEqualTo(seed.getVersion());

        assertThat(child.child("https://a.com/deeper").getDepth()).isEqualTo(2);
    }

}
