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

package com.github.greenfinger.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.output.ContentReader;
import com.github.greenfinger.core.output.OutputChannel;
import com.github.greenfinger.core.output.OutputPayload;

/**
 * The order the outputs run in, and what happens when one of them fails.
 * 
 * @Description: CompositeOutputChannelTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
class CompositeOutputChannelTest {

    /** Records what it was asked to do, and fails on demand. */
    private static class SpyChannel implements OutputChannel {

        private final OutputType type;
        private final List<String> events;
        private boolean failOnWrite;
        private boolean failOnOpen;

        SpyChannel(OutputType type, List<String> events) {
            this.type = type;
            this.events = events;
        }

        @Override
        public String getName() {
            return type.getRepr();
        }

        @Override
        public OutputType getType() {
            return type;
        }

        @Override
        public void open(CatalogDetails catalogDetails) {
            events.add("open:" + getName());
            if (failOnOpen) {
                throw new IllegalStateException(getName() + " is unreachable");
            }
        }

        @Override
        public void write(OutputPayload payload) {
            events.add("write:" + getName() + ":" + payload.getText());
            if (failOnWrite) {
                throw new IllegalStateException(getName() + " is having a bad day");
            }
        }

        @Override
        public void flush() {
            events.add("flush:" + getName());
        }

        @Override
        public void close() {
            events.add("close:" + getName());
        }
    }

    private static class FixedContentReader implements ContentReader {

        private final String text;

        FixedContentReader(String text) {
            this.text = text;
        }

        @Override
        public Optional<String> readText(String path) {
            return Optional.ofNullable(text);
        }

        @Override
        public Optional<byte[]> readBytes(String path) {
            return Optional.empty();
        }
    }

    private OutputPayload payload(CatalogDetails details) {
        return OutputFixtures.payload(details,
                OutputFixtures.page("https://www.example.com/a", "A", "in memory"));
    }

    @Test
    @DisplayName("file first, then index, then vector, whatever order they were handed over in")
    void runsInTheOneAllowedOrder() throws Exception {
        List<String> events = new ArrayList<>();
        CompositeOutputChannel composite = new CompositeOutputChannel(
                List.of(new SpyChannel(OutputType.VECTOR, events),
                        new SpyChannel(OutputType.INDEX, events),
                        new SpyChannel(OutputType.FILE, events)),
                new FixedContentReader("read back"));

        CatalogDetails details = OutputFixtures.catalogDetails();
        composite.open(details);
        composite.write(payload(details));

        assertThat(events).containsSubsequence("write:file:in memory", "write:index:read back",
                "write:vector:read back");
    }

    @Test
    @DisplayName("the later layers are handed what the file layer wrote, not what was in memory")
    void readsTheTextBackBeforeIndexing() throws Exception {
        List<String> events = new ArrayList<>();
        CompositeOutputChannel composite = new CompositeOutputChannel(
                List.of(new SpyChannel(OutputType.FILE, events),
                        new SpyChannel(OutputType.INDEX, events)),
                new FixedContentReader("read back"));

        CatalogDetails details = OutputFixtures.catalogDetails();
        composite.open(details);
        composite.write(payload(details));

        assertThat(events).contains("write:file:in memory").contains("write:index:read back");
    }

    @Test
    @DisplayName("an unreadable file leaves the in-memory text rather than indexing nothing")
    void fallsBackWhenTheReadBackComesUpEmpty() throws Exception {
        List<String> events = new ArrayList<>();
        CompositeOutputChannel composite = new CompositeOutputChannel(
                List.of(new SpyChannel(OutputType.FILE, events),
                        new SpyChannel(OutputType.INDEX, events)),
                new FixedContentReader(null));

        CatalogDetails details = OutputFixtures.catalogDetails();
        composite.open(details);
        composite.write(payload(details));

        assertThat(events).contains("write:index:in memory");
    }

    @Test
    @DisplayName("the file layer is the source, so its failure stops the page")
    void aFileFailureIsFatal() throws Exception {
        List<String> events = new ArrayList<>();
        SpyChannel file = new SpyChannel(OutputType.FILE, events);
        file.failOnWrite = true;
        CompositeOutputChannel composite = new CompositeOutputChannel(
                List.of(file, new SpyChannel(OutputType.INDEX, events)),
                new FixedContentReader("read back"));

        CatalogDetails details = OutputFixtures.catalogDetails();
        composite.open(details);

        assertThatThrownBy(() -> composite.write(payload(details)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(events).noneMatch(e -> e.startsWith("write:index"));
    }

    @Test
    @DisplayName("one Elasticsearch hiccup is counted, not fatal: a replay can fill the gap")
    void anIndexFailureIsCountedAndTheCrawlCarriesOn() throws Exception {
        List<String> events = new ArrayList<>();
        SpyChannel index = new SpyChannel(OutputType.INDEX, events);
        index.failOnWrite = true;
        CompositeOutputChannel composite = new CompositeOutputChannel(
                List.of(new SpyChannel(OutputType.FILE, events), index,
                        new SpyChannel(OutputType.VECTOR, events)),
                new FixedContentReader("read back"));

        CatalogDetails details = OutputFixtures.catalogDetails();
        composite.open(details);
        composite.write(payload(details));

        assertThat(composite.getFailures().get("index").get()).isEqualTo(1L);
        // the vector layer still ran
        assertThat(events).contains("write:vector:read back");
    }

    @Test
    void opensAndClosesEveryChannel() throws Exception {
        List<String> events = new ArrayList<>();
        CompositeOutputChannel composite = new CompositeOutputChannel(
                List.of(new SpyChannel(OutputType.FILE, events),
                        new SpyChannel(OutputType.INDEX, events)),
                new FixedContentReader("x"));

        CatalogDetails details = OutputFixtures.catalogDetails();
        composite.open(details);
        composite.flush();
        composite.close();

        assertThat(events).contains("open:file", "open:index", "flush:file", "flush:index",
                "close:file", "close:index");
    }

    @Test
    @DisplayName("closing carries on past a failure, then reports it")
    void closeReportsAFailureAfterTryingThemAll() throws Exception {
        List<String> events = new ArrayList<>();
        OutputChannel broken = new SpyChannel(OutputType.INDEX, events) {
            @Override
            public void close() {
                throw new IllegalStateException("will not close");
            }
        };
        CompositeOutputChannel composite = new CompositeOutputChannel(
                List.of(new SpyChannel(OutputType.FILE, events), broken),
                new FixedContentReader("x"));

        composite.open(OutputFixtures.catalogDetails());
        assertThatThrownBy(composite::close).isInstanceOf(IllegalStateException.class);
        assertThat(events).contains("close:file");
    }

    @Test
    void namesEveryChannelItCarries() {
        List<String> events = new ArrayList<>();
        CompositeOutputChannel composite = new CompositeOutputChannel(
                List.of(new SpyChannel(OutputType.FILE, events),
                        new SpyChannel(OutputType.INDEX, events)),
                new FixedContentReader("x"));

        assertThat(composite.getName()).isEqualTo("file+index");
        assertThat(composite.getType()).isEqualTo(OutputType.FILE);
        assertThat(composite.getChannels()).hasSize(2);
    }


    @Test
    @DisplayName("an Elasticsearch that is down costs the index, not the pages")
    void carriesOnWhenAnOptionalOutputWillNotOpen() throws Exception {
        List<String> events = new ArrayList<>();
        SpyChannel index = new SpyChannel(OutputType.INDEX, events);
        index.failOnOpen = true;
        CompositeOutputChannel composite = new CompositeOutputChannel(
                List.of(new SpyChannel(OutputType.FILE, events), index,
                        new SpyChannel(OutputType.VECTOR, events)),
                new FixedContentReader("read back"));

        CatalogDetails details = OutputFixtures.catalogDetails();
        composite.open(details);
        composite.write(payload(details));

        // the file layer and the vectors carried on; the index was dropped for this run
        assertThat(composite.getActiveChannels()).extracting(OutputChannel::getType)
                .containsExactly(OutputType.FILE, OutputType.VECTOR);
        assertThat(events).contains("write:file:in memory", "write:vector:read back");
        assertThat(events).noneMatch(e -> e.startsWith("write:index"));
        assertThat(composite.getFailures().get("index").get()).isEqualTo(1L);
    }

    @Test
    @DisplayName("the file layer failing to open is still fatal: there is nothing to build on")
    void aFileLayerThatWillNotOpenIsFatal() {
        List<String> events = new ArrayList<>();
        SpyChannel file = new SpyChannel(OutputType.FILE, events);
        file.failOnOpen = true;
        CompositeOutputChannel composite = new CompositeOutputChannel(
                List.of(file, new SpyChannel(OutputType.INDEX, events)),
                new FixedContentReader("x"));

        assertThatThrownBy(() -> composite.open(OutputFixtures.catalogDetails()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a dropped channel is not flushed or closed either")
    void aDroppedChannelIsLeftOutOfEverything() throws Exception {
        List<String> events = new ArrayList<>();
        SpyChannel index = new SpyChannel(OutputType.INDEX, events);
        index.failOnOpen = true;
        CompositeOutputChannel composite = new CompositeOutputChannel(
                List.of(new SpyChannel(OutputType.FILE, events), index),
                new FixedContentReader("x"));

        composite.open(OutputFixtures.catalogDetails());
        composite.flush();
        composite.close();

        assertThat(events).contains("flush:file", "close:file");
        assertThat(events).noneMatch(e -> e.startsWith("flush:index"))
                .noneMatch(e -> e.startsWith("close:index"));
    }

}
