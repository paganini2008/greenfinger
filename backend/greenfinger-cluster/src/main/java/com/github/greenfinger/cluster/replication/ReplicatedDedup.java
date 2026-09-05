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

package com.github.greenfinger.cluster.replication;

import com.github.greenfinger.core.component.dedup.ContentDedupFilter;
import com.github.greenfinger.core.component.dedup.ExistingUrlPathFilter;
import lombok.extern.slf4j.Slf4j;

/**
 * The two dedup filters, told to the rest of the cluster as they learn.
 *
 * <p>
 * Both are RocksDB stores, so each node has its own file and knows only what it saw. With urls
 * dispatched round robin, a url discovered on two different pages usually lands on two different
 * nodes, and without this both would fetch it: one page, fetched twice, written twice.
 *
 * <h2>It is best effort, and that is survivable</h2>
 * Replication is asynchronous, so two nodes can decide the same url is new within the same
 * millisecond and both fetch it. What that costs is one wasted request -- not a duplicate row, not
 * a duplicate file, not a duplicate vector -- because every id downstream is derived from the url:
 * the second write lands on the same row, the same path and the same point, and overwrites itself.
 * The filter is an optimisation with a correctness backstop underneath it, which is why "mostly
 * agreed, quickly" is the right trade rather than a lock per url.
 * 
 * @Description: ReplicatedDedup
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@Slf4j
public final class ReplicatedDedup {

    /** A url somebody else has taken. */
    public static final byte OP_URL = 1;

    /** Text somebody else has already stored under some url. */
    public static final byte OP_CONTENT = 2;

    private ReplicatedDedup() {}

    /**
     * Applies what another node learned.
     *
     * <p>
     * The delegate's own record-and-report method is the way in -- recording is exactly what is
     * wanted and the answer is not. It has to be the <em>delegate</em>, never the wrapper: going
     * through the wrapper would announce what was just received, and the announcement would come
     * back, and the cluster would spend itself telling itself the same url forever.
     */
    public static void apply(ReplicationBatch.Entry entry, ExistingUrlPathFilter urls,
            ContentDedupFilter contents) {
        switch (entry.op()) {
            case OP_URL -> urls.mightExist(entry.key());
            case OP_CONTENT -> contents.isDuplicate(entry.valueAsText());
            default -> log.debug("Unknown dedup op: {}", entry.op());
        }
    }

    /**
     * Wraps the url filter so that every url this node claims is announced.
     * 
     * @Description: Urls
     * @Author: Fred Feng
     * @Date: 02/09/2026
     * @Version 2.0.0
     */
    public static class Urls implements ExistingUrlPathFilter {

        private final ExistingUrlPathFilter delegate;
        private final ReplicationSink channel;
        private final String scope;

        public Urls(ExistingUrlPathFilter delegate, ReplicationSink channel, String scope) {
            this.delegate = delegate;
            this.channel = channel;
            this.scope = scope;
        }

        @Override
        public String getName() {
            return "replicated:" + delegate.getName();
        }

        @Override
        public boolean mightExist(String path) {
            boolean seen = delegate.mightExist(path);
            if (!seen) {
                // only when this node is the one that claimed it: re-announcing what somebody
                // else told us would echo around the cluster forever
                channel.replicate(ReplicationBatch.Entry.of(OP_URL, scope, path));
            }
            return seen;
        }

        @Override
        public void afterPropertiesSet() throws Exception {
            delegate.afterPropertiesSet();
        }

        @Override
        public void destroy() throws Exception {
            delegate.destroy();
        }

        @Override
        public void clean() throws Exception {
            delegate.clean();
        }

        @Override
        public long size() throws Exception {
            return delegate.size();
        }

        @Override
        public int export(com.github.greenfinger.core.component.dedup.UrlPathFilterExporter
                exporter, boolean deleted) throws Exception {
            return delegate.export(exporter, deleted);
        }

        public ExistingUrlPathFilter unwrap() {
            return delegate;
        }
    }

    /**
     * Wraps the content filter the same way. The fingerprint travels, not the text: a page body is
     * kilobytes and the filter only ever compares hashes.
     * 
     * @Description: Contents
     * @Author: Fred Feng
     * @Date: 02/09/2026
     * @Version 2.0.0
     */
    public static class Contents implements ContentDedupFilter {

        private final ContentDedupFilter delegate;
        private final ReplicationSink channel;
        private final String scope;

        public Contents(ContentDedupFilter delegate, ReplicationSink channel, String scope) {
            this.delegate = delegate;
            this.channel = channel;
            this.scope = scope;
        }

        @Override
        public String getName() {
            return "replicated:" + delegate.getName();
        }

        @Override
        public boolean isDuplicate(String text) {
            boolean duplicate = delegate.isDuplicate(text);
            if (!duplicate) {
                String fingerprint = delegate.fingerprint(text);
                if (fingerprint != null) {
                    channel.replicate(ReplicationBatch.Entry.of(OP_CONTENT, scope, fingerprint,
                            text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                }
            }
            return duplicate;
        }

        @Override
        public String fingerprint(String text) {
            return delegate.fingerprint(text);
        }

        @Override
        public void afterPropertiesSet() throws Exception {
            delegate.afterPropertiesSet();
        }

        @Override
        public void destroy() throws Exception {
            delegate.destroy();
        }

        @Override
        public void clean() throws Exception {
            delegate.clean();
        }

        @Override
        public long size() throws Exception {
            return delegate.size();
        }

        public ContentDedupFilter unwrap() {
            return delegate;
        }
    }

}
