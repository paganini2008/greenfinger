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

package com.github.greenfinger.output.index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.KnnVectorsReader;
import org.apache.lucene.codecs.KnnVectorsWriter;
import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat;
import org.apache.lucene.codecs.perfield.PerFieldKnnVectorsFormat;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import com.github.greenfinger.core.WebCrawlerException;
import lombok.extern.slf4j.Slf4j;

/**
 * The open Lucene indices of this process: one writer and one searcher per directory, shared.
 *
 * <p>
 * Lucene takes a lock on a directory for as long as a writer is open, so a second writer on the
 * same index is not slow, it is a {@code LockObtainFailedException}. Every path into an index --
 * the crawl writing it, a replay rewriting it, a search reading it, a delete removing a version
 * from it -- therefore has to go through one object, and this is it.
 *
 <h2>One per directory, for the whole process</h2>
 * The lock is the file system's, not this object's, so two of these on one root would deadlock
 * each other exactly as two processes would -- and there are two natural places to build one: the
 * plain output factory and the cluster's. {@link #shared} is therefore how they are obtained, and
 * the static cache it keeps is not a convenience but the shape of the resource: one jvm, one set
 * of writers per directory, however many callers there are.
 *
 * <p>
 * Searchers come from a {@link SearcherManager}, which is what makes a search see a crawl that is
 * still running: the writer publishes its segments on commit, and {@code maybeRefresh} picks them
 * up without reopening anything that has not changed. A searcher is borrowed and given back --
 * never closed by the borrower -- because the readers underneath it are shared with whoever else
 * is searching at that moment.
 * 
 * @Description: LuceneIndexes
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class LuceneIndexes implements AutoCloseable {

    private final Path root;
    private final Analyzer analyzer;
    private final Map<String, Open> open = new ConcurrentHashMap<>();

    private static final Map<Path, LuceneIndexes> SHARED = new ConcurrentHashMap<>();

    /**
     * The process's indices under one root, opened once.
     *
     * @param analyzer used only if this is the first caller for that root. An analyzer is a
     *        property of the index rather than of the caller, and the second caller asking for a
     *        different one would be a configuration mistake rather than a request to honour.
     */
    public static LuceneIndexes shared(String directory, Analyzer analyzer) {
        Path root = Paths.get(directory).toAbsolutePath().normalize();
        return SHARED.computeIfAbsent(root, key -> new LuceneIndexes(key, analyzer));
    }

    /**
     * Closes one shared root, and forgets it so the next caller opens it afresh.
     *
     * <p>
     * One root rather than all of them, because "all of them" is not this caller's to decide: two
     * applications in one jvm -- which is what a test run is -- each configure their own
     * directories, and one shutting down must not take the other's writers with it.
     */
    public static void closeShared(String directory) {
        Path root = Paths.get(directory).toAbsolutePath().normalize();
        LuceneIndexes indexes = SHARED.remove(root);
        if (indexes != null) {
            indexes.commitAll();
            indexes.close();
        }
    }

    public LuceneIndexes(String directory, Analyzer analyzer) {
        this(Paths.get(directory).toAbsolutePath().normalize(), analyzer);
    }

    private LuceneIndexes(Path root, Analyzer analyzer) {
        this.root = root;
        this.analyzer = analyzer;
    }

    public Path getRoot() {
        return root;
    }

    public Analyzer getAnalyzer() {
        return analyzer;
    }

    /**
     * One index's writer, opened on first use and kept until this object is closed.
     */
    public IndexWriter writer(String name) {
        return opened(name).writer;
    }

    /**
     * Borrow a searcher. Always in a try/finally with {@link #release}, and never closed.
     *
     * @return null when that index has never been written, which is not an error: a catalog that
     *         has not been crawled simply has no documents.
     */
    public IndexSearcher acquire(String name) throws IOException {
        if (!exists(name)) {
            return null;
        }
        Open index = opened(name);
        index.searchers.maybeRefresh();
        return index.searchers.acquire();
    }

    public void release(String name, IndexSearcher searcher) {
        if (searcher == null) {
            return;
        }
        Open index = open.get(name);
        if (index == null) {
            return;
        }
        try {
            index.searchers.release(searcher);
        } catch (IOException e) {
            log.debug("Could not release a searcher on '{}': {}", name, e.getMessage());
        }
    }

    /**
     * Makes everything written so far visible to the next search.
     */
    public void commit(String name) throws IOException {
        Open index = open.get(name);
        if (index != null) {
            index.writer.commit();
            index.searchers.maybeRefresh();
        }
    }

    public void commitAll() {
        open.keySet().forEach(name -> {
            try {
                commit(name);
            } catch (IOException e) {
                log.warn("Could not commit '{}': {}", name, e.getMessage());
            }
        });
    }

    /**
     * Whether that index has anything on disk yet.
     */
    public boolean exists(String name) {
        return Files.isDirectory(root.resolve(name));
    }

    /**
     * Every index under the root, in name order.
     */
    public List<String> names() {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var children = Files.list(root)) {
            return children.filter(Files::isDirectory).map(path -> path.getFileName().toString())
                    .sorted().toList();
        } catch (IOException e) {
            log.warn("Could not list {}: {}", root, e.getMessage());
            return List.of();
        }
    }

    /**
     * Closes an index and removes its directory, which is how a whole catalog goes.
     *
     * @return true when a directory was actually removed.
     */
    public boolean drop(String name) {
        Open index = open.remove(name);
        if (index != null) {
            index.close();
        }
        Path directory = root.resolve(name);
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (var walk = Files.walk(directory)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
            return true;
        } catch (IOException e) {
            throw new WebCrawlerException("Could not remove the index at " + directory, e);
        }
    }

    private Open opened(String name) {
        return open.computeIfAbsent(name, key -> {
            try {
                Path directory = root.resolve(key);
                Files.createDirectories(directory);
                return new Open(FSDirectory.open(directory), analyzer);
            } catch (IOException e) {
                throw new WebCrawlerException("Could not open the index at "
                        + root.resolve(key), e);
            }
        });
    }

    @Override
    public void close() {
        List<Open> all = new ArrayList<>(open.values());
        open.clear();
        all.forEach(Open::close);
    }

    /**
     * The default codec, with room for the vectors people actually have.
     *
     * <p>
     * A {@link FilterCodec} rather than a format of our own: everything about how a vector is
     * stored and searched stays Lucene's, and the one number that is a policy rather than a
     * format -- how wide a vector may be -- is answered differently. An index written this way is
     * read back by the same codec, which is why it is named and looked up rather than anonymous.
     */
    private static final String DEFAULT_CODEC = "Lucene912";
    private static final String HNSW_FORMAT = "Lucene99HnswVectorsFormat";
    private static final KnnVectorsFormat WIDE_HNSW = new WideHnswFormat();
    private static final Codec WIDE_VECTORS = new WideVectorCodec();

    /** How many floats a vector may have here. Four embeddings in, nothing needs more. */
    static final int MAX_VECTOR_DIMENSIONS = 4096;

    /**
     *
     * @Description: WideVectorCodec
     * @Author: Fred Feng
     * @Date: 23/09/2026
     * @Version 2.0.0
     */
    private static final class WideVectorCodec extends FilterCodec {

        private final KnnVectorsFormat vectors = new PerFieldKnnVectorsFormat() {

            @Override
            public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
                return WIDE_HNSW;
            }
        };

        private WideVectorCodec() {
            super(DEFAULT_CODEC, Codec.forName(DEFAULT_CODEC));
        }

        @Override
        public KnnVectorsFormat knnVectorsFormat() {
            return vectors;
        }
    }

    /**
     * Lucene's own hnsw format, answering one question differently.
     *
     * <p>
     * It delegates rather than extends because the format is final, and it keeps the format's own
     * name: the name is what an index records and what a reader looks up, so writing under it
     * means anything that can read a Lucene index can read this one. Only the ceiling changes,
     * and only while writing -- reading never asks.
     */
    private static final class WideHnswFormat extends KnnVectorsFormat {

        private final KnnVectorsFormat delegate = new Lucene99HnswVectorsFormat();

        private WideHnswFormat() {
            super(HNSW_FORMAT);
        }

        @Override
        public KnnVectorsWriter fieldsWriter(SegmentWriteState state) throws IOException {
            return delegate.fieldsWriter(state);
        }

        @Override
        public KnnVectorsReader fieldsReader(SegmentReadState state) throws IOException {
            return delegate.fieldsReader(state);
        }

        @Override
        public int getMaxDimensions(String fieldName) {
            return MAX_VECTOR_DIMENSIONS;
        }
    }

    /**
     * One directory: its writer, and the searchers reading what that writer has committed.
     */
    private static final class Open {

        private final Directory directory;
        private final IndexWriter writer;
        private final SearcherManager searchers;

        private Open(Directory directory, Analyzer analyzer) throws IOException {
            this.directory = directory;
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            // Lucene's own limit is 1024 floats a vector, which is a sensible default and not a
            // law: the format lets an index raise it, and every embedding model worth pointing at
            // this has outgrown it. qwen3-embedding produces 2560, so with the stock limit every
            // flush failed with "dimensions must be <= [1024]" and the crawl went on to report
            // itself finished with an empty vector store behind it. Raised here rather than
            // guarded against, because refusing the model is not the answer when holding it costs
            // nothing but the disk it is written to.
            config.setCodec(WIDE_VECTORS);
            // a crawl writes the same page again on an update, and by url-derived id: replacing
            // rather than appending is what keeps a re-crawl from doubling the index
            config.setCommitOnClose(true);
            this.writer = new IndexWriter(directory, config);
            // applyAllDeletes true: a version deleted a moment ago must not still be searchable,
            // and these indices are small enough that the cost of honouring that is nothing
            this.searchers = new SearcherManager(writer, true, true, null);
        }

        private void close() {
            closeQuietly(searchers);
            closeQuietly(writer);
            closeQuietly(directory);
        }

        private void closeQuietly(AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.warn("Could not close a lucene resource: {}", e.getMessage());
            }
        }

    }

}
