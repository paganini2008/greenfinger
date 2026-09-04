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

package com.github.greenfinger.core.component.dedup;

import java.nio.charset.StandardCharsets;
import org.apache.commons.lang3.StringUtils;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.core.utils.HashUtils;

/**
 * Exact content deduplication: two pages are the same document when their normalised text hashes
 * alike. No false positives at all, but a single changed byte -- a view counter, a rotating advert,
 * a rendered timestamp -- makes two copies of one article look unrelated. Use
 * {@link SimHashContentDedupFilter} when that matters.
 * 
 * @Description: Sha256ContentDedupFilter
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class Sha256ContentDedupFilter implements ContentDedupFilter {

    private static final byte[] PRESENT = "1".getBytes(StandardCharsets.UTF_8);

    private final RocksDbStore store;
    private final int minTextLength;

    public Sha256ContentDedupFilter(String directory, int minTextLength) {
        this.store = new RocksDbStore(directory);
        this.minTextLength = minTextLength;
    }

    @Override
    public String getName() {
        return "sha256";
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        store.afterPropertiesSet();
    }

    @Override
    public String fingerprint(String text) {
        String normalized = TextNormalizer.normalize(text);
        return StringUtils.isBlank(normalized) ? null : HashUtils.sha256(normalized);
    }

    @Override
    public boolean isDuplicate(String text) {
        String normalized = TextNormalizer.normalize(text);
        // too short to be distinctive; a shared "page not found" body would swallow the whole site
        if (normalized.length() < minTextLength) {
            return false;
        }
        try {
            return store.putIfAbsent(HashUtils.sha256(normalized), PRESENT);
        } catch (Exception e) {
            throw new WebCrawlerException("Content dedup lookup failed", e);
        }
    }

    @Override
    public long size() {
        return store.estimatedKeyCount();
    }

    @Override
    public void clean() throws Exception {
        store.clean();
    }

    @Override
    public void destroy() throws Exception {
        store.destroy();
    }

}
