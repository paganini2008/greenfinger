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

package com.github.greenfinger.shell.render;

import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import com.github.greenfinger.shell.ConsoleIO;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.state.Dashboard;
import com.github.greenfinger.core.engine.CrawlFrontier;

/**
 * Refreshes the dashboard in place while a crawl runs.
 *
 * <p>
 * On a terminal the block is redrawn where it stands, so the counters tick over without the screen
 * scrolling. When output is redirected there is no cursor to move, so the block is simply appended
 * at a slower cadence and the log stays readable.
 *
 * <p>
 * How many lines to move back over is counted from what was actually printed rather than worked
 * out in advance. The block changes height while it is up -- a node joins and the per-node table
 * grows a row -- and a redraw that moves back by yesterday's height leaves a trail of half-erased
 * tables behind it.
 * 
 * @Description: LiveDashboard
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class LiveDashboard implements AutoCloseable {

    private final CatalogDetails catalogDetails;
    private final Dashboard dashboard;
    private final CrawlFrontier frontier;
    private final PrintStream out;
    private final DashboardRenderer renderer = new DashboardRenderer();
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean drawn = new AtomicBoolean(false);
    private final AtomicInteger lastHeight = new AtomicInteger(0);

    /** What each node did, when the caller asked for that. Null for the totals alone. */
    private volatile Supplier<Map<String, Map<String, Long>>> perNode;

    public LiveDashboard(CatalogDetails catalogDetails, Dashboard dashboard, CrawlFrontier frontier,
            PrintStream out) {
        this.catalogDetails = catalogDetails;
        this.dashboard = dashboard;
        this.frontier = frontier;
        this.out = out;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "greenfinger-dashboard");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Adds the per-node table underneath, which is what {@code status --all} asks for.
     */
    public LiveDashboard perNode(Supplier<Map<String, Map<String, Long>>> perNode) {
        this.perNode = perNode;
        return this;
    }

    public void start() {
        long period = Ansi.enabled() ? 1L : 15L;
        scheduler.scheduleAtFixedRate(this::draw, 0L, period, TimeUnit.SECONDS);
    }

    /**
     * Watches until the crawl ends or the reader asks to stop.
     *
     * <p>
     * Stopping the watch is not stopping the crawl, and the difference is the point: a crawl runs
     * for hours and the prompt should not be gone for those hours. {@code status} brings the view
     * back, {@code pause} is what ends the crawl.
     *
     * @return true when the crawl finished, false when the reader typed one of the quit words.
     */
    public boolean await(BooleanSupplier finished, ConsoleIO io) {
        try {
            while (!finished.getAsBoolean()) {
                if (io != null && io.quitRequested()) {
                    return false;
                }
                Thread.sleep(200L);
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void draw() {
        try {
            String block = renderer.render(catalogDetails, dashboard, remaining(),
                    perNode != null ? perNode.get() : null);
            StringBuilder str = new StringBuilder();
            if (Ansi.enabled() && drawn.get()) {
                str.append(Ansi.redraw(lastHeight.get()));
            }
            str.append(block);
            out.print(str);
            out.flush();
            lastHeight.set(heightOf(block));
            drawn.set(true);
        } catch (Exception ignored) {
            // a dashboard frame is never worth interrupting a crawl for
        }
    }

    static int heightOf(String block) {
        int lines = 0;
        for (int i = 0; i < block.length(); i++) {
            if (block.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private long remaining() {
        try {
            return frontier.remaining();
        } catch (Exception e) {
            return -1L;
        }
    }

    /**
     * Stops refreshing and clears the live block, so the caller can print a final summary in its
     * place.
     */
    @Override
    public void close() {
        scheduler.shutdownNow();
        if (Ansi.enabled() && drawn.get()) {
            out.print(Ansi.redraw(lastHeight.get()));
            out.flush();
        }
    }

}
