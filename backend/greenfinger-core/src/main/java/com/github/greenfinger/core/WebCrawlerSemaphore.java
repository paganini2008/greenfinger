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
 * Allows one crawl at a time, in this process and across the cluster.
 *
 * <p>
 * Two crawls of the same catalog would fight over one frontier and one dedup store; two of
 * different catalogs would simply divide the available bandwidth and finish no sooner. 1.x
 * enforced the same rule, in one process, which was all it had to.
 *
 * <h2>Two questions, because there are two ways to break the rule</h2>
 * A permit answers the first: is this process already crawling? It is cheap, it is exact, and it
 * is the only one that matters on a laptop.
 *
 * <p>
 * The catalog table answers the second: is another node already crawling something else? Every
 * node writes its running state there and every node can read it, which makes it the one place
 * the whole cluster already agrees on -- no lock service, no leader, nothing to keep alive. A
 * crawl that is already running on <em>this</em> catalog is not a refusal but the ordinary case
 * of a node joining it, so only a different catalog counts.
 *
 * <p>
 * It is a check rather than a lock, and two commands issued in the same instant on two nodes can
 * both pass it. What that costs is two crawls of different catalogs sharing the bandwidth, which
 * is the thing the rule exists to discourage rather than to make impossible.
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
     * Whether a crawl of this catalog could start, without taking the permit.
     *
     * <p>
     * For a caller that has to answer before the crawl is handed to a background thread: the
     * server returns "started" the moment it accepts the request, so a refusal that happens
     * afterwards is a refusal nobody is told about. This is a look rather than a claim, so two
     * callers can both be told yes and one of them then refused for real -- which is the ordinary
     * shape of a pre-flight check, and better than saying nothing.
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
