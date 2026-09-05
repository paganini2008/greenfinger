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

package com.github.greenfinger.core.component.dedup;

import java.io.File;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import com.github.greenfinger.core.ManagedBeanLifeCycle;
import com.github.greenfinger.core.WebCrawlerException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin wrapper over an embedded RocksDB instance, shared by both dedup filters.
 *
 * <p>
 * Two things the 1.x wrapper got wrong are fixed here. It deleted the entire database directory
 * from {@code destroy()}, so a crawl could never be resumed after a restart -- closing and deleting
 * are now separate operations, and only {@link #clean()} removes files. And it swallowed every
 * RocksDB exception and returned "already seen", which meant a single storage fault silently
 * discarded the rest of the site; failures now propagate.
 * 
 * @Description: RocksDbStore
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class RocksDbStore implements ManagedBeanLifeCycle {

    static {
        RocksDB.loadLibrary();
    }

    @Getter
    private final String directory;
    private Options options;
    private RocksDB db;

    public RocksDbStore(String directory) {
        this.directory = directory;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        File dir = new File(directory);
        if (!dir.exists()) {
            FileUtils.forceMkdir(dir);
        }
        options = new Options();
        options.setCreateIfMissing(true);
        try {
            db = RocksDB.open(options, dir.getAbsolutePath());
        } catch (RocksDBException e) {
            // RocksDB takes an exclusive lock on its directory, so this is what a second process
            // crawling out of one data store looks like. The message it raises names a LOCK file
            // and says "resource temporarily unavailable", which is true and tells nobody what to
            // do about it: read together, write alone, and a process that crawls is a node.
            if (String.valueOf(e.getMessage()).contains("lock")) {
                throw new WebCrawlerException("Another greenfinger process is already using "
                        + dir.getAbsolutePath() + ". One process at a time may crawl out of a data"
                        + " store -- several may read it, but only one may write. Give this one a"
                        + " data store of its own: GF_DATA_STORE in run.conf, or start it as a"
                        + " second node with run-local.sh.", e);
            }
            throw e;
        }
        if (log.isInfoEnabled()) {
            log.info("Opened RocksDB at {}, approximate keys: {}", dir.getAbsolutePath(),
                    estimatedKeyCount());
        }
    }

    public byte[] get(byte[] key) throws RocksDBException {
        return db.get(key);
    }

    public byte[] get(String key) throws RocksDBException {
        return get(key.getBytes(StandardCharsets.UTF_8));
    }

    public void put(byte[] key, byte[] value) throws RocksDBException {
        db.put(key, value);
    }

    public void put(String key, byte[] value) throws RocksDBException {
        put(key.getBytes(StandardCharsets.UTF_8), value);
    }

    public void delete(byte[] key) throws RocksDBException {
        db.delete(key);
    }

    /**
     * Records the key if absent, and reports whether it was already there.
     */
    public synchronized boolean putIfAbsent(String key, byte[] value) throws RocksDBException {
        byte[] existing = get(key);
        if (existing != null && existing.length > 0) {
            return true;
        }
        put(key, value);
        return false;
    }

    public RocksIterator newIterator() {
        return db.newIterator();
    }

    public long estimatedKeyCount() {
        try {
            String value = db.getProperty("rocksdb.estimate-num-keys");
            return value != null ? Long.parseLong(value) : -1L;
        } catch (Exception e) {
            return -1L;
        }
    }

    /**
     * Closes the handle. The files stay on disk so a crawl can be resumed.
     */
    @Override
    public void destroy() throws Exception {
        close();
    }

    public void close() {
        if (db != null) {
            db.close();
            db = null;
        }
        if (options != null) {
            options.close();
            options = null;
        }
    }

    /**
     * Closes the handle and removes the files. Only ever called deliberately -- a clean, a rebuild,
     * or an explicit CLI command.
     */
    public void clean() throws Exception {
        close();
        File dir = new File(directory);
        if (dir.exists()) {
            FileUtils.deleteDirectory(dir);
            if (log.isWarnEnabled()) {
                log.warn("Deleted RocksDB directory: {}", dir.getAbsolutePath());
            }
        }
    }

}
