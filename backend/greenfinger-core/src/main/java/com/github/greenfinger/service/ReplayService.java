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

package com.github.greenfinger.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsService;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.output.BlobStore;
import com.github.greenfinger.core.output.OutputChannel;
import com.github.greenfinger.core.output.OutputPayload;
import com.github.greenfinger.core.record.ResourceRecord;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.core.utils.BeanLifeCycleUtils;
import com.github.greenfinger.output.CompositeOutputChannel;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.output.vector.EmbeddingClient;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Rebuilds the index or the vector store from the database, without crawling anything again.
 *
 * <p>
 * The database keeps every version's metadata and the paths to its files, so an index that was
 * dropped by accident, a vector collection that was recreated, or documents lost while
 * Elasticsearch was briefly down can all be restored from what is already on hand.
 *
 * <p>
 * It is an overwrite rather than an append, and that is safe for exactly one reason: every id is a
 * name-based UUID derived from natural keys, so replaying produces the same ids as the original
 * write and lands on top of it instead of beside it.
 * 
 * @Description: ReplayService
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class ReplayService {

    private static final int PAGE_SIZE = 200;

    private final OutputFactory outputFactory;
    private final ResourceRecordStore recordStore;
    private final CatalogDetailsService catalogDetailsService;
    private final FileRestorer fileRestorer;

    /** What the last restore of the file layer did; empty when this replay did not touch it. */
    @Getter
    private volatile FileRestorer.Result lastFileRestore;

    /**
     * @param layers which outputs to rebuild
     * @return how many pages were replayed
     */
    public long replay(String catalogId, int version, Set<OutputType> layers) throws Exception {
        return replaySlice(catalogId, version, layers, 0, Integer.MAX_VALUE);
    }

    /**
     * The same rebuild, over a range of the version's pages rather than all of them.
     *
     * <p>
     * A range because this is the one operation in the system with a scatter-gather shape: the
     * work is known in advance, it fans out once, and the expensive part -- embedding -- is pure
     * arithmetic over a page that is already on disk. So it can be cut into slices and given to
     * several nodes, and a slice that fails can simply be done again: every id downstream is a
     * name-based UUID of the natural key, so a repeat lands on top of itself.
     *
     * <p>
     * Ordering is by the store's own page order, which is stable for a version that is not being
     * crawled -- and a version being crawled is not a version anybody should be replaying.
     *
     * @param offset how many pages to skip
     * @param limit  how many to do; {@code Integer.MAX_VALUE} for the rest
     */
    public long replaySlice(String catalogId, int version, Set<OutputType> layers, int offset,
            int limit) throws Exception {
        CatalogDetails catalogDetails = catalogDetailsService.loadCatalogDetails(catalogId);
        List<OutputType> targets = new ArrayList<>(layers);
        boolean restoringFiles = targets.remove(OutputType.FILE) && fileRestorer != null;

        // Files first, and it has to be first: the index and the vectors are built from the text
        // read back out of the file store, so rebuilding them before the files are back would
        // index nothing at all -- silently, since a missing file reads as empty rather than as an
        // error. Doing both in one call therefore repairs a version completely.
        long restored = 0L;
        if (restoringFiles) {
            BlobStore fileStore = null;
            try {
                fileStore = outputFactory.getBlobStore();
                BeanLifeCycleUtils.afterPropertiesSet(fileStore);
                FileRestorer.Result result = fileRestorer.restore(catalogDetails, version,
                        fileStore, offset, limit);
                lastFileRestore = result;
                restored = result.checked();
                log.info(
                        "Restored files for catalog {} version {}: {} page(s) and {} image(s)"
                                + " written, {} already there, {} unreachable, {} changed since"
                                + " the crawl",
                        catalogId, version, result.pages(), result.images(), result.intact(),
                        result.unreachable(), result.changed());
            } finally {
                BeanLifeCycleUtils.destroyQuietly(fileStore);
            }
        }
        if (targets.isEmpty()) {
            return restored;
        }

        BlobStore blobStore = null;
        EmbeddingClient embeddingClient = null;
        CompositeOutputChannel outputChannel = null;
        try {
            blobStore = outputFactory.getBlobStore();
            BeanLifeCycleUtils.afterPropertiesSet(blobStore);
            if (targets.contains(OutputType.VECTOR)) {
                // shared with the crawl and the searches, already initialised
                embeddingClient = outputFactory.sharedEmbeddingClient();
            }
            outputChannel = outputFactory.getOutputChannel(
                    new ReplayCatalogDetails(catalogDetails, version, Set.copyOf(targets)),
                    blobStore, embeddingClient);
            outputChannel.open(catalogDetails);

            long replayed = 0L;
            for (int cursor = offset; replayed < limit; cursor += PAGE_SIZE) {
                int size = (int) Math.min(PAGE_SIZE, limit - replayed);
                List<ResourceRecord> batch = recordStore.load(catalogId, version, cursor, size);
                if (batch.isEmpty()) {
                    break;
                }
                for (ResourceRecord record : batch) {
                    // page is null: the files already exist, so the file channel stands aside and
                    // the text is read back from where it was written
                    outputChannel.write(new OutputPayload(catalogDetails, record, null));
                    replayed++;
                }
                log.info("Replayed {} page(s) of catalog {} version {} from offset {}", replayed,
                        catalogId, version, offset);
            }
            outputChannel.flush();
            return replayed;
        } finally {
            closeQuietly(outputChannel);
            // the embedding client is the process's, not this replay's. Closing it here left the
            // next replay -- and the next crawl, and the next semantic search -- talking to a
            // shut model, which reports itself as "local embedding failed" and nothing else
            BeanLifeCycleUtils.destroyQuietly(blobStore);
        }
    }

    private void closeQuietly(OutputChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (Exception e) {
            log.warn("Closing the replay outputs failed: {}", e.getMessage());
        }
    }

    /**
     * The catalog as the replay sees it: pinned to the version being rebuilt, and offering only the
     * outputs the caller asked for.
     */
    private record ReplayCatalogDetails(CatalogDetails delegate, int version,
            Set<OutputType> outputs) implements CatalogDetails {

        @Override
        public String getId() {
            return delegate.getId();
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public String getUrl() {
            return delegate.getUrl();
        }

        @Override
        public String getStartUrl() {
            return delegate.getStartUrl();
        }

        @Override
        public String getCategory() {
            return delegate.getCategory();
        }

        @Override
        public List<String> getPathPatterns() {
            return delegate.getPathPatterns();
        }

        @Override
        public List<String> getExcludedPathPatterns() {
            return delegate.getExcludedPathPatterns();
        }

        @Override
        public String getPageEncoding() {
            return delegate.getPageEncoding();
        }

        @Override
        public Integer getMaxFetchSize() {
            return delegate.getMaxFetchSize();
        }

        @Override
        public Integer getMaxFetchDepth() {
            return delegate.getMaxFetchDepth();
        }

        @Override
        public com.github.greenfinger.core.component.extractor.ThreadWait getThreadWait() {
            return delegate.getThreadWait();
        }

        @Override
        public Long getFetchInterval() {
            return delegate.getFetchInterval();
        }

        @Override
        public Long getFetchDuration() {
            return delegate.getFetchDuration();
        }

        @Override
        public com.github.greenfinger.core.component.state.CountingType getCountingType() {
            return delegate.getCountingType();
        }

        @Override
        public Integer getMaxRetryCount() {
            return delegate.getMaxRetryCount();
        }

        @Override
        public List<String> getUrlPathAcceptors() {
            return delegate.getUrlPathAcceptors();
        }

        @Override
        public String getUrlPathFilter() {
            return delegate.getUrlPathFilter();
        }

        @Override
        public com.github.greenfinger.core.model.ExtractorType getExtractor() {
            return delegate.getExtractor();
        }

        @Override
        public Integer getVersion() {
            return version;
        }

        @Override
        public Integer getSearchVersion() {
            return delegate.getSearchVersion();
        }

        @Override
        public Integer getMaxVersions() {
            return delegate.getMaxVersions();
        }

        @Override
        public String getRunningState() {
            return delegate.getRunningState();
        }

        @Override
        public Set<OutputType> getOutputTypes() {
            return outputs;
        }

        @Override
        public boolean isImageEnabled() {
            return delegate.isImageEnabled();
        }

        @Override
        public com.github.greenfinger.core.model.ContentMode getContentMode() {
            return delegate.getContentMode();
        }

    }

}
