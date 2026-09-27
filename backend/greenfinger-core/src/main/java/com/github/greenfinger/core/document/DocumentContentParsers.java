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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * The parsers an installation has, and which one reads a given file.
 *
 * <p>
 * The application's own come first, so a pdf reader somebody added wins over anything shipped with
 * the same name. {@link PlainTextDocumentParser} is always last and always present.
 *
 * @Description: DocumentContentParsers
 * @Author: Fred Feng
 * @Date: 27/09/2026
 * @Version 2.0.0
 */
public class DocumentContentParsers {

    /** Documents a page links to and this crawler knows the name of, whether it can read one yet. */
    public static final Set<String> DOCUMENT_TYPES = Set.of("pdf", "doc", "docx", "xls", "xlsx",
            "ppt", "pptx", "odt", "ods", "odp", "rtf", "epub", "txt", "md", "markdown", "csv",
            "tsv", "log");

    private final List<DocumentContentParser> parsers;

    public DocumentContentParsers(List<DocumentContentParser> configured) {
        List<DocumentContentParser> all = new ArrayList<>(configured != null ? configured
                : List.of());
        all.add(new PlainTextDocumentParser());
        this.parsers = List.copyOf(all);
    }

    public Optional<DocumentContentParser> parserFor(String fileType) {
        return parsers.stream().filter(parser -> parser.supports(fileType)).findFirst();
    }

    /**
     * The text of one file, or empty when nothing here can read that format.
     *
     * <p>
     * Empty rather than an exception: a site linking a pdf is not a broken site, and a crawl that
     * stopped over one would be the wrong answer. What is missing is a parser, which is an
     * installation's decision and is said once at startup rather than once per file.
     */
    public String extractText(String fileType, byte[] content, String url, Charset encoding)
            throws Exception {
        Optional<DocumentContentParser> parser = parserFor(fileType);
        return parser.isPresent() ? StringUtils.defaultString(
                parser.get().extractText(content, url, encoding)) : "";
    }

    /** What this installation can actually read, for the line it prints at startup. */
    public Set<String> readable() {
        Set<String> types = new LinkedHashSet<>();
        parsers.forEach(parser -> types.addAll(parser.fileTypes()));
        return types;
    }

    /** Named in a page's links, and nothing here reads them. */
    public Set<String> unreadable() {
        Set<String> types = new LinkedHashSet<>(DOCUMENT_TYPES);
        types.removeAll(readable());
        return types;
    }

}
