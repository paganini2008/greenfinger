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

import java.util.Map;
import java.util.Optional;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.utils.HttpUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Fetches a linked file as bytes, because a pdf is not markup and the page extractor would refuse
 * it on sight.
 *
 * <p>
 * Deliberately small and separate from the extractors: no rendering, no conditional get, no link
 * discovery -- a file is a leaf. The cap matters more than it does for a page, because a parser
 * holds the whole thing in memory.
 *
 * @Description: DocumentFetcher
 * @Author: Fred Feng
 * @Date: 27/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class DocumentFetcher {

    /** How long to wait for the bytes of one document. The connect timeout is the shared one. */
    private static final int RESPONSE_TIMEOUT = 60_000;

    private final WebCrawlerProperties.Document config;

    public DocumentFetcher(WebCrawlerProperties.Document config) {
        this.config = config;
    }

    /** The bytes, or empty when the answer was not one worth reading. Never throws for a file. */
    public Optional<byte[]> fetch(String url, String referer) {
        try {
            HttpUtils.Reply reply = HttpUtils.get(url, RESPONSE_TIMEOUT,
                    Map.of("Referer", referer != null ? referer : ""));
            if (!reply.isOk()) {
                return Optional.empty();
            }
            byte[] bytes = reply.body();
            if (bytes.length == 0) {
                return Optional.empty();
            }
            if (config.getMaxBytes() > 0 && bytes.length > config.getMaxBytes()) {
                log.info("{} is {} bytes, past the {} a document may be; not read", url,
                        bytes.length, config.getMaxBytes());
                return Optional.empty();
            }
            return Optional.of(bytes);
        } catch (Exception e) {
            log.warn("Could not fetch '{}': {}", url, e.getMessage());
            return Optional.empty();
        }
    }

}
