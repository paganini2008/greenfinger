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

import java.nio.charset.Charset;
import java.util.Set;
import com.github.greenfinger.core.component.WebCrawlerComponent;

/**
 * Turns a file a page linked to into text, so a pdf can be searched like a page.
 *
 * <p>
 * A site is not only its html. A handbook is a pdf, a price list is a spreadsheet, a release note
 * is a markdown file, and a crawl that follows only {@code <a>} into more html walks past all of
 * it. {@link com.github.greenfinger.core.engine.PageParser#extractDownloadedFiles} collects those
 * links; this is what reads one.
 *
 * <p>
 * <b>Only txt and markdown are parsed out of the box</b>, by {@link PlainTextDocumentParser}. Pdf,
 * Word and Excel each mean another dependency with its own licence and its own opinions about
 * memory, and that is a choice for the application rather than for the crawler. Implement this,
 * publish it as a bean, and those files become text the same way html does:
 *
 * <pre>
 * &#64;Bean
 * DocumentContentParser pdfParser() {
 *     return new DocumentContentParser() {
 *         public Set&lt;String&gt; fileTypes() {
 *             return Set.of("pdf");
 *         }
 *
 *         public String extractText(byte[] content, String url, Charset encoding) {
 *             return new Tika().parseToString(new ByteArrayInputStream(content));
 *         }
 *     };
 * }
 * </pre>
 *
 * @Description: DocumentContentParser
 * @Author: Fred Feng
 * @Date: 27/09/2026
 * @Version 2.0.0
 */
public interface DocumentContentParser extends WebCrawlerComponent {

    /** The extensions this reads, lower case and without the dot: {@code pdf}, {@code docx}. */
    Set<String> fileTypes();

    /**
     * The text inside, or empty when there is none to be had. Never null, and never the bytes
     * decoded blindly: what the outputs index has to be text somebody could read.
     *
     * @param content  the file as it was fetched
     * @param url      where it came from, for messages
     * @param encoding the catalog's encoding, for the formats that do not carry their own
     */
    String extractText(byte[] content, String url, Charset encoding) throws Exception;

    default boolean supports(String fileType) {
        return fileType != null && fileTypes().contains(fileType.toLowerCase());
    }

}
