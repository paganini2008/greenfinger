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

package com.github.greenfinger.shell;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import com.github.greenfinger.core.engine.CrawlerEngine;

/**
 * The crawl this shell started, running behind the prompt.
 *
 * <p>
 * A crawl used to run on the thread that typed the command, which made the prompt unavailable for
 * however long the crawl took -- and the only way out was Ctrl+C, which stops the crawl as well as
 * the watching. Started here instead, the two are separable: {@code status} attaches to the live
 * counters, {@code q} detaches from them, and {@code pause} is what stops the crawl.
 *
 * <p>
 * One at a time, because {@code WebCrawlerSemaphore} allows one at a time -- a second would be
 * refused by the engine anyway, and refusing it here says so before anything is started.
 * 
 * @Description: RunningCrawls
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
@Component
public class RunningCrawls implements DisposableBean {

    private final Map<String, Future<CrawlerEngine.Result>> running = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "greenfinger-crawl");
        // not a daemon: a one-shot invocation must not have its crawl killed when the command
        // thread returns
        thread.setDaemon(false);
        return thread;
    });

    public Future<CrawlerEngine.Result> start(String catalogId,
            Callable<CrawlerEngine.Result> crawl) {
        Future<CrawlerEngine.Result> future = executor.submit(crawl);
        running.put(catalogId, future);
        return future;
    }

    /**
     * The crawl started here for this catalog, or null. A finished one is kept until another
     * starts, so the summary is still available afterwards.
     */
    public Future<CrawlerEngine.Result> get(String catalogId) {
        return running.get(catalogId);
    }

    /**
     * Whichever crawl this shell has going, or null when it has none.
     */
    public Map.Entry<String, Future<CrawlerEngine.Result>> current() {
        return running.entrySet().stream().filter(entry -> !entry.getValue().isDone()).findFirst()
                .orElse(null);
    }

    public void forget(String catalogId) {
        running.remove(catalogId);
    }

    @Override
    public void destroy() {
        executor.shutdown();
        try {
            // a crawl asked to stop winds down at its next check; this is how long that is given
            if (!executor.awaitTermination(30L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

}
