package com.github.greenfinger;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @Description: WebCrawlerSemaphore
 * @Author: Fred Feng
 * @Date: 16/01/2025
 * @Version 1.0.0
 */
public final class WebCrawlerSemaphore {

    private final Semaphore semaphore = new Semaphore(1);

    private long catalogId;

    public long getCatalogId() {
        return catalogId;
    }

    public void setCatalogId(long catalogId) {
        this.catalogId = catalogId;
    }

    public boolean acquire() {
        try {
            return semaphore.tryAcquire(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    public void release() {
        semaphore.release();
    }

    public boolean isOccupied() {
        return semaphore.availablePermits() == 0;
    }

}
