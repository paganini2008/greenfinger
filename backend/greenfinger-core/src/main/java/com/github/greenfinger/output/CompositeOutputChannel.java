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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.output.ContentReader;
import com.github.greenfinger.core.output.OutputChannel;
import com.github.greenfinger.core.output.OutputPayload;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs the outputs in the one order they are allowed to run in.
 *
 * <pre>
 * file  --&gt;  read the text back  --&gt;  index  --&gt;  vector
 * </pre>
 *
 * <p>
 * The text handed to the index and the vector store is read back from the file layer rather than
 * taken from the page still in memory. It costs one read, and it buys the guarantee that replaying
 * a layer later works from exactly the same bytes and produces exactly the same result.
 *
 * <p>
 * A failure in the file layer stops the page: it is the source everything else is rebuilt from. A
 * failure in the index or the vector store is counted and logged, and the crawl carries on -- one
 * Elasticsearch hiccup must not destroy a whole run, and what was missed can be replayed from the
 * database afterwards.
 * 
 * @Description: CompositeOutputChannel
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class CompositeOutputChannel implements OutputChannel {

    private final List<OutputChannel> channels;
    private final ContentReader contentReader;

    /** Per channel failure counts, reported in the run summary. */
    @Getter
    private final Map<String, AtomicLong> failures = new LinkedHashMap<>();

    /** Filled in by {@link #open}; the channels that were actually reachable. */
    private volatile List<OutputChannel> active = List.of();

    public CompositeOutputChannel(List<OutputChannel> channels, ContentReader contentReader) {
        // file first, then index, then vector -- the enum's own order
        this.channels = channels.stream()
                .sorted((a, b) -> a.getType().ordinal() - b.getType().ordinal()).toList();
        this.contentReader = contentReader;
        this.channels.forEach(c -> failures.put(c.getName(), new AtomicLong()));
    }

    public List<OutputChannel> getChannels() {
        return channels;
    }

    /**
     * The channels that actually opened. Everything after {@code open} works from this, so a
     * destination that was unreachable at the start is not asked for the rest of the run.
     */
    public List<OutputChannel> getActiveChannels() {
        return active;
    }

    @Override
    public String getName() {
        return channels.stream().map(OutputChannel::getName).reduce((a, b) -> a + "+" + b)
                .orElse("none");
    }

    @Override
    public OutputType getType() {
        return OutputType.FILE;
    }

    /**
     * Prepares each destination, and drops the ones that cannot be prepared.
     *
     * <p>
     * A destination that will not open is the same problem as one that will not accept a page, and
     * gets the same answer: fatal for the file layer, which everything else is built from, and
     * merely noted for the others. An Elasticsearch that happens to be down should cost the crawl
     * its index, not its pages -- they are on disk and in the database, and {@code replay} builds
     * the index from them once it is back.
     */
    @Override
    public void open(CatalogDetails catalogDetails) throws Exception {
        List<OutputChannel> opened = new ArrayList<>();
        for (OutputChannel channel : channels) {
            try {
                channel.open(catalogDetails);
                opened.add(channel);
            } catch (Exception e) {
                if (channel.isRequired()) {
                    throw e;
                }
                failures.get(channel.getName()).incrementAndGet();
                log.warn("Output '{}' could not be opened and is skipped for this run: {}."
                        + " Fill it in afterwards with: replay --catalog {} --layers {}",
                        channel.getName(), e.getMessage(), catalogDetails.getName(),
                        channel.getType().getRepr());
            }
        }
        this.active = List.copyOf(opened);
    }

    @Override
    public void write(OutputPayload payload) throws Exception {
        boolean textLoaded = false;
        for (OutputChannel channel : active) {
            if (channel.getType() != OutputType.FILE && !textLoaded) {
                loadText(payload);
                textLoaded = true;
            }
            try {
                channel.write(payload);
            } catch (Exception e) {
                if (channel.isRequired()) {
                    throw e;
                }
                failures.get(channel.getName()).incrementAndGet();
                log.warn("Output '{}' failed on '{}': {}", channel.getName(), payload.getUrl(),
                        e.getMessage());
            }
        }
    }

    /**
     * Reads the .txt the file layer just wrote. Falls back to what is in memory when the read comes
     * back empty, so a blob store that is eventually consistent cannot silently index nothing.
     */
    private void loadText(OutputPayload payload) {
        String path = payload.getRecord().resource().getHtmlContentFilePath();
        try {
            contentReader.readText(path).filter(t -> !t.isBlank()).ifPresent(payload::setText);
        } catch (Exception e) {
            log.warn("Could not read back '{}': {}", path, e.getMessage());
        }
    }

    @Override
    public void flush() throws Exception {
        for (OutputChannel channel : active) {
            try {
                channel.flush();
            } catch (Exception e) {
                if (channel.isRequired()) {
                    throw e;
                }
                log.warn("Flushing '{}' failed: {}", channel.getName(), e.getMessage());
            }
        }
    }

    @Override
    public void close() throws Exception {
        List<Exception> problems = new ArrayList<>();
        for (OutputChannel channel : active) {
            try {
                channel.close();
            } catch (Exception e) {
                problems.add(e);
                log.warn("Closing '{}' failed: {}", channel.getName(), e.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            Exception first = problems.get(0);
            problems.stream().skip(1).forEach(first::addSuppressed);
            throw first;
        }
    }

}
