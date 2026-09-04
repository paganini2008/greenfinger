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

package com.github.greenfinger.shell.command;

import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;
import com.github.greenfinger.shell.ConsoleIO;
import com.github.greenfinger.shell.Interview;
import com.github.greenfinger.shell.UsageException;
import com.github.greenfinger.shell.render.Ansi;
import com.github.greenfinger.shell.render.TextTable;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsService;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.ContentMode;
import com.github.greenfinger.core.model.ExtractorType;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.utils.UrlPathPatterns;
import com.github.greenfinger.core.utils.UrlUtils;
import com.github.greenfinger.service.CatalogAdminService;
import com.github.greenfinger.service.CrawlReportService;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Looking after the crawl definitions themselves.
 *
 * <p>
 * Every command here addresses a catalog by its id. The name is unique and would work, and that is
 * the trap: a name can be edited, so a script written against one is correct until the day somebody
 * renames a catalog. {@code catalog-list} is where the ids come from.
 * 
 * @Description: CatalogCommands
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Component
@RequiredArgsConstructor
public class CatalogCommands {

    private final CatalogAdminService catalogAdminService;
    private final CatalogDetailsService catalogDetailsService;
    private final CrawlReportService crawlReportService;
    private final WebCrawlerProperties webCrawlerProperties;
    private final ConsoleIO io;

    /**
     * Set by {@link CrawlCommands}, which offers to start a crawl the moment one is saved. A
     * setter rather than a constructor argument because the two classes would otherwise be a
     * cycle, and this is the direction that is genuinely optional.
     */
    @Setter
    private java.util.function.BiConsumer<String, String> starter;

    /** The same mapper shape the api uses, so one json works against both. */
    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .configure(com.fasterxml.jackson.databind.DeserializationFeature
                            .FAIL_ON_UNKNOWN_PROPERTIES, true);


    @Command(name = "catalog-list", group = "Catalog", description = "Every stored catalog")
    public void list() {
        List<Catalog> catalogs = catalogAdminService.findAll();
        if (catalogs.isEmpty()) {
            print(Ansi.dim("No catalogs yet. Create one with:  catalog-save"));
            return;
        }
        TextTable table = TextTable.of("Id", "Name", "Url", "Category", "Outputs", "Version",
                "Search").maxWidth(2, 40).title("Catalogs");
        for (Catalog catalog : catalogs) {
            CatalogDetails details = catalogDetailsService.loadCatalogDetails(catalog.getId());
            table.row(Ansi.cyan(catalog.getId()), catalog.getName(), catalog.getUrl(),
                    catalog.getCat(),
                    String.join("+",
                            details.getOutputTypes().stream().map(OutputType::getRepr).toList()),
                    "v" + details.getVersion(),
                    details.getSearchVersion() >= 0 ? "v" + details.getSearchVersion()
                            : Ansi.dim("-"));
        }
        print(table.render());
    }

    @Command(name = "catalog-show", group = "Catalog",
            description = "One catalog, in full. Without an id, whichever one is running")
    public void show(@Option(longName = "id",
            description = "The catalog id, from catalog-list. Omit to show the running crawl")
    String id) {
        // Everything printed comes from CatalogDetails rather than from the Catalog row: the row
        // is how the catalog is stored, details is what it means once the defaults are applied, and
        // showing the row would report a blank where a default is in force.
        CatalogDetails details = StringUtils.isNotBlank(id)
                ? catalogDetailsService.loadCatalogDetails(catalogAdminService.requireById(id)
                        .getId())
                : running();

        TextTable table = TextTable.of("Setting", "Value").maxWidth(1, 70)
                .title("Catalog " + details.getName());
        table.row("Id", details.getId());
        table.row("Name", details.getName());
        table.row("Url", details.getUrl());
        table.row("Start url", details.getStartUrl());
        table.row("Sitemap url", StringUtils.defaultIfBlank(details.getSitemapUrl(), "-"));
        table.row("Category", details.getCategory());
        table.row("Include", String.join(", ", details.getPathPatterns()));
        table.row("Exclude", String.join(", ", details.getExcludedPathPatterns()));
        table.row("Max pages", details.getMaxFetchSize());
        table.row("Max depth", details.getMaxFetchDepth());
        table.row("Interval", details.getFetchInterval() + " ms");
        table.row("Duration", details.getFetchDuration() + " min");
        table.row("Counted by", details.getCountingType().getRepr());
        table.row("Retry", details.getMaxRetryCount());
        table.row("Encoding", details.getPageEncoding());
        table.row("Extractor", details.getExtractor().getRepr());
        table.row("Url dedup", details.getUrlPathFilter());
        table.row("Outputs",
                String.join("+",
                        details.getOutputTypes().stream().map(OutputType::getRepr).toList()));
        table.row("Content", details.getContentMode().getRepr());
        table.row("Images", details.isImageEnabled());
        table.row("Version", "v" + details.getVersion());
        table.row("Search version",
                details.getSearchVersion() >= 0 ? "v" + details.getSearchVersion() : "-");
        table.row("Keep versions", details.getMaxVersions());
        table.row("State", details.getRunningState());
        print(table.render());

        // What the last run did is crawler-report's job, and it does it from the report rows
        // rather than from a settings file. Printing a second, shorter version of it here was two
        // answers to one question.
        print(Ansi.dim("Run 'crawler-report --id=" + details.getId()
                + "' for what its crawls did."));
    }

    /**
     * The crawl in progress, when the command was given no id.
     *
     * <p>
     * A prompt watching a crawl asks about that crawl, and having to paste its id to do so is a
     * step nobody wants. 1.x had no other way of asking -- its whole execution context was built
     * around "the catalog that is running" -- and the question is still worth having an answer to.
     */
    private CatalogDetails running() {
        CatalogDetails details = catalogDetailsService.loadRunningCatalogDetails();
        if (details == null) {
            throw new UsageException("Nothing is crawling. Name one: catalog-show --id=<id>");
        }
        return details;
    }

    /**
     * Create or update a catalog, one question at a time.
     *
     * <p>
     * The one command here that asks rather than reads. Seventeen settings do not fit on a line
     * anybody types twice, and as flags they came with the failure mode that gives command lines a
     * bad name: mistype one and the crawl starts anyway, with a default nobody chose. Asked in
     * order, each with what it accepts and what it currently says, the whole thing is return
     * pressed seventeen times and the two that matter typed in the middle.
     *
     * @param id an existing catalog to change. Without it, a new one.
     */
    @Command(name = "catalog-save", group = "Catalog",
            description = "Create or update a catalog: one question at a time, or --json")
    public void save(
            @Option(longName = "id",
                    description = "An existing catalog id to update; omit to create one") String id,
            @Option(longName = "json",
                    description = "The catalog as json, for a script. Omit to be asked instead")
            String json)
            throws Exception {
        if (StringUtils.isNotBlank(json)) {
            saveJson(id, json);
            return;
        }
        if (!io.isInteractive()) {
            throw new UsageException("catalog-save asks questions, so it needs a terminal.",
                    "Run it from the greenfinger prompt, or pass the whole catalog as json:",
                    "  catalog-save --json='{\"name\":\"books\",\"url\":\"https://example.com\"}'");
        }
        boolean updating = StringUtils.isNotBlank(id);
        Catalog catalog = updating ? catalogAdminService.requireById(id) : blank();

        print(Ansi.bold(updating ? "Updating '" + catalog.getName() + "'" : "New catalog"));
        print(Ansi.dim("Return keeps what is in the brackets. 'cancel' abandons the whole thing."));

        Interview interview = new Interview(io);
        Catalog saved;
        try {
            saved = catalogAdminService.save(interview(interview, catalog));
        } catch (Interview.Cancelled e) {
            print(Ansi.yellow("Cancelled. Nothing was saved."));
            return;
        }
        print(Ansi.green((updating ? "Updated '" : "Saved '") + saved.getName() + "'"));
        print("Id: " + Ansi.cyan(saved.getId()));

        offerToRun(interview, saved);
    }

    /**
     * Every question, in the order somebody setting up a crawl would think of them: what to fetch,
     * then how far to go, then where it goes afterwards.
     */
    /**
     * The whole catalog in one argument, for anything that is not a person at a keyboard.
     *
     * <p>
     * Twenty settings is a good interview and a terrible command line: as flags it is a line
     * nobody types twice and the failure mode is the one that gives command lines a bad name --
     * mistype one and the crawl starts anyway, with a default nobody chose. As json it is one
     * argument, it can be built by whatever is calling, and it is exactly the body
     * {@code POST /v2/catalog} takes, so a script that works against the server works here.
     *
     * <p>
     * Only the keys that are present are touched. With {@code --id} that makes this an edit of one
     * field rather than a re-statement of all twenty; without it, every key left out takes its
     * default, which is the same thing pressing return does in the interview.
     */
    private void saveJson(String id, String json) {
        Catalog catalog = StringUtils.isNotBlank(id) ? catalogAdminService.requireById(id)
                : blank();
        try {
            // readerForUpdating: the json is a patch onto what is there, not a replacement, so a
            // field the caller did not mention keeps the value it had
            catalog = OBJECT_MAPPER.readerForUpdating(catalog).readValue(json);
        } catch (Exception e) {
            throw new UsageException("That is not a catalog: " + e.getMessage(),
                    "It takes the same json as POST /v2/catalog, for example:",
                    "  catalog-save --json='{\"name\":\"books\",\"url\":\"https://books.toscrape.com\","
                            + "\"maxFetchSize\":500}'",
                    "Run 'options' for every field and what it accepts.");
        }
        Catalog saved = catalogAdminService.save(catalog);
        print(Ansi.green((StringUtils.isNotBlank(id) ? "Updated '" : "Saved '") + saved.getName()
                + "'"));
        print("Id: " + Ansi.cyan(saved.getId()));
        print(Ansi.dim("Crawl it with: catalog-crawl --id=" + saved.getId()));
    }

    private Catalog interview(Interview interview, Catalog catalog) {
        catalog.setUrl(interview.text("url", "http:// or https://", catalog.getUrl(), true));
        catalog.setName(interview.text("name", "unique; defaults to the domain",
                StringUtils.defaultIfBlank(catalog.getName(),
                        UrlUtils.getDomainName(catalog.getUrl())),
                true));
        catalog.setCat(interview.text("cat", "your own label", catalog.getCat(), false));
        catalog.setStartUrl(interview.text("start-url",
                "seed, and the prefix everything crawled stays under",
                StringUtils.defaultIfBlank(catalog.getStartUrl(), catalog.getUrl()), true));
        catalog.setSitemapUrl(interview.text("sitemap-url",
                "leave empty to discover it automatically", catalog.getSitemapUrl(), false));
        catalog.setPathPattern(interview.text("include", "ant path pattern, ',' for several",
                StringUtils.defaultIfBlank(catalog.getPathPattern(),
                        UrlPathPatterns.defaultPathPattern(catalog.getUrl())),
                true));
        catalog.setExcludedPathPattern(interview.text("exclude",
                "ant path pattern, ',' for several; empty for none",
                catalog.getExcludedPathPattern(), false));
        catalog.setPageEncoding(interview.text("encoding", "UTF-8 | GBK | ...; only a fallback",
                catalog.getPageEncoding(), true));
        catalog.setExtractorType(interview.extractor(catalog.getExtractorType()));

        catalog.setMaxFetchSize(
                interview.integer("max-size", "1 or more saved pages", catalog.getMaxFetchSize(),
                        1));
        catalog.setDepth(interview.integer("depth", "-1 for no limit, or 1 or more",
                catalog.getDepth(), -1));
        catalog.setDuration(
                interview.duration("duration", "minutes, 1 or more", catalog.getDuration(), 1L));
        catalog.setFetchInterval(interview.duration("interval",
                "milliseconds between fetches, 0 or more", catalog.getFetchInterval(), 0L));
        catalog.setMaxRetryCount(
                interview.integer("retry", "retries per url, 0 or more", catalog.getMaxRetryCount(),
                        0));

        catalog.setUrlPathFilter(interview.urlPathFilter(catalog.getUrlPathFilter()));
        catalog.setImageEnabled(interview.bool("images", catalog.getImageEnabled()));
        catalog.setOutputTypes(interview.outputs(catalog.getOutputTypes()));
        catalog.setContentMode(interview.content(catalog.getContentMode()));
        catalog.setMaxVersions(interview.integer("max-versions", "versions to keep, 1 or more",
                catalog.getMaxVersions(), 1));
        return catalog;
    }

    /**
     * A catalog is saved in order to be crawled, so the offer belongs here rather than in a second
     * command somebody has to know the name of.
     */
    private void offerToRun(Interview interview, Catalog saved) {
        if (starter == null) {
            return;
        }
        String answer;
        try {
            answer = interview.choose("start now", List.of("no", "crawl", "update", "rebuild"));
        } catch (Interview.Cancelled e) {
            answer = "no";
        }
        if ("no".equals(answer)) {
            print(Ansi.dim("Start it later with:  catalog-crawl --id=" + saved.getId()));
            return;
        }
        starter.accept(answer, saved.getId());
    }

    /**
     * A new catalog carrying the configured defaults, so the first question already has an answer
     * in its brackets rather than an empty pair.
     */
    private Catalog blank() {
        Catalog catalog = new Catalog();
        catalog.setCat("default");
        catalog.setPageEncoding(webCrawlerProperties.getDefaultPageEncoding());
        catalog.setExtractorType(ExtractorType.of(webCrawlerProperties.getDefaultExtractor()));
        catalog.setMaxFetchSize(webCrawlerProperties.getDefaultMaxFetchSize());
        catalog.setDepth(webCrawlerProperties.getDefaultMaxFetchDepth());
        catalog.setDuration(webCrawlerProperties.getDefaultFetchDuration());
        catalog.setFetchInterval(webCrawlerProperties.getDefaultFetchInterval());
        catalog.setMaxRetryCount(webCrawlerProperties.getDefaultMaxRetryCount());
        catalog.setImageEnabled(webCrawlerProperties.getImage().isEnabled());
        catalog.setMaxVersions(webCrawlerProperties.getDefaultMaxVersions());
        catalog.setContentMode(ContentMode.TEXT_IMAGE);
        return catalog;
    }

    @Command(name = "catalog-delete", group = "Catalog",
            description = "Remove the definition; use 'delete' for the data it produced")
    public void deleteCatalog(@Option(longName = "id",
            description = "The catalog id, from catalog-list") String id) {
        Catalog catalog = catalogAdminService.requireById(id);
        boolean removed = catalogAdminService.delete(catalog.getId());
        if (!removed) {
            throw new WebCrawlerException("Could not delete '" + catalog.getName() + "'");
        }
        print(Ansi.green("Deleted '" + catalog.getName() + "'"));
        print(Ansi.dim("Its crawled data is untouched. Remove that with:  delete --id="
                + catalog.getId() + " --all"));
    }

    @Command(name = "catalog-cats", group = "Catalog", description = "Every category in use")
    public void categories() {
        List<String> categories = catalogAdminService.findAllCategories();
        if (categories.isEmpty()) {
            print(Ansi.dim("No categories yet."));
            return;
        }
        TextTable table = TextTable.of("Category").title("Categories");
        categories.forEach(table::row);
        print(table.render());
    }

    /**
     * Which versions a catalog has, what is in each, and which one search is serving.
     */
    @Command(name = "versions", group = "Catalog",
            description = "Every version of one catalog, newest first")
    public void versions(@Option(longName = "id",
            description = "The catalog id, from catalog-list") String id) {
        Catalog catalog = catalogAdminService.requireById(id);
        List<Map<String, Object>> versions = crawlReportService.versions(catalog.getId());
        TextTable table = TextTable.of("Version", "Pages", "Images", "State", "First built",
                "Last run").rightAlign(1).rightAlign(2).title("Versions of " + catalog.getName());
        for (Map<String, Object> version : versions) {
            table.row("v" + version.get("version"), version.get("pages"), version.get("images"),
                    state(version), value(version.get("createdAt")),
                    value(version.get("updatedAt")));
        }
        print(table.render());
        print(Ansi.dim("A report per version:  crawler-report --id=" + catalog.getId()
                + " --version=<n>"));
    }

    private String state(Map<String, Object> version) {
        StringBuilder state = new StringBuilder();
        if (Boolean.TRUE.equals(version.get("current"))) {
            state.append("current");
        }
        if (Boolean.TRUE.equals(version.get("searchable"))) {
            state.append(state.length() > 0 ? ", " : "").append(Ansi.green("searchable"));
        }
        return state.length() > 0 ? state.toString() : Ansi.dim("-");
    }

    private String value(Object value) {
        return value != null ? String.valueOf(value) : Ansi.dim("-");
    }

    /**
     * The stored dashboard of one version: what it produced, what each node did, the cluster it
     * ran on, and the settings it ran under.
     */
    @Command(name = "crawler-report", group = "Catalog",
            description = "The stored report of one version")
    public void report(
            @Option(longName = "id",
                    description = "The catalog id, from catalog-list") String id,
            @Option(longName = "version",
                    description = "Which version; omit for the newest one with a report")
                    Integer version) {
        Catalog catalog = catalogAdminService.requireById(id);
        Map<String, Object> report = crawlReportService.stored(catalog.getId(), version)
                .orElseThrow(() -> new WebCrawlerException(version != null
                        ? "No report for v" + version + " of '" + catalog.getName() + "'."
                                + " Run 'versions --id=" + catalog.getId() + "' to see which"
                                + " versions there are."
                        : "'" + catalog.getName() + "' has no report yet. One is written when a"
                                + " crawl finishes."));
        render(report, "Report");
    }

    /**
     * A report is nested maps of numbers, and printing it as json puts the reader in front of two
     * hundred lines of punctuation. One table per section, flattened one level, is the same
     * content readable.
     */
    private void render(Map<String, Object> report, String title) {
        TextTable summary = TextTable.of("Field", "Value").maxWidth(1, 70).title(title);
        report.forEach((key, value) -> {
            if (!(value instanceof Map) && !(value instanceof List)) {
                summary.row(key, String.valueOf(value));
            }
        });
        print(summary.render());
        report.forEach((key, value) -> {
            if (value instanceof Map<?, ?> section && !section.isEmpty()) {
                TextTable table = TextTable.of("Field", "Value").maxWidth(1, 70).title(key);
                section.forEach((field, entry) -> table.row(String.valueOf(field), flatten(entry)));
                print(table.render());
            } else if (value instanceof List<?> rows && !rows.isEmpty()) {
                TextTable table = TextTable.of(key).maxWidth(0, 100).title(key);
                rows.forEach(row -> table.row(flatten(row)));
                print(table.render());
            }
        });
    }

    private String flatten(Object value) {
        if (value instanceof Map<?, ?> nested) {
            return nested.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .reduce((a, b) -> a + ", " + b).orElse("");
        }
        if (value instanceof List<?> items) {
            return items.stream().map(this::flatten).reduce((a, b) -> a + ", " + b).orElse("");
        }
        return String.valueOf(value);
    }

    private void print(String text) {
        System.out.println(text);
    }

}
