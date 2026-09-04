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
import org.rocksdb.RocksIterator;
import com.github.greenfinger.core.WebCrawlerConstants;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.core.utils.UrlUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Url deduplication backed by RocksDB. Exact and durable, unlike the bloom filters 1.x offered,
 * which traded a false positive rate -- silently dropped pages -- for memory the standalone edition
 * does not need to save.
 * 
 * @Description: RocksDbUrlPathFilter
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class RocksDbUrlPathFilter implements ExistingUrlPathFilter {

    private static final byte[] PRESENT = "1".getBytes(StandardCharsets.UTF_8);

    private final RocksDbStore store;
    private final boolean normalize;

    public RocksDbUrlPathFilter(String directory, boolean normalize) {
        this.store = new RocksDbStore(directory);
        this.normalize = normalize;
    }

    @Override
    public String getName() {
        return WebCrawlerConstants.URL_PATH_FILTER_ROCKSDB;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        store.afterPropertiesSet();
    }

    @Override
    public boolean mightExist(String path) {
        try {
            return store.putIfAbsent(canonical(path), PRESENT);
        } catch (Exception e) {
            // Reporting "seen" here would discard the page; reporting "new" at worst re-fetches it.
            throw new WebCrawlerException("Url dedup lookup failed for: " + path, e);
        }
    }

    private String canonical(String path) {
        return normalize ? UrlUtils.normalize(path) : path;
    }

    @Override
    public long size() {
        return store.estimatedKeyCount();
    }

    @Override
    public int export(UrlPathFilterExporter exporter, boolean deleted) throws Exception {
        int n = 0;
        try (RocksIterator iterator = store.newIterator()) {
            iterator.seekToFirst();
            while (iterator.isValid()) {
                byte[] key = iterator.key();
                if (exporter != null
                        && !exporter.doExport(++n, new String(key, StandardCharsets.UTF_8))) {
                    break;
                }
                if (deleted) {
                    store.delete(key);
                }
                iterator.next();
            }
        }
        return n;
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
