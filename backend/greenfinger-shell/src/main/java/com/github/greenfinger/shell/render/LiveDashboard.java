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
import com.github.greenfinger.service.ops.DashboardSnapshot;
import com.github.greenfinger.service.ops.GreenfingerOperations.Live;

/**
 * Refreshes the dashboard in place while a crawl runs.
 *
 * <p>
 * On a terminal the block is redrawn where it stands, so the counters tick over without the screen
 * scrolling. When output is redirected there is no cursor to move, so the block is simply appended
 * at a slower cadence and the log stays readable.
 *
 * <p>
 * How far to move back is counted from what was actually printed, not worked out in advance: the
 * block changes height while it is up -- a node joins and the table grows a row -- and moving back
 * by the old height leaves a trail of half-erased tables.
 * 
 * @Description: LiveDashboard
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class LiveDashboard implements AutoCloseable {

    private final Supplier<Live> frames;
    private final PrintStream out;
    private final DashboardRenderer renderer = new DashboardRenderer();
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean drawn = new AtomicBoolean(false);
    private final AtomicInteger lastHeight = new AtomicInteger(0);

    /** What each node did, when the caller asked for that. Null for the totals alone. */
    private volatile Supplier<Map<String, Map<String, Long>>> perNode;

    /**
     * The last frame drawn. A caller watching a crawl on another node asks this rather than the
     * node: the view already fetches a frame a second, and polling for "is it over" five times a
     * second would be five requests a second to the leader to ask what it has just been told.
     */
    private volatile Live last;

    /**
     * The crawl running in this process: the counters and the frontier are read afresh on every
     * frame, because they are the live objects.
     */
    public LiveDashboard(CatalogDetails catalogDetails, Dashboard dashboard, CrawlFrontier frontier,
            PrintStream out) {
        this(() -> new Live(DashboardSnapshot.of(dashboard), remaining(frontier), null, false),
                out);
    }

    /**
     * A crawl running somewhere else: every frame is one answer from the node that has it, which
     * is how the terminal watches a crawl it is not running.
     */
    public LiveDashboard(Supplier<Live> frames, PrintStream out) {
        this.frames = frames;
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

    /** Whether the last frame said the run had ended. */
    public boolean finished() {
        Live frame = last;
        return frame != null && frame.finished();
    }

    private void draw() {
        try {
            Live frame = frames.get();
            if (frame == null || frame.dashboard() == null) {
                return;
            }
            last = frame;
            String block = renderer.render(frame.dashboard().getCatalogDetails(),
                    frame.dashboard(), frame.remaining(),
                    perNode != null ? perNode.get() : frame.perNode());
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

    /** A queue length is worth a dash: the store can be closed under a crawl that just ended. */
    private static long remaining(CrawlFrontier frontier) {
        try {
            return frontier != null ? frontier.remaining() : -1L;
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
