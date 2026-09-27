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

package com.github.greenfinger.core.document;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The seam an application adds a pdf reader through, and the one format that ships.
 *
 * @Description: DocumentContentParserTest
 * @Author: Fred Feng
 * @Date: 27/09/2026
 * @Version 2.0.0
 */
class DocumentContentParserTest {

    private final DocumentContentParsers parsers = new DocumentContentParsers(List.of());

    @Test
    @DisplayName("text formats are read out of the box")
    void textIsRead() throws Exception {
        assertThat(parsers.extractText("md", "# A heading\nand a line".getBytes(
                StandardCharsets.UTF_8), "https://x/readme.md", StandardCharsets.UTF_8))
                        .contains("A heading").contains("and a line");
        assertThat(parsers.extractText("txt", "plain".getBytes(StandardCharsets.UTF_8),
                "https://x/a.txt", StandardCharsets.UTF_8)).isEqualTo("plain");
    }

    @Test
    @DisplayName("bytes the declared charset cannot decode are kept, not dropped")
    void aWrongCharsetIsSurvived() throws Exception {
        // Debian publishes a GB2312 file as charset=utf-8, and a crawl has to survive that: the
        // text comes back with replacement characters rather than an exception, and the warning
        // that names the file is what tells somebody to set the catalog's pageEncoding
        byte[] gb2312 = "\u7231\u5b89\u88c5".getBytes(Charset.forName("GB2312"));
        String text = new PlainTextDocumentParser().extractText(gb2312, "http://host/a.txt",
                StandardCharsets.UTF_8);
        assertThat(text).isNotEmpty().contains("\ufffd");
    }

    @Test
    @DisplayName("the right charset reads the same bytes cleanly")
    void theRightCharsetReadsIt() throws Exception {
        byte[] gb2312 = "\u7231\u5b89\u88c5".getBytes(Charset.forName("GB2312"));
        String text = new PlainTextDocumentParser().extractText(gb2312, "http://host/a.txt",
                Charset.forName("GB2312"));
        assertThat(text).isEqualTo("\u7231\u5b89\u88c5").doesNotContain("\ufffd");
    }

    @Test
    @DisplayName("nothing to read is empty rather than an exception")
    void emptyContentIsEmpty() throws Exception {
        PlainTextDocumentParser parser = new PlainTextDocumentParser();
        assertThat(parser.extractText(null, "http://host/a.txt", StandardCharsets.UTF_8)).isEmpty();
        assertThat(parser.extractText(new byte[0], "http://host/a.txt", null)).isEmpty();
    }

    @Test
    @DisplayName("a format nothing reads is empty, not an exception")
    void anUnreadFormatIsEmpty() throws Exception {
        // a site linking a pdf is not a broken site, and a crawl that stopped over one would be
        // the wrong answer
        assertThat(parsers.extractText("pdf", new byte[] {1, 2, 3}, "https://x/a.pdf",
                StandardCharsets.UTF_8)).isEmpty();
        assertThat(parsers.parserFor("pdf")).isEmpty();
        assertThat(parsers.unreadable()).contains("pdf", "docx", "xlsx");
    }

    @Test
    @DisplayName("an application's own parser wins, and is all it takes")
    void anApplicationCanAddOne() throws Exception {
        DocumentContentParsers withPdf = new DocumentContentParsers(List.of(
                new DocumentContentParser() {

                    @Override
                    public Set<String> fileTypes() {
                        return Set.of("pdf");
                    }

                    @Override
                    public String extractText(byte[] content, String url, Charset encoding) {
                        return "the handbook";
                    }
                }));

        assertThat(withPdf.extractText("pdf", new byte[0], "https://x/a.pdf",
                StandardCharsets.UTF_8)).isEqualTo("the handbook");
        assertThat(withPdf.readable()).contains("pdf", "md");
        assertThat(withPdf.unreadable()).doesNotContain("pdf");
    }

}
