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

package com.github.greenfinger.core.engine;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.output.OutputChannel;
import com.github.greenfinger.core.output.OutputPayload;

/**
 * Keeps what the engine handed it, so a test can assert on the pages that came out rather than on
 * files or an index.
 * 
 * @Description: RecordingOutputChannel
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public class RecordingOutputChannel implements OutputChannel {

    private final List<OutputPayload> written = new CopyOnWriteArrayList<>();
    private volatile boolean opened;
    private volatile boolean closed;

    @Override
    public String getName() {
        return "recording";
    }

    @Override
    public OutputType getType() {
        return OutputType.FILE;
    }

    @Override
    public void open(CatalogDetails catalogDetails) {
        opened = true;
    }

    @Override
    public void write(OutputPayload payload) {
        written.add(payload);
    }

    @Override
    public void flush() {
        // nothing is buffered
    }

    @Override
    public void close() {
        closed = true;
    }

    public List<OutputPayload> getWritten() {
        return written;
    }

    public List<String> getUrls() {
        return written.stream().map(OutputPayload::getUrl).toList();
    }

    /** The pages as the engine produced them, for tests that look at titles and text. */
    public List<CrawledPage> getPages() {
        return written.stream().map(OutputPayload::getPage).toList();
    }

    public java.util.Optional<CrawledPage> byUrl(String url) {
        return getPages().stream().filter(p -> url.equals(p.getUrl())).findFirst();
    }

    public boolean isOpened() {
        return opened;
    }

    public boolean isClosed() {
        return closed;
    }

}
