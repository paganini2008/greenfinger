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

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * The formats that are already text: txt, markdown, csv and the log files beside them.
 *
 * <p>
 * The only parser that ships. It needs no dependency, which is the reason it can: a pdf reader is
 * a decision about licences and memory that belongs to the application, and
 * {@link DocumentContentParser} is where that decision plugs in.
 *
 * <p>
 * Markdown is handed over as it is rather than rendered. The marks are punctuation an index
 * ignores, and a heading is still the sentence it reads as.
 *
 * @Description: PlainTextDocumentParser
 * @Author: Fred Feng
 * @Date: 27/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class PlainTextDocumentParser implements DocumentContentParser {

    static final Set<String> TYPES = Set.of("txt", "md", "markdown", "csv", "tsv", "log", "text");

    @Override
    public String getName() {
        return "plaintext";
    }

    @Override
    public Set<String> fileTypes() {
        return TYPES;
    }

    /**
     * Decoded strictly first, so that bytes which are not in this charset are said out loud.
     *
     * <p>
     * A plain text file carries no charset of its own: html has a meta tag the extractor sniffs,
     * and this has nothing. What arrives is what the catalog was configured with, and a server can
     * declare the wrong one -- Debian publishes a GB2312 dedication as {@code charset=utf-8}.
     * Decoded leniently that turns into several hundred U+FFFD and no error, which is a page stored
     * and indexed as noise. The lenient decode still happens, because half a document is a better
     * result than a failed crawl, but the log names the file and the charset that did not fit so
     * the catalog's {@code pageEncoding} can be pointed at the right one.
     */
    @Override
    public String extractText(byte[] content, String url, Charset encoding) {
        if (content == null || content.length == 0) {
            return "";
        }
        Charset charset = encoding != null ? encoding : StandardCharsets.UTF_8;
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            log.warn("'{}' is not {}: the text is kept with the bytes that did not decode replaced."
                    + " Set the catalog's pageEncoding if the server declares the wrong charset.",
                    url, charset.name());
            return new String(content, charset);
        }
    }

}
