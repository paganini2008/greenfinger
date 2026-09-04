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

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.time.DurationFormatUtils;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.state.CountingType;
import com.github.greenfinger.core.component.state.Dashboard;

/**
 * Draws the crawl dashboard: a headline, a progress bar and a table of counters.
 * 
 * @Description: DashboardRenderer
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class DashboardRenderer {

    private static final int BAR_WIDTH = 32;

    public String render(CatalogDetails catalogDetails, Dashboard dashboard, long remaining) {
        return render(catalogDetails, dashboard, remaining, null);
    }

    /**
     * @param perNode what each node did, by node then by counter, or null for the totals alone.
     *        Rendered underneath rather than instead: the totals are the answer to "how is it
     *        going", and this is the answer to "is one of them doing nothing".
     */
    public String render(CatalogDetails catalogDetails, Dashboard dashboard, long remaining,
            Map<String, Map<String, Long>> perNode) {
        StringBuilder str = new StringBuilder();
        str.append(Ansi.bold("Crawling ")).append(Ansi.cyan(catalogDetails.getName())).append("  ")
                .append(Ansi.dim(catalogDetails.getUrl())).append(System.lineSeparator());
        str.append(sizeBar(catalogDetails, dashboard)).append(System.lineSeparator());
        str.append(durationBar(catalogDetails, dashboard)).append(System.lineSeparator());
        str.append(table(dashboard, remaining).render());
        if (perNode != null) {
            str.append(nodeTable(perNode).render());
        }
        return str.toString();
    }

    /**
     * One row per node. Empty of nodes is still a table, saying this is a cluster of one rather
     * than that nobody looked.
     */
    private TextTable nodeTable(Map<String, Map<String, Long>> perNode) {
        TextTable table = TextTable.of("Node", "Saved", "Handled", "Dispatched", "Failed")
                .rightAlign(1).rightAlign(2).rightAlign(3).rightAlign(4).title("By node");
        if (perNode.isEmpty()) {
            table.row(Ansi.dim("this node only"), "-", "-", "-", "-");
            return table;
        }
        new TreeMap<>(perNode).forEach((node, counters) -> table.row(Ansi.cyan(node),
                counters.getOrDefault(CountingType.SAVED_RESOURCE_COUNT.getRepr(), 0L),
                counters.getOrDefault(CountingType.HANDLED_URL_COUNT.getRepr(), 0L),
                counters.getOrDefault(CountingType.URL_TOTAL_COUNT.getRepr(), 0L),
                counters.getOrDefault(CountingType.INVALID_URL_COUNT.getRepr(), 0L)));
        return table;
    }

    /**
     * How many lines {@link #render} produces, so a caller redrawing in place knows how far to move
     * the cursor back.
     *
     * <p>
     * Derived from the table rather than written down: a hardcoded count silently corrupts the
     * display the first time a counter is added.
     */
    public int lineCount() {
        // the headline and the two progress bars, then the counter table itself
        return 3 + table(null, 0L).lineCount();
    }

    /**
     * Two bars, not one.
     *
     * <p>
     * A crawl ends on whichever of the two limits arrives first, and one bar showing the nearer of
     * them cannot say which that will be -- it moves for one reason, then for the other, and a
     * crawl at 60% has no way to tell you whether it is 60% of the pages or 60% of the time. 1.x
     * drew both for that reason and so does this.
     */
    private String sizeBar(CatalogDetails catalogDetails, Dashboard dashboard) {
        long counted = catalogDetails.getCountingType().getValue(dashboard);
        Integer maxFetchSize = catalogDetails.getMaxFetchSize();
        double ratio = maxFetchSize != null && maxFetchSize > 0
                ? (double) counted / maxFetchSize
                : 0d;
        String label = maxFetchSize != null && maxFetchSize > 0
                ? String.format("%s %d / %d%s", catalogDetails.getCountingType().getRepr(),
                        counted, maxFetchSize, eta(catalogDetails, dashboard, counted))
                : String.format("%s %d / no limit", catalogDetails.getCountingType().getRepr(),
                        counted);
        return bar("size", ratio, label);
    }

    private String durationBar(CatalogDetails catalogDetails, Dashboard dashboard) {
        long elapsed = dashboard.getElapsedTime();
        Long fetchDuration = catalogDetails.getFetchDuration();
        long duration = fetchDuration != null ? TimeUnit.MINUTES.toMillis(fetchDuration) : 0L;
        double ratio = duration > 0 ? (double) elapsed / duration : 0d;
        String label = duration > 0
                ? String.format("%s / %s elapsed", clock(elapsed), clock(duration))
                : String.format("%s elapsed, no limit", clock(elapsed));
        return bar("time", ratio, label);
    }

    /**
     * How long the remaining pages would take at the rate this crawl has managed so far.
     *
     * <p>
     * From the average time a counted page took rather than from the elapsed time divided by the
     * count, because the two differ whenever the crawl was waiting on something -- and it usually
     * was.
     */
    private String eta(CatalogDetails catalogDetails, Dashboard dashboard, long counted) {
        double average = dashboard.getAverageExecutionTime();
        long remaining = catalogDetails.getMaxFetchSize() - counted;
        if (average <= 0d || remaining <= 0L || counted <= 0L) {
            return "";
        }
        return String.format(", about %s left", clock((long) (remaining * average)));
    }

    private String bar(String name, double ratio, String label) {
        double progress = Math.min(1d, Math.max(0d, ratio));
        int filled = (int) Math.round(progress * BAR_WIDTH);
        StringBuilder bar = new StringBuilder();
        bar.append("\u2588".repeat(filled));
        bar.append(Ansi.dim("\u2591".repeat(BAR_WIDTH - filled)));
        String colour = progress >= 0.9d ? Ansi.YELLOW : Ansi.GREEN;
        return String.format("%-5s %s %s  %s", Ansi.dim(name),
                Ansi.paint(bar.toString(), colour),
                Ansi.bold(String.format("%3d%%", Math.round(progress * 100))), Ansi.dim(label));
    }

    private TextTable table(Dashboard dashboard, long remaining) {
        TextTable table = TextTable.of("Metric", "Count").rightAlign(1);
        table.row("Pages saved", dashboard != null ? dashboard.getSavedResourceCount() : 0);
        table.row("Images saved", dashboard != null ? dashboard.getSavedImageCount() : 0);
        table.row("Urls seen", dashboard != null ? dashboard.getTotalUrlCount() : 0);
        table.row("Already visited", dashboard != null ? dashboard.getExistingUrlCount() : 0);
        table.row("Filtered out", dashboard != null ? dashboard.getFilteredUrlCount() : 0);
        table.row("Duplicate content",
                dashboard != null ? dashboard.getDuplicatedContentCount() : 0);
        table.row("Failed", dashboard != null ? dashboard.getInvalidUrlCount() : 0);
        table.row("Queued", remaining >= 0 ? remaining : "-");
        table.row("Elapsed", clock(dashboard != null ? dashboard.getElapsedTime() : 0L));
        return table;
    }

    /**
     * The table printed once a crawl has finished, including why it stopped.
     */
    public String renderSummary(CatalogDetails catalogDetails, Dashboard dashboard, String reason,
            long remaining, String outputPath) {
        TextTable table = TextTable.of("Result", "Value").rightAlign(1).maxWidth(1, 70)
                .title("Crawl finished");
        table.row("Catalog", catalogDetails.getName());
        table.row("Site", catalogDetails.getUrl());
        table.row("Pages saved", dashboard.getSavedResourceCount());
        table.row("Images saved", dashboard.getSavedImageCount());
        table.row("Urls seen", dashboard.getTotalUrlCount());
        table.row("Already visited", dashboard.getExistingUrlCount());
        table.row("Filtered out", dashboard.getFilteredUrlCount());
        table.row("Duplicate content", dashboard.getDuplicatedContentCount());
        table.row("Failed", dashboard.getInvalidUrlCount());
        table.row("Elapsed", clock(dashboard.getElapsedTime()));
        table.row("Stopped because", reason);
        table.row("Still queued", remaining);
        table.row("Output", outputPath);

        StringBuilder str = new StringBuilder(table.render());
        if (remaining > 0) {
            str.append(Ansi.yellow("Run 'resume' to continue where this left off."))
                    .append(System.lineSeparator());
        }
        return str.toString();
    }

    private String clock(long millis) {
        return DurationFormatUtils.formatDuration(Math.max(0L, millis), "HH:mm:ss");
    }

}
