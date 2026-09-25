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

package com.github.greenfinger.core;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.model.Catalog;

/**
 * Allows one crawl at a time, in this process and across the cluster: two crawls of one catalog
 * would fight over the frontier and dedup store, two of different catalogs just split the
 * bandwidth.
 *
 * <p>
 * Two questions. A local permit answers "is this process already crawling", which is all a laptop
 * needs. The catalog table answers "is another node crawling something else" -- every node writes
 * its running state there, so no lock service or leader is involved; the same catalog is not a
 * refusal but a node joining. A check, not a lock: two simultaneous commands can both pass, which
 * costs shared bandwidth, the thing the rule discourages rather than forbids.
 * 
 * @Description: WebCrawlerSemaphore
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public final class WebCrawlerSemaphore {

    private final Semaphore semaphore = new Semaphore(1);
    private final AtomicReference<String> catalogId = new AtomicReference<>();

    /**
     * Null when there is no catalog table to ask -- a test, or a run assembled by hand. Then the
     * permit is the whole rule, which is what it was before there was a cluster.
     */
    private final CatalogStore catalogStore;

    public WebCrawlerSemaphore() {
        this(null);
    }

    public WebCrawlerSemaphore(CatalogStore catalogStore) {
        this.catalogStore = catalogStore;
    }

    public String getCatalogId() {
        return catalogId.get();
    }

    public boolean acquire(String catalogId) {
        return acquire(catalogId, 3, TimeUnit.SECONDS);
    }

    public boolean acquire(String catalogId, long timeout, TimeUnit timeUnit) {
        try {
            if (!semaphore.tryAcquire(timeout, timeUnit)) {
                return false;
            }
            if (isRunningElsewhere(catalogId)) {
                // taken and given straight back: the permit has to be held to ask the second
                // question, or two threads here would both ask it and both be told yes
                semaphore.release();
                return false;
            }
            this.catalogId.set(catalogId);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Whether some other catalog is being crawled, by this node or any other.
     *
     * <p>
     * A failure to read is not a refusal. The permit has already said this process is free, and
     * refusing a crawl because the database was briefly unreachable would be a worse answer than
     * allowing a second one.
     */
    private boolean isRunningElsewhere(String catalogId) {
        if (catalogStore == null) {
            return false;
        }
        try {
            List<Catalog> running = catalogStore.findRunning();
            return running.stream().anyMatch(catalog -> !catalog.getId().equals(catalogId));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Whether a crawl could start, without taking the permit -- for callers that must answer before
     * the crawl goes to a background thread, since a later refusal reaches nobody. A look, not a
     * claim: two callers can both be told yes and one then refused for real.
     */
    public boolean available(String catalogId) {
        return !isOccupied() && !isRunningElsewhere(catalogId);
    }

    /** Which catalog is being crawled anywhere, this node included. Empty when none is. */
    public List<Catalog> running() {
        if (catalogStore == null) {
            return List.of();
        }
        try {
            return catalogStore.findRunning();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    public void release() {
        if (isOccupied()) {
            catalogId.set(null);
            semaphore.release();
        }
    }

    public boolean isOccupied() {
        return semaphore.availablePermits() == 0;
    }

}
