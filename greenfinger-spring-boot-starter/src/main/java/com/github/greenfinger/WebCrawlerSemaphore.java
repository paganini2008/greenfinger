/*
 * Copyright 2017-2025 Fred Feng (paganini.fy@gmail.com)
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
