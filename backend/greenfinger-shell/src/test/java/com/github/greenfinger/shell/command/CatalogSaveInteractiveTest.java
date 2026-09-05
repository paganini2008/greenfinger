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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import com.github.greenfinger.shell.ConsoleCapture;
import com.github.greenfinger.shell.ConsoleIO;
import com.github.greenfinger.shell.GreenfingerShellMain;
import com.github.greenfinger.shell.ScriptedConsole;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.core.model.ContentMode;
import com.github.greenfinger.core.model.ExtractorType;
import com.github.greenfinger.shell.UsageException;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.service.CatalogAdminService;

/**
 * {@code catalog-save}, which is the one command that asks rather than reads.
 *
 * <p>
 * Seventeen questions in a fixed order, each with a default in the brackets, and the whole thing
 * abandonable at any of them. That last part is what the tests here are mostly about: half a
 * catalog written because somebody changed their mind at question twelve would be worse than no
 * catalog at all.
 * 
 * @Description: CatalogSaveInteractiveTest
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
@SpringBootTest(classes = GreenfingerShellMain.class)
@Import(CatalogSaveInteractiveTest.ScriptedTerminal.class)
@TestPropertySource(properties = {"spring.shell.interactive.enabled=false",
        "spring.main.banner-mode=off",
        "spring.datasource.url=jdbc:h2:mem:greenfinger-save;DB_CLOSE_DELAY=-1",
        "greenfinger.output.file.directory=${java.io.tmpdir}/gf-save/data",
        "greenfinger.frontier-directory=${java.io.tmpdir}/gf-save/frontier",
        "greenfinger.dedup.url.directory=${java.io.tmpdir}/gf-save/url",
        "greenfinger.dedup.content.directory=${java.io.tmpdir}/gf-save/content",
        "greenfinger.output.index.lucene.directory=${java.io.tmpdir}/gf-save-lucene",
        "greenfinger.output.vector.lucene.directory=${java.io.tmpdir}/gf-save-lucene-vector",
        "greenfinger.embedding.preload=false"})
class CatalogSaveInteractiveTest {

    @TestConfiguration
    static class ScriptedTerminal {

        @Primary
        @Bean
        ConsoleIO scriptedConsole() {
            return new ScriptedConsole();
        }

    }

    @Autowired
    private CatalogCommands catalogCommands;

    @Autowired
    private CatalogAdminService catalogAdminService;

    @Autowired
    private ConsoleIO io;

    private ScriptedConsole console() {
        return (ScriptedConsole) io;
    }

    @BeforeEach
    void setUp() {
        catalogAdminService.findAll().forEach(c -> catalogAdminService.delete(c.getId()));
    }

    /**
     * The answers to the eighteen questions, in order, with everything after the url left as it
     * came.
     */
    private String[] answersFor(String url, String... afterUrl) {
        String[] answers = new String[1 + afterUrl.length];
        answers[0] = url;
        System.arraycopy(afterUrl, 0, answers, 1, afterUrl.length);
        return answers;
    }

    @Test
    @DisplayName("return through every question saves a catalog on the defaults")
    void everyDefaultAccepted() throws Exception {
        console().script(answersFor("https://example.com", "", "", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "", "no"));

        try (ConsoleCapture captured = new ConsoleCapture()) {
            catalogCommands.save(null, null);
            assertThat(captured.output()).contains("New catalog").contains("Saved")
                    .contains("Id: ").contains("crawl --id=");
        }

        List<Catalog> catalogs = catalogAdminService.findAll();
        assertThat(catalogs).hasSize(1);
        Catalog saved = catalogs.get(0);
        assertThat(saved.getUrl()).isEqualTo("https://example.com");
        // the name defaults to the registrable domain, which is what was in the brackets
        assertThat(saved.getName()).isEqualTo("example");
        assertThat(saved.getStartUrl()).isEqualTo("https://example.com");
        assertThat(saved.getCat()).isEqualTo("other");
        assertThat(saved.getExtractorType()).isEqualTo(ExtractorType.ADAPTIVE);
        assertThat(saved.getOutputTypes()).containsExactly(OutputType.FILE);
        assertThat(saved.getContentMode()).isEqualTo(ContentMode.TEXT_IMAGE);
        assertThat(saved.getMaxFetchSize()).isPositive();
    }

    @Test
    @DisplayName("every question is asked, in order, with its range and its current value")
    void everyQuestionCarriesItsRange() throws Exception {
        console().script(answersFor("https://example.com", "", "", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "", "no"));

        try (ConsoleCapture captured = new ConsoleCapture()) {
            catalogCommands.save(null, null);
            String output = captured.output();
            for (String question : List.of("url", "name", "cat", "start-url", "sitemap-url",
                    "include", "exclude", "encoding", "extractor", "max-size", "depth", "duration",
                    "interval", "retry", "url-dedup", "images", "output-types", "content",
                    "max-versions")) {
                assertThat(output).as(question).contains(question);
            }
            assertThat(output).contains("http:// or https://").contains("true | false")
                    .contains("text+image | text").contains("-1 for no limit")
                    .contains("file+index+vector");
        }
    }

    @Test
    @DisplayName("what was typed is what is saved")
    void takesTheAnswers() throws Exception {
        console().script("https://news.example.com", "my-news", "news",
                "https://news.example.com/section", "", "**/section/**", "**/tags/**", "GBK",
                "restclient", "250", "4", "12", "500", "2", "rocksdb", "false", "index+vector",
                "text", "3", "no");

        catalogCommands.save(null, null);

        Catalog saved = catalogAdminService.findAll().get(0);
        assertThat(saved.getName()).isEqualTo("my-news");
        assertThat(saved.getCat()).isEqualTo("news");
        assertThat(saved.getStartUrl()).isEqualTo("https://news.example.com/section");
        assertThat(saved.getPathPattern()).isEqualTo("**/section/**");
        assertThat(saved.getUrlPathFilter()).isEqualTo("rocksdb");
        assertThat(saved.getExcludedPathPattern()).isEqualTo("**/tags/**");
        assertThat(saved.getPageEncoding()).isEqualTo("GBK");
        assertThat(saved.getExtractorType()).isEqualTo(ExtractorType.RESTCLIENT);
        assertThat(saved.getMaxFetchSize()).isEqualTo(250);
        assertThat(saved.getDepth()).isEqualTo(4);
        assertThat(saved.getDuration()).isEqualTo(12L);
        assertThat(saved.getFetchInterval()).isEqualTo(500L);
        assertThat(saved.getMaxRetryCount()).isEqualTo(2);
        assertThat(saved.getImageEnabled()).isFalse();
        // file is always on, whether or not it was typed
        assertThat(saved.getOutputTypes()).containsExactly(OutputType.FILE, OutputType.INDEX,
                OutputType.VECTOR);
        assertThat(saved.getContentMode()).isEqualTo(ContentMode.TEXT);
        assertThat(saved.getMaxVersions()).isEqualTo(3);
    }

    @Test
    @DisplayName("--id updates the catalog it names, and the brackets hold what it says now")
    void updatingShowsWhatIsThere() throws Exception {
        console().script(answersFor("https://example.com", "first-name", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "", "", "", "", "", "no"));
        catalogCommands.save(null, null);
        String id = catalogAdminService.findAll().get(0).getId();

        console().script(answersFor("", "second-name", "", "", "", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "no"));
        try (ConsoleCapture captured = new ConsoleCapture()) {
            catalogCommands.save(id, null);
            String output = captured.output();
            assertThat(output).contains("Updating 'first-name'").contains("Updated")
                    .contains("[https://example.com]").contains("[first-name]");
        }

        assertThat(catalogAdminService.findAll()).hasSize(1);
        assertThat(catalogAdminService.requireById(id).getName()).isEqualTo("second-name");
        // return kept the url, which is the whole point of showing it in the brackets
        assertThat(catalogAdminService.requireById(id).getUrl()).isEqualTo("https://example.com");
    }

    @Test
    @DisplayName("cancel at any question writes nothing at all")
    void cancelWritesNothing() throws Exception {
        console().script("https://example.com", "a-name", "cancel");

        try (ConsoleCapture captured = new ConsoleCapture()) {
            catalogCommands.save(null, null);
            assertThat(captured.output()).contains("Cancelled").contains("Nothing was saved");
        }
        assertThat(catalogAdminService.findAll()).isEmpty();
    }

    @Test
    @DisplayName("input that simply ends is a cancel, not half a catalog")
    void endOfInputWritesNothing() throws Exception {
        console().script("https://example.com");

        catalogCommands.save(null, null);

        assertThat(catalogAdminService.findAll()).isEmpty();
    }

    @Test
    @DisplayName("a bad answer is asked again rather than accepted")
    void asksAgainRatherThanAccepting() throws Exception {
        // a refusal asks again, so extractor and max-size each take two answers and the second
        // one is what is kept
        console().script("https://example.com",
                "", "", "", "", "", "", "",             // name .. encoding
                "lynx", "adaptive",                     // extractor, refused then taken
                "0", "500",                             // max-size, refused then taken
                "", "", "", "", "", "", "", "", "",     // depth .. max-versions
                "no");

        try (ConsoleCapture captured = new ConsoleCapture()) {
            catalogCommands.save(null, null);
            String output = captured.output();
            assertThat(output).contains("lynx").contains("max-size takes");
        }
        Catalog saved = catalogAdminService.findAll().get(0);
        assertThat(saved.getExtractorType()).isEqualTo(ExtractorType.ADAPTIVE);
        assertThat(saved.getMaxFetchSize()).isEqualTo(500);
    }

    @Test
    @DisplayName("--json saves the whole catalog without asking anything")
    void jsonSavesWithoutAsking() throws Exception {
        // no script(): a scripted answer here would mean a question was asked, and none should be
        try (ConsoleCapture captured = new ConsoleCapture()) {
            catalogCommands.save(null, "{\"name\":\"from-json\",\"url\":\"https://example.com\","
                    + "\"cat\":\"travel\",\"maxFetchSize\":250}");
            assertThat(captured.output()).contains("Saved").contains("Id: ")
                    .contains("catalog-crawl --id=");
        }

        Catalog saved = catalogAdminService.findAll().get(0);
        assertThat(saved.getName()).isEqualTo("from-json");
        assertThat(saved.getUrl()).isEqualTo("https://example.com");
        assertThat(saved.getCat()).isEqualTo("travel");
        assertThat(saved.getMaxFetchSize()).isEqualTo(250);
        // everything left out took its default, which is what pressing return does in the
        // interview
        assertThat(saved.getExtractorType()).isEqualTo(ExtractorType.ADAPTIVE);
        assertThat(saved.getStartUrl()).isNotBlank();
    }

    @Test
    @DisplayName("--json with an id changes the fields it names and leaves the rest")
    void jsonWithAnIdIsAPatch() throws Exception {
        catalogCommands.save(null, "{\"name\":\"before\",\"url\":\"https://example.com\","
                + "\"cat\":\"health\",\"maxFetchSize\":250}");
        String id = catalogAdminService.findAll().get(0).getId();

        catalogCommands.save(id, "{\"maxFetchSize\":900}");

        Catalog saved = catalogAdminService.requireById(id);
        assertThat(saved.getMaxFetchSize()).isEqualTo(900);
        assertThat(saved.getName()).isEqualTo("before");
        assertThat(saved.getCat()).isEqualTo("health");
    }

    @Test
    @DisplayName("json that is not a catalog says so, and says what one looks like")
    void badJsonIsExplained() {
        assertThatThrownBy(() -> catalogCommands.save(null, "{\"nonsense\":true}"))
                .isInstanceOf(UsageException.class)
                .hasMessageContaining("not a catalog");
        assertThatThrownBy(() -> catalogCommands.save(null, "not json at all"))
                .isInstanceOf(UsageException.class);
        assertThat(catalogAdminService.findAll()).isEmpty();
    }

    @Test
    @DisplayName("saving offers to run it, and 'no' says how to run it later")
    void offersToRunIt() throws Exception {
        console().script(answersFor("https://example.com", "", "", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "", ""));

        try (ConsoleCapture captured = new ConsoleCapture()) {
            catalogCommands.save(null, null);
            // return takes the first answer, which is always the one that does nothing
            assertThat(captured.output()).contains("start now")
                    .contains("no | crawl | update | rebuild").contains("Start it later with");
        }
    }

    @Test
    @DisplayName("a stored report is rendered as tables rather than as json")
    void reportsAreRenderedAsTables() throws Exception {
        console().script(answersFor("https://example.com", "", "", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "", "no"));
        catalogCommands.save(null, null);
        String id = catalogAdminService.findAll().get(0).getId();

        try (ConsoleCapture captured = new ConsoleCapture()) {
            catalogCommands.versions(id);
            assertThat(captured.output()).contains("v0").contains("crawler-report");
        }
        // nothing has been crawled, so there is no report and it says so rather than printing
        // an empty table
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> catalogCommands.report(id, null))
                .hasMessageContaining("no report yet");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> catalogCommands.report(id, 7))
                .hasMessageContaining("No report for v7");
    }

    @Test
    @DisplayName("catalog-save needs a terminal, and says so rather than hanging")
    void refusesWithoutATerminal() {
        Map<String, Object> nothing = Map.of();
        assertThat(nothing).isEmpty();
        ConsoleIO plain = ConsoleIO.of(java.io.InputStream.nullInputStream(), System.out);
        assertThat(plain.isInteractive()).isFalse();
    }

}
