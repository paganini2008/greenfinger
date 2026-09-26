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

package com.github.greenfinger.service.ops;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.DeleteLayer;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.service.DeleteReport;

/**
 * Everything a face can ask of a crawler. Two implementations the caller cannot tell apart:
 * {@link LocalOperations} does the work here, and the cluster's remote one asks the leader -- which
 * is what lets the terminal be a client with no engine and no database.
 *
 * <p>
 * Everything crossing it has to survive json, so the answers are flat records and the two
 * interfaces a crawl is described by travel as {@link CatalogSnapshot} and
 * {@link DashboardSnapshot}.
 *
 * @Description: GreenfingerOperations
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
public interface GreenfingerOperations {

    // ---- catalogs ---------------------------------------------------------------------------

    /** Every catalog, what is crawling, and each one's last run: two tables, one round trip. */
    Overview overview();

    /** One catalog, with its defaults applied; a blank id means whichever one is crawling. */
    CatalogSnapshot catalog(String idOrName);

    /** The categories actually in use. */
    List<String> categories();

    /**
     * The row behind a catalog, to be edited and handed back to {@link #saveCatalog}; a blank name
     * gives a new one carrying the defaults, which are the crawler's rather than the terminal's.
     */
    Catalog form(String idOrName);

    /** Creates or updates a catalog, and gives back what was stored. */
    CatalogSnapshot saveCatalog(Catalog form);

    /**
     * The catalog for this name or url, created with the defaults when there is not one yet. What
     * lets the one-line form take a url instead of an id: a script that has a url and wants pages
     * should not have to make two calls and parse an id out of the first. The prompt still defines
     * catalogs with {@code catalog-save}, where a typo is a question rather than a new catalog.
     *
     * @param name what to call it; the registrable domain when left out
     * @param url  where to crawl; required only when there is nothing by that name yet
     */
    CatalogSnapshot ensureCatalog(String name, String url);

    /** Removes a definition, stopping its crawl first. Answers with the name it had. */
    String deleteCatalog(String idOrName);

    /** Which versions a catalog has and what each holds. */
    Versions versions(String idOrName);

    /** The stored report of one version; the newest one when no version is named. */
    Map<String, Object> report(String idOrName, Integer version);

    // ---- crawling ---------------------------------------------------------------------------

    /** Begins a run and returns at once: the crawl belongs to the cluster, not to the caller. */
    String start(StartAsk ask);

    /** Winds a running crawl down. False when it was not running. */
    boolean interrupt(String idOrName);

    /** One frame of the live view, or null when that catalog is not crawling. */
    Live live(String catalogId, boolean perNode);

    /** Removes versions from any combination of the four stores. */
    List<DeleteReport.Line> delete(DeleteAsk ask);

    /** Writes a finished version into the index, the vectors or the files again. */
    ReplayAnswer replay(ReplayAsk ask);

    // ---- search -----------------------------------------------------------------------------

    /** Words, meaning or pictures -- and a blank query lists what was kept. */
    SearchAnswer search(SearchAsk ask);

    /** Where the full text index is, and how many documents each version put in it. */
    Info indexInfo();

    /** The same, for the vector store. */
    Info vectorInfo();

    // ---- what travels -----------------------------------------------------------------------

    /**
     * @param running  the catalog ids crawling anywhere in the cluster
     * @param lastRuns by catalog id, the counters of the last finished run
     */
    record Overview(List<CatalogSnapshot> catalogs, List<String> running,
            Map<String, Map<String, Object>> lastRuns) {
    }

    /** The rows {@code CrawlReportService} keeps per version, with the name to title them. */
    record Versions(String catalogId, String catalogName, List<Map<String, Object>> rows) {
    }

    /**
     * @param verb crawl, update, merge, rebuild or resume
     * @param from for an update, the url to carry on from
     */
    record StartAsk(String verb, String idOrName, String from, Integer threads) {
    }

    /**
     * A frame of the live view.
     *
     * @param finished true once the run has ended, which is what stops the view refreshing
     */
    record Live(DashboardSnapshot dashboard, long remaining,
            Map<String, Map<String, Long>> perNode, boolean finished) {
    }

    /**
     * Which versions go is worked out where the versions are, not by the face.
     *
     * @param version    one version to remove
     * @param keepLatest keep the newest n and remove the rest
     * @param all        every version; the catalog's index is emptied and left standing
     * @param purge      every version, and the index dropped as well
     */
    record DeleteAsk(String idOrName, Integer version, Integer keepLatest, boolean all,
            boolean purge, Set<DeleteLayer> layers, boolean dryRun, boolean force) {
    }

    record ReplayAsk(String idOrName, Integer version, Set<OutputType> layers) {
    }

    /**
     * @param files what the file layer could and could not put back, or null when it was not asked
     */
    record ReplayAnswer(long replayed, int version, FileLines files) {
    }

    record FileLines(long pages, long images, long intact, long unreachable, long changed) {
    }

    /**
     * @param mode words, meaning or pictures
     */
    record SearchAsk(String query, String idOrName, Integer size, String mode) {
    }

    /**
     * @param ranked false for a listing, where every score is zero and the column is dropped
     * @param images true when the rows are pictures rather than pages
     */
    record SearchAnswer(String title, boolean ranked, boolean images, long total, List<Hit> hits) {
    }

    /** One row of an answer: a score, and the two or three columns the mode puts on screen. */
    record Hit(double score, String first, String second) {
    }

    record InfoRow(String name, String value) {
    }

    /**
     * @param about  how the store is configured
     * @param counts one row per catalog version that put anything there
     * @param names  the indices or collections that exist, when the store can list them
     */
    record Info(List<InfoRow> about, List<CountRow> counts, List<String> names) {
    }

    record CountRow(String catalogId, String catalog, int version, String where, long count) {
    }

}
