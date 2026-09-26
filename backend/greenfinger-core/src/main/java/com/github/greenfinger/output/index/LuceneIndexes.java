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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import com.github.greenfinger.core.WebCrawlerException;
import lombok.extern.slf4j.Slf4j;

/**
 * The open Lucene indices of this process: one writer and one searcher per directory, shared.
 *
 * <p>
 * A writer holds a file system lock on its directory, so a second writer is not slow but a
 * {@code LockObtainFailedException} -- and two of these on one root deadlock each other as two
 * processes would. Every path in (crawl, replay, search, delete) goes through this one object,
 * obtained from {@link #shared}, whose static cache is the shape of the resource.
 *
 * <p>
 * Searchers come from a {@link SearcherManager}, which lets a search see a crawl that is still
 * running. Borrowed and given back, never closed: the readers underneath are shared.
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
     * Closes one shared root and forgets it. One rather than all: two applications in one jvm --
     * which is what a test run is -- must not close each other's writers.
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
     *
     * @throws WebCrawlerException when this process could only open the index to read, meaning
     *         another one is writing it -- said here, because a null would surface three frames
     *         away from the reason.
     */
    public IndexWriter writer(String name) {
        Open index = opened(name);
        if (index.writer == null) {
            throw new WebCrawlerException("The index at " + root.resolve(name)
                    + " is being written by another process, so this one can only read it."
                    + " Stop the node that holds it, or run this against its api instead.");
        }
        return index.writer;
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
        if (index.searchers == null) {
            // the directory is there and empty: a catalog whose index has been created but never
            // written, which is not an error and has no documents to return
            return null;
        }
        index.searchers.maybeRefresh();
        return index.searchers.acquire();
    }

    public void release(String name, IndexSearcher searcher) {
        if (searcher == null) {
            return;
        }
        Open index = open.get(name);
        if (index == null || index.searchers == null) {
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
        if (index != null && index.writer != null) {
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

    /**
     * The index, to write if this process can and to read if it cannot.
     *
     * <p>
     * An {@code IndexWriter} takes the directory's lock whether or not anything is written, so a
     * read-only command could not open an index belonging to a running node -- the same courtesy
     * H2's AUTO_SERVER has given the database since 1.x. Reading a directory somebody else is
     * writing is safe: a segment is never mutated, so a reader sees the last commit.
     */
    private Open opened(String name) {
        return open.computeIfAbsent(name, key -> {
            Path directory = root.resolve(key);
            try {
                Files.createDirectories(directory);
                Directory dir = FSDirectory.open(directory);
                try {
                    return new Open(dir, analyzer);
                } catch (LockObtainFailedException locked) {
                    log.info("The index at {} is being written elsewhere; opening it to read.",
                            directory);
                    return Open.toRead(dir);
                }
            } catch (IOException e) {
                throw new WebCrawlerException("Could not open the index at " + directory, e);
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
     * The default codec, with room for the vectors people actually have. A {@link FilterCodec},
     * so everything stays Lucene's except the one number that is a policy rather than a format.
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
     * Lucene's own hnsw format with a higher ceiling. Delegates because the format is final, and
     * keeps its name -- that is what a reader looks up, so any Lucene tool can read this index.
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
            // Lucene's 1024 floats a vector is a default, not a law, and every model worth
            // using has outgrown it -- qwen3-embedding is 2560, which failed every flush while
            // the crawl reported itself finished with an empty vector store.
            config.setCodec(WIDE_VECTORS);
            // a crawl writes the same page again on an update, and by url-derived id: replacing
            // rather than appending is what keeps a re-crawl from doubling the index
            config.setCommitOnClose(true);
            this.writer = new IndexWriter(directory, config);
            // applyAllDeletes true: a version deleted a moment ago must not still be searchable,
            // and these indices are small enough that the cost of honouring that is nothing
            this.searchers = new SearcherManager(writer, true, true, null);
        }

        /**
         * The same index with no writer: a reader on the last commit, nothing locked. An empty
         * directory has no commit, which is not an error -- it comes back with no searcher.
         */
        private static Open toRead(Directory directory) throws IOException {
            try {
                return new Open(directory, DirectoryReader.open(directory));
            } catch (IndexNotFoundException empty) {
                return new Open(directory, (DirectoryReader) null);
            }
        }

        private Open(Directory directory, DirectoryReader reader) throws IOException {
            this.directory = directory;
            this.writer = null;
            this.searchers = reader != null ? new SearcherManager(reader, null) : null;
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
