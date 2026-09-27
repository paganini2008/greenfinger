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

package com.github.greenfinger.core.component.acceptor;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Which links are a page and which are a file somebody linked to.
 *
 * @Description: AssetUrlPathAcceptorTest
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
class AssetUrlPathAcceptorTest {

    private final AssetUrlPathAcceptor acceptor = new AssetUrlPathAcceptor();

    @ParameterizedTest
    @ValueSource(strings = {"https://simplefood.blog/wp-content/uploads/2013/05/biscuits.jpg",
            "https://example.com/a/b/poster.PNG", "https://example.com/handbook.pdf",
            "https://example.com/build/app.js", "https://example.com/style.css",
            "https://example.com/release.tar.gz", "https://example.com/talk.mp4"})
    @DisplayName("a link to a file is not followed")
    void filesAreNotPages(String url) {
        assertThat(acceptor.accept(null, null, url, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://simplefood.blog/2020/05/peanut-biscuits/",
            "https://example.com/", "https://example.com/about",
            "https://example.com/index.html", "https://example.com/page.htm",
            "https://example.com/v1.2/getting-started", "https://example.com/article.php",
            "https://example.com/list.do", "https://example.com/feed.xml"})
    @DisplayName("a page is followed, including the ones with a dot in the path")
    void pagesAreFollowed(String url) {
        assertThat(acceptor.accept(null, null, url, null)).isTrue();
    }

    @Test
    @DisplayName("the query is not part of the extension, in either direction")
    void theQueryIsNotTheExtension() {
        // a picture with a cache buster is still a picture
        assertThat(acceptor.accept(null, null, "https://example.com/photo.jpg?v=2", null))
                .isFalse();
        // and a page that hands one out is still a page
        assertThat(acceptor.accept(null, null, "https://example.com/download?file=x.jpg", null))
                .isTrue();
        assertThat(acceptor.accept(null, null, "https://example.com/photo.jpg#top", null))
                .isFalse();
    }

    @Test
    @DisplayName("the list can be replaced, and turned off")
    void theListIsTheInstallation() {
        AssetUrlPathAcceptor onlyZip = new AssetUrlPathAcceptor("zip, .rar");
        assertThat(onlyZip.accept(null, null, "https://example.com/a.zip", null)).isFalse();
        // a jpg is a page now, because the installation said so
        assertThat(onlyZip.accept(null, null, "https://example.com/a.jpg", null)).isTrue();

        AssetUrlPathAcceptor off = new AssetUrlPathAcceptor("none");
        assertThat(off.accept(null, null, "https://example.com/a.jpg", null)).isTrue();

        // blank keeps the built-in list rather than turning the check off by accident
        assertThat(new AssetUrlPathAcceptor("  ").accept(null, null, "https://x.com/a.jpg", null))
                .isFalse();
    }

    @Test
    @DisplayName("it runs before anything that costs a request or a regex")
    void itIsCheapAndEarly() {
        assertThat(acceptor.getOrder())
                .isLessThan(new MaxFetchDepthUrlPathAcceptor().getOrder());
        assertThat(acceptor.getName()).isEqualTo("asset");
    }

}
