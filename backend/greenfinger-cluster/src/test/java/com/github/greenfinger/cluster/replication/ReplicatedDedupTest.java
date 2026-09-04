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

import static org.assertj.core.api.Assertions.assertThat;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.cluster.support.CapturingSink;
import com.github.greenfinger.core.component.dedup.ContentDedupFilter;
import com.github.greenfinger.core.component.dedup.ExistingUrlPathFilter;

/**
 * Telling the other nodes which urls and which pages are taken.
 *
 * <p>
 * The one thing that must never happen here is an echo: a url learned from a peer, announced back
 * to it, announced back again. With three nodes that is not a nuisance, it is the cluster
 * saturating itself with one url.
 * 
 * @Description: ReplicatedDedupTest
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
class ReplicatedDedupTest {

    private final CapturingSink sink = new CapturingSink();

    @Test
    @DisplayName("a url this node claims is announced")
    void announcesWhatItClaims() {
        MemoryUrls delegate = new MemoryUrls();
        ReplicatedDedup.Urls urls = new ReplicatedDedup.Urls(delegate, sink, "cat-1");

        assertThat(urls.mightExist("https://example.com/a")).isFalse();

        assertThat(sink.keys()).containsExactly("https://example.com/a");
        assertThat(sink.entries().get(0).op()).isEqualTo(ReplicatedDedup.OP_URL);
        assertThat(sink.entries().get(0).scope()).isEqualTo("cat-1");
    }

    @Test
    @DisplayName("a url it already had is not announced again")
    void saysNothingTwice() {
        ReplicatedDedup.Urls urls = new ReplicatedDedup.Urls(new MemoryUrls(), sink, "cat-1");
        urls.mightExist("https://example.com/a");
        sink.clear();

        assertThat(urls.mightExist("https://example.com/a")).isTrue();

        assertThat(sink.entries()).isEmpty();
    }

    @Test
    @DisplayName("applying goes to the delegate, so what arrives is not announced back")
    void applyingDoesNotEcho() {
        MemoryUrls delegate = new MemoryUrls();
        ReplicatedDedup.Urls urls = new ReplicatedDedup.Urls(delegate, sink, "cat-1");

        ReplicatedDedup.apply(
                ReplicationBatch.Entry.of(ReplicatedDedup.OP_URL, "cat-1", "https://peer.test/x"),
                delegate, new MemoryContents());

        assertThat(sink.entries()).isEmpty();
        // and it took: the url is now known here as well
        assertThat(urls.mightExist("https://peer.test/x")).isTrue();
    }

    @Test
    void contentFingerprintsTravelWithTheirText() {
        MemoryContents delegate = new MemoryContents();
        ReplicatedDedup.Contents contents = new ReplicatedDedup.Contents(delegate, sink, "cat-1");

        assertThat(contents.isDuplicate("the body of a page")).isFalse();

        assertThat(sink.ops()).containsExactly(ReplicatedDedup.OP_CONTENT);
        assertThat(sink.entries().get(0).valueAsText()).isEqualTo("the body of a page");
        assertThat(sink.entries().get(0).key()).isEqualTo(delegate.fingerprint("the body of a page"));
    }

    @Test
    @DisplayName("a page seen elsewhere is a duplicate here too")
    void appliedContentIsRecognised() {
        MemoryContents delegate = new MemoryContents();
        ReplicatedDedup.Contents contents = new ReplicatedDedup.Contents(delegate, sink, "cat-1");

        ReplicatedDedup.apply(ReplicationBatch.Entry.of(ReplicatedDedup.OP_CONTENT, "cat-1", "h",
                "shared text".getBytes(java.nio.charset.StandardCharsets.UTF_8)), new MemoryUrls(),
                delegate);
        sink.clear();

        assertThat(contents.isDuplicate("shared text")).isTrue();
        assertThat(sink.entries()).isEmpty();
    }

    @Test
    void anUnknownOpIsIgnoredRatherThanFatal() {
        MemoryUrls urls = new MemoryUrls();
        ReplicatedDedup.apply(ReplicationBatch.Entry.of((byte) 99, "cat-1", "x"), urls,
                new MemoryContents());
        assertThat(urls.seen).isEmpty();
    }

    @Test
    void theWrappersPassTheirLifecycleThrough() throws Exception {
        MemoryUrls delegate = new MemoryUrls();
        ReplicatedDedup.Urls urls = new ReplicatedDedup.Urls(delegate, sink, "cat-1");
        urls.afterPropertiesSet();
        urls.mightExist("a");
        assertThat(urls.size()).isEqualTo(1);
        assertThat(urls.getName()).startsWith("replicated:");
        assertThat(urls.unwrap()).isSameAs(delegate);
        urls.clean();
        assertThat(delegate.seen).isEmpty();
        urls.destroy();

        MemoryContents contents = new MemoryContents();
        ReplicatedDedup.Contents wrapped = new ReplicatedDedup.Contents(contents, sink, "cat-1");
        wrapped.afterPropertiesSet();
        wrapped.isDuplicate("x");
        assertThat(wrapped.size()).isEqualTo(1);
        assertThat(wrapped.getName()).startsWith("replicated:");
        assertThat(wrapped.unwrap()).isSameAs(contents);
        wrapped.clean();
        wrapped.destroy();
    }

    /**
     * The real filter is RocksDB on disk. What matters here is only "have I seen this", so a set
     * says the same thing without a temporary directory per test.
     */
    private static class MemoryUrls implements ExistingUrlPathFilter {

        private final Set<String> seen = new HashSet<>();

        @Override
        public String getName() {
            return "memory";
        }

        @Override
        public boolean mightExist(String path) {
            return !seen.add(path);
        }

        @Override
        public void clean() {
            seen.clear();
        }

        @Override
        public long size() {
            return seen.size();
        }
    }

    /**
     * Fingerprints by content, as the real one does; the real one hashes, and the hash is not what
     * is under test.
     */
    private static class MemoryContents implements ContentDedupFilter {

        private final Set<String> seen = new HashSet<>();

        @Override
        public String getName() {
            return "memory";
        }

        @Override
        public boolean isDuplicate(String text) {
            return !seen.add(fingerprint(text));
        }

        @Override
        public String fingerprint(String text) {
            return "fp-" + text.hashCode();
        }

        @Override
        public void clean() {
            seen.clear();
        }

        @Override
        public long size() {
            return seen.size();
        }
    }

}
