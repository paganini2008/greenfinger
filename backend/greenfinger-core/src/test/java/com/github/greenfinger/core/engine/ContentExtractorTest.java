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
import org.jsoup.Jsoup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 
 * @Description: ContentExtractorTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
class ContentExtractorTest {

    private final ContentExtractor extractor = new ContentExtractor(true, 200, 180);

    private static String prose(String subject) {
        return ("The " + subject + " runs to several sentences and reads like something a person "
                + "wrote on purpose, which is exactly what an article looks like and exactly what "
                + "a navigation bar does not. ").repeat(3);
    }

    @Test
    @DisplayName("navigation and footers are dropped, the article is kept")
    void keepsTheArticleAndDropsTheFurniture() {
        String html = "<html><body>"
                + "<nav><a href='/a'>Home</a><a href='/b'>About</a><a href='/c'>Contact</a></nav>"
                + "<div class='sidebar'><a href='/x'>Related one</a><a href='/y'>Related two</a>"
                + "</div>"
                + "<article><p>" + prose("article") + "</p></article>"
                + "<footer>Copyright 2026, all rights reserved, every last one of them.</footer>"
                + "</body></html>";

        String text = extractor.extract(Jsoup.parse(html));

        assertThat(text).contains("article runs to several sentences");
        assertThat(text).doesNotContain("Home").doesNotContain("Copyright")
                .doesNotContain("Related one");
    }

    @Test
    @DisplayName("an explicit <main> settles it without any scoring")
    void prefersAnExplicitMain() {
        String html = "<html><body>"
                + "<div class='content'><p>" + prose("decoy") + "</p></div>"
                + "<main><p>" + prose("real thing") + "</p></main>"
                + "</body></html>";

        assertThat(extractor.extract(Jsoup.parse(html))).contains("real thing runs to several");
    }

    @Test
    @DisplayName("a block that is mostly links is a menu, however long it is")
    void rejectsBlocksThatAreMostlyLinks() {
        StringBuilder menu = new StringBuilder("<div class='wrapper'>");
        for (int i = 0; i < 60; i++) {
            menu.append("<a href='/p").append(i).append("'>Some category name here</a> ");
        }
        menu.append("</div>");

        String html = "<html><body>" + menu + "<div><p>" + prose("article") + "</p></div>"
                + "</body></html>";

        String text = extractor.extract(Jsoup.parse(html));
        assertThat(text).contains("article runs to several");
        assertThat(text).doesNotContain("Some category name here");
    }

    @Test
    @DisplayName("scripts and styles never reach the index")
    void stripsScriptsAndStyles() {
        String html = "<html><body><script>var tracking = 'do not index me';</script>"
                + "<style>.a { color: red }</style>"
                + "<article><p>" + prose("article") + "</p></article></body></html>";

        String text = extractor.extract(Jsoup.parse(html));
        assertThat(text).doesNotContain("do not index me").doesNotContain("color: red");
    }

    @Test
    @DisplayName("a listing has no article to find, so the whole body is kept")
    void fallsBackWhenNothingLooksLikeAnArticle() {
        String html = "<html><body><ul>"
                + "<li><a href='/1'>First book title</a></li>"
                + "<li><a href='/2'>Second book title</a></li>"
                + "</ul></body></html>";

        String text = extractor.extract(Jsoup.parse(html));
        assertThat(text).contains("First book title").contains("Second book title");
    }

    @Test
    void anEmptyDocumentIsEmpty() {
        assertThat(extractor.extract(Jsoup.parse("<html><body></body></html>"))).isEmpty();
    }

    @Test
    @DisplayName("turning it off keeps everything, furniture included")
    void canBeTurnedOff() {
        ContentExtractor off = new ContentExtractor(false, 200, 180);
        String html = "<html><body><nav>Home About Contact</nav>"
                + "<article><p>" + prose("article") + "</p></article></body></html>";

        assertThat(off.extract(Jsoup.parse(html))).contains("Home About Contact");
    }

    @Test
    @DisplayName("a class name saying 'comments' is not the article, even when it reads like one")
    void demotesBlocksThatNameThemselvesFurniture() {
        String html = "<html><body>"
                + "<div class='comments'><p>" + prose("comment thread") + "</p></div>"
                + "<div class='post-body'><p>" + prose("article") + "</p></div>"
                + "</body></html>";

        assertThat(extractor.extract(Jsoup.parse(html)))
                .contains("article runs to several sentences");
    }

    @Test
    @DisplayName("works the same in Chinese, because it never looks at the words")
    void isLanguageAgnostic() {
        String chinese = "这是一篇正文，写了好几句话，读起来像是有人认真写出来的东西，"
                + "而不是导航栏里的链接文字。".repeat(6);
        String html = "<html><body><nav><a href='/a'>首页</a><a href='/b'>关于</a></nav>"
                + "<article><p>" + chinese + "</p></article></body></html>";

        String text = extractor.extract(Jsoup.parse(html));
        assertThat(text).contains("这是一篇正文");
        assertThat(text).doesNotContain("首页");
    }

    @Test
    @DisplayName("a threshold in characters is a threshold in English, so CJK is weighted")
    void countsCjkByHowMuchWasSaid() {
        // the same number of characters says far more in Chinese than in English
        assertThat(ContentExtractor.weightedLength("这是一篇正文"))
                .isGreaterThan(ContentExtractor.weightedLength("an article"));
        assertThat(ContentExtractor.weightedLength("abcdefghij")).isEqualTo(10);
        assertThat(ContentExtractor.weightedLength("")).isZero();
        assertThat(ContentExtractor.weightedLength(null)).isZero();
    }

    @Test
    void reportsWhatItStrips() {
        assertThat(extractor.strippedTags()).contains("script", "style", "nav", "footer");
    }

}
