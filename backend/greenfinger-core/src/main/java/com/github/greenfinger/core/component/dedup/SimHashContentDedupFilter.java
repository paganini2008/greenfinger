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

import java.nio.ByteBuffer;
import org.apache.commons.lang3.StringUtils;
import com.github.greenfinger.core.WebCrawlerException;
import lombok.extern.slf4j.Slf4j;

/**
 * Near-duplicate content deduplication. Two documents count as the same when their 64 bit simhash
 * fingerprints lie within {@code maxDistance} bits of each other, which catches the same article
 * wrapped in different templates, or with a changing timestamp or advert in the body.
 *
 * <p>
 * Comparing a new fingerprint against every stored one would be linear in the size of the crawl, so
 * fingerprints are indexed by band: the 64 bits are cut into four 16 bit bands and each band value
 * indexes the fingerprints that carry it. Two fingerprints within three bits must agree exactly on
 * at least one band, so looking up four bands finds every real candidate while reading a small
 * fraction of the store.
 * 
 * @Description: SimHashContentDedupFilter
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class SimHashContentDedupFilter implements ContentDedupFilter {

    /**
     * Cap on how many fingerprints one band bucket retains. A pathological bucket would otherwise
     * grow without bound and turn each lookup into a scan.
     */
    private static final int MAX_BUCKET_SIZE = 512;

    private final RocksDbStore store;
    private final int maxDistance;
    private final int minTextLength;

    public SimHashContentDedupFilter(String directory, int maxDistance, int minTextLength) {
        this.store = new RocksDbStore(directory);
        this.maxDistance = maxDistance;
        this.minTextLength = minTextLength;
    }

    @Override
    public String getName() {
        return "simhash";
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (maxDistance >= SimHash.BANDS) {
            log.warn(
                    "simhashDistance {} is not smaller than the band count {}; banded lookup can "
                            + "miss duplicates at that distance. Consider lowering it.",
                    maxDistance, SimHash.BANDS);
        }
        store.afterPropertiesSet();
    }

    @Override
    public String fingerprint(String text) {
        String normalized = TextNormalizer.normalize(text);
        if (StringUtils.isBlank(normalized)) {
            return null;
        }
        return Long.toHexString(SimHash.fingerprint(normalized));
    }

    @Override
    public synchronized boolean isDuplicate(String text) {
        String normalized = TextNormalizer.normalize(text);
        if (normalized.length() < minTextLength) {
            return false;
        }
        long fingerprint = SimHash.fingerprint(normalized);
        try {
            for (int i = 0; i < SimHash.BANDS; i++) {
                long[] bucket = readBucket(bandKey(i, SimHash.band(fingerprint, i)));
                for (long candidate : bucket) {
                    if (SimHash.hammingDistance(fingerprint, candidate) <= maxDistance) {
                        return true;
                    }
                }
            }
            for (int i = 0; i < SimHash.BANDS; i++) {
                appendToBucket(bandKey(i, SimHash.band(fingerprint, i)), fingerprint);
            }
            return false;
        } catch (Exception e) {
            throw new WebCrawlerException("Content dedup lookup failed", e);
        }
    }

    private String bandKey(int index, int bandValue) {
        return "b" + index + ":" + bandValue;
    }

    private long[] readBucket(String key) throws Exception {
        byte[] bytes = store.get(key);
        if (bytes == null || bytes.length == 0) {
            return new long[0];
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long[] values = new long[bytes.length / Long.BYTES];
        for (int i = 0; i < values.length; i++) {
            values[i] = buffer.getLong();
        }
        return values;
    }

    private void appendToBucket(String key, long fingerprint) throws Exception {
        long[] existing = readBucket(key);
        int retained = Math.min(existing.length, MAX_BUCKET_SIZE - 1);
        ByteBuffer buffer = ByteBuffer.allocate((retained + 1) * Long.BYTES);
        // keep the most recent entries; older pages are the least likely to recur
        for (int i = existing.length - retained; i < existing.length; i++) {
            buffer.putLong(existing[i]);
        }
        buffer.putLong(fingerprint);
        store.put(key, buffer.array());
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
