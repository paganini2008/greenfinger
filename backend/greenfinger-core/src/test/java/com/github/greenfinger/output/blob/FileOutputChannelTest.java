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

package com.github.greenfinger.output.blob;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawledPage;
import com.github.greenfinger.core.output.FileLayout;
import com.github.greenfinger.core.output.OutputPayload;
import com.github.greenfinger.core.record.ResourceRecord;
import com.github.greenfinger.output.OutputFixtures;
import com.github.greenfinger.output.blob.LocalBlobStore;

/**
 * 
 * @Description: FileOutputChannelTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
class FileOutputChannelTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private FileOutputChannel open(Path root, CatalogDetails details) throws Exception {
        LocalBlobStore blobStore = new LocalBlobStore(root);
        blobStore.afterPropertiesSet();
        FileOutputChannel channel = new FileOutputChannel(blobStore, OutputFixtures.layout());
        channel.open(details);
        return channel;
    }

    @Test
    void writesHtmlAndTextWhereTheRecordSaysTheyAre(@TempDir Path root) throws Exception {
        CatalogDetails details = OutputFixtures.catalogDetails();
        CrawledPage page = OutputFixtures.page("https://www.example.com/a", "A", "body of a");
        ResourceRecord record = OutputFixtures.record(page);

        try (FileOutputChannel channel = open(root, details)) {
            channel.write(new OutputPayload(details, record, page));
            channel.flush();
        }

        Path html = root.resolve(record.resource().getHtmlFilePath());
        Path text = root.resolve(record.resource().getHtmlContentFilePath());
        assertThat(html).exists();
        assertThat(text).exists();
        assertThat(Files.readString(text)).isEqualTo("body of a");
    }

    @Test
    void versionAndShardingAreInThePath(@TempDir Path root) throws Exception {
        CatalogDetails details = OutputFixtures.catalogDetails();
        CrawledPage page = OutputFixtures.page("https://www.example.com/a", "A", "text");
        ResourceRecord record = OutputFixtures.record(page);

        assertThat(record.resource().getHtmlFilePath())
                .startsWith(OutputFixtures.CATALOG_ID + "/v0/pages/");
        // two levels of two hex characters, taken from the front of the id
        String id = record.resource().getId().replace("-", "");
        assertThat(record.resource().getHtmlFilePath())
                .contains("/" + id.substring(0, 2) + "/" + id.substring(2, 4) + "/");
    }

    @Test
    void writesImageBytes(@TempDir Path root) throws Exception {
        CatalogDetails details = OutputFixtures.catalogDetails();
        CrawledPage page =
                OutputFixtures.pageWithImage("https://www.example.com/b", "B", "with a picture");
        ResourceRecord record = OutputFixtures.record(page);

        try (FileOutputChannel channel = open(root, details)) {
            channel.write(new OutputPayload(details, record, page));
        }

        Path image = root.resolve(record.images().get(0).image().getImageFilePath());
        assertThat(image).exists();
        assertThat(Files.readAllBytes(image)).containsExactly(1, 2, 3, 4);
        assertThat(record.images().get(0).image().getImageFilePath()).endsWith(".jpg");
    }

    @Test
    void settingsAreWrittenBesideTheVersion(@TempDir Path root) throws Exception {
        CatalogDetails details = OutputFixtures.catalogDetails();
        FileOutputChannel channel = open(root, details);
        channel.setRunSummarySupplier(() -> Map.of("savedResourceCount", 3));
        channel.close();

        Path settings = root.resolve(OutputFixtures.layout().settings());
        assertThat(settings).exists();
        Map<?, ?> parsed = objectMapper.readValue(Files.readString(settings), Map.class);
        assertThat(parsed.get("name")).isEqualTo("example");
        assertThat(parsed.get("version")).isEqualTo(0);
        assertThat(((Map<?, ?>) parsed.get("lastRun")).get("savedResourceCount")).isEqualTo(3);
    }

    @Test
    void replayWritesNothing(@TempDir Path root) throws Exception {
        CatalogDetails details = OutputFixtures.catalogDetails();
        CrawledPage page = OutputFixtures.page("https://www.example.com/a", "A", "t");
        ResourceRecord record = OutputFixtures.record(page);

        try (FileOutputChannel channel = open(root, details)) {
            // page is null: the files are the input to a replay, not its output
            channel.write(new OutputPayload(details, record, null));
        }
        assertThat(root.resolve(record.resource().getHtmlFilePath())).doesNotExist();
    }

    @Test
    void layoutNamesTheVersionPrefixForDeletion() {
        FileLayout layout = new FileLayout("my catalog", 3, 2);
        assertThat(layout.versionPrefix()).isEqualTo("my_catalog/v3");
        assertThat(layout.settings()).isEqualTo("my_catalog/v3/settings.json");
    }

}
