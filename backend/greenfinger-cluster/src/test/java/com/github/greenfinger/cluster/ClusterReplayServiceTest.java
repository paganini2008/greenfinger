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

package com.github.greenfinger.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.chaconneai.openspreader.pooling.PoolStats;
import com.chaconneai.openspreader.pooling.ProcessingPool;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.record.ResourceRecordStore;

/**
 * Splitting a replay into slices, and knowing when not to.
 *
 * <p>
 * What the pool then does with a slice -- pick a node, fall back locally, retry -- is the pool's
 * own and has its own tests. What is asserted here is the arithmetic in front of it: how many
 * slices, how big, and the two cases where there should be none at all.
 * 
 * @Description: ClusterReplayServiceTest
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
class ClusterReplayServiceTest {

    @Test
    @DisplayName("alone, the whole thing runs here -- which is what it always did")
    void aloneItDoesTheWholeThing() throws Exception {
        RecordingPool pool = new RecordingPool(0);
        Replay replay = new Replay(pool, 10_000);

        assertThat(replay.replay("cat-1", 0, Set.of(OutputType.INDEX))).isEqualTo(10_000);
        assertThat(pool.calls).isEmpty();
        assertThat(replay.ranWholeThingLocally).isTrue();
    }

    @Test
    @DisplayName("too little to be worth spreading is done here too: the round trips would cost"
            + " more than the work")
    void aTinyReplayIsNotWorthSpreading() throws Exception {
        RecordingPool pool = new RecordingPool(2);
        Replay replay = new Replay(pool, ClusterReplayService.MIN_PAGES_TO_SPREAD - 1);

        replay.replay("cat-1", 0, Set.of(OutputType.VECTOR));

        assertThat(pool.calls).isEmpty();
        assertThat(replay.ranWholeThingLocally).isTrue();
    }

    @Test
    @DisplayName("the slices cover every page exactly once, and there are more of them than nodes")
    void slicesCoverEverything() throws Exception {
        RecordingPool pool = new RecordingPool(2);
        Replay replay = new Replay(pool, 1_000);

        long replayed = replay.replay("cat-1", 3, Set.of(OutputType.INDEX, OutputType.VECTOR));

        // more slices than nodes, so a slow node holds up less of the whole
        assertThat(pool.calls.size()).isGreaterThan(3);
        assertThat(pool.calls.size())
                .isLessThanOrEqualTo(3 * ClusterReplayService.SLICES_PER_NODE);

        // every page, once: the offsets tile [0, 1000) without a gap or an overlap
        List<Integer> offsets = pool.calls.stream().map(call -> (Integer) call[3]).sorted()
                .toList();
        int size = (Integer) pool.calls.get(0)[4];
        for (int i = 0; i < offsets.size(); i++) {
            assertThat(offsets.get(i)).isEqualTo(i * size);
        }
        assertThat(offsets.get(offsets.size() - 1) + size).isGreaterThanOrEqualTo(1_000);

        // and the total is what came back from them
        assertThat(replayed).isEqualTo(pool.calls.size());
    }

    @Test
    @DisplayName("the layers travel as names, because an ordinal is not a durable thing to send")
    void layersTravelAsNames() throws Exception {
        RecordingPool pool = new RecordingPool(2);
        new Replay(pool, 1_000).replay("cat-1", 0, Set.of(OutputType.VECTOR));

        String layers = (String) pool.calls.get(0)[2];
        assertThat(layers).contains("vector");
        assertThat(OutputType.parse(layers)).contains(OutputType.VECTOR);
    }

    @Test
    @DisplayName("a slice is capped however large the catalog, so it fits inside the pool's wait")
    void aSliceIsNeverBiggerThanTheCap() throws Exception {
        RecordingPool pool = new RecordingPool(2);
        // a hundred thousand pages over three nodes: by node count alone a slice would be eleven
        // thousand pages, which is minutes and would time out
        new Replay(pool, 100_000).replay("cat-1", 0, Set.of(OutputType.INDEX));

        assertThat((Integer) pool.calls.get(0)[4])
                .isEqualTo(ClusterReplayService.MAX_SLICE_PAGES);
        assertThat(pool.calls.size()).isEqualTo(500);
    }

    @Test
    @DisplayName("a slice that never comes back is done here rather than failing the replay")
    void aLostSliceIsDoneLocally() throws Exception {
        RecordingPool pool = new RecordingPool(2);
        pool.failFrom(1);
        Replay replay = new Replay(pool, 1_000);

        long replayed = replay.replay("cat-1", 0, Set.of(OutputType.INDEX));

        // every slice accounted for: the ones that answered, and the ones redone here
        assertThat(replayed).isEqualTo(pool.calls.size());
        assertThat(replay.slices).isNotEmpty();
    }

    @Test
    @DisplayName("a slice does the work rather than splitting again: only replay fans out")
    void aSliceDoesNotFanOutAgain() throws Exception {
        RecordingPool pool = new RecordingPool(2);
        Replay replay = new Replay(pool, 1_000);

        replay.replayRange("cat-1", 0, "index", 100, 50);

        assertThat(pool.calls).isEmpty();
        assertThat(replay.slices).containsExactly("100:50");
    }

    /**
     * The real one, with the two things it talks to replaced: the pool records instead of
     * dispatching, and the work records instead of embedding half a gigabyte of text.
     */
    private static class Replay extends ClusterReplayService {

        private final List<String> slices = new ArrayList<>();
        private final long pages;
        private boolean ranWholeThingLocally;

        Replay(ProcessingPool pool, long pages) {
            super(null, new CountingStore(pages), null, null, pool, "clusterReplayService");
            this.pages = pages;
        }

        @Override
        public long replaySlice(String catalogId, int version, Set<OutputType> layers, int offset,
                int limit) {
            // the whole thing, as super.replay asks for it
            if (limit == Integer.MAX_VALUE) {
                ranWholeThingLocally = true;
                return pages;
            }
            slices.add(offset + ":" + limit);
            return 1L;
        }
    }

    /**
     * Answers how many pages a version has, which is the only thing the split needs.
     * 
     * @Description: CountingStore
     * @Author: Fred Feng
     * @Date: 02/09/2026
     * @Version 2.0.0
     */
    private record CountingStore(long pages) implements ResourceRecordStore {

        @Override
        public long countByCatalog(String catalogId, int version) {
            return pages;
        }

        @Override
        public com.github.greenfinger.core.record.ResourceRecord save(
                com.github.greenfinger.core.catalog.CatalogDetails catalogDetails,
                com.github.greenfinger.core.engine.CrawledPage page,
                com.github.greenfinger.core.output.FileLayout layout) {
            return null;
        }

        @Override
        public java.util.Optional<com.github.greenfinger.core.record.ResourceRecord> load(
                String resourceId) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<String> findContentHash(String catalogId, int version,
                String urlHash) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<PageState> findPageState(String catalogId, int version,
                String urlHash) {
            return java.util.Optional.empty();
        }

        @Override
        public List<com.github.greenfinger.core.record.ResourceRecord> load(String catalogId,
                int version, int offset, int limit) {
            return List.of();
        }

        @Override
        public java.util.Optional<String> getLatestReferencePath(String catalogId, int version) {
            return java.util.Optional.empty();
        }

        @Override
        public long countImagesByCatalog(String catalogId, int version) {
            return 0;
        }

        @Override
        public List<Integer> findVersions(String catalogId) {
            return List.of();
        }

        @Override
        public long deleteByCatalog(String catalogId) {
            return 0L;
        }

        @Override
        public long deleteByCatalogAndVersion(String catalogId, int version) {
            return 0;
        }
    }

    /**
     * 
     * @Description: RecordingPool
     * @Author: Fred Feng
     * @Date: 02/09/2026
     * @Version 2.0.0
     */
    private static class RecordingPool implements ProcessingPool {

        private final List<Object[]> calls = new ArrayList<>();
        private final int peers;
        private final AtomicInteger answered = new AtomicInteger();
        private int failFrom = Integer.MAX_VALUE;

        RecordingPool(int peers) {
            this.peers = peers;
        }

        void failFrom(int index) {
            this.failFrom = index;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> CompletableFuture<T> submit(String className, String beanName,
                String methodName, Object[] args) {
            calls.add(args);
            if (answered.getAndIncrement() >= failFrom) {
                return CompletableFuture.failedFuture(new IllegalStateException("no answer"));
            }
            return (CompletableFuture<T>) CompletableFuture.completedFuture(1L);
        }

        @Override
        public void execute(String className, String beanName, String methodName, Object[] args) {
            submit(className, beanName, methodName, args);
        }

        @Override
        public int peerCount() {
            return peers;
        }

        @Override
        public PoolStats poolStats() {
            return null;
        }
    }

}
