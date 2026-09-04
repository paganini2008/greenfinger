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

import org.apache.commons.lang3.StringUtils;

/**
 * A place data is kept, and the one question the cluster needs answered about it: does a write on
 * this node have to be copied to the others?
 *
 * <p>
 * The answer is not about the technology but about who holds the bytes. A file-backed store gives
 * every process its own copy -- SQLite, H2 in file or memory mode, RocksDB, a local blob
 * directory -- so a row written here does not exist there, and the write has to be sent. A server
 * every node dials -- MySQL, PostgreSQL, SQL Server, Oracle, an H2 in server mode, MinIO,
 * Elasticsearch, Qdrant -- already holds one copy for all of them, and copying would mean writing
 * the same row twice.
 *
 * <p>
 * Note that "file-backed" is a property of the url, not of the product: {@code jdbc:h2:./data/db}
 * has to be replicated and {@code jdbc:h2:tcp://host/db} must not be, and the two differ by four
 * characters. That is why this is derived from the url rather than from a configured name.
 * 
 * @Description: StoreType
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
public record StoreType(String name, boolean replicated) {

    // ---- the record database ----------------------------------------------------------------

    public static final StoreType SQLITE = new StoreType("SQLITE", true);

    public static final StoreType H2 = new StoreType("H2", true);

    /** H2 reached over tcp: one server, every node dials it, nothing to copy. */
    public static final StoreType H2_SERVER = new StoreType("H2_SERVER", false);

    public static final StoreType MYSQL = new StoreType("MYSQL", false);

    public static final StoreType POSTGRESQL = new StoreType("POSTGRESQL", false);

    public static final StoreType SQLSERVER = new StoreType("SQLSERVER", false);

    public static final StoreType ORACLE = new StoreType("ORACLE", false);

    /**
     * Anything unrecognised. Not replicated, because copying rows into a database that already
     * has them is the damaging half of the guess -- and an unknown driver is far more likely to be
     * another shared server than another embedded file.
     */
    public static final StoreType OTHER = new StoreType("OTHER", false);

    // ---- where the bytes go -----------------------------------------------------------------

    /** A directory on this node's disk. */
    public static final StoreType LOCAL_FILE = new StoreType("LOCAL_FILE", true);

    public static final StoreType MINIO = new StoreType("MINIO", false);

    /** The frontier and the two dedup filters. Always a local file, so always replicated. */
    public static final StoreType ROCKSDB = new StoreType("ROCKSDB", true);

    /**
     * Reads the type off a jdbc url, which is the only place the distinction is actually recorded.
     */
    public static StoreType ofJdbcUrl(String jdbcUrl) {
        String url = StringUtils.lowerCase(StringUtils.trimToEmpty(jdbcUrl));
        if (url.startsWith("jdbc:sqlite:")) {
            return SQLITE;
        }
        if (url.startsWith("jdbc:h2:")) {
            // tcp: and ssl: are a server somebody else is running; mem:, file: and a bare path
            // are this process's own
            return url.contains(":tcp:") || url.contains(":ssl:") ? H2_SERVER : H2;
        }
        if (url.startsWith("jdbc:mysql:") || url.startsWith("jdbc:mariadb:")) {
            return MYSQL;
        }
        if (url.startsWith("jdbc:postgresql:")) {
            return POSTGRESQL;
        }
        if (url.startsWith("jdbc:sqlserver:")) {
            return SQLSERVER;
        }
        // thin and oci differ in how they reach the server, not in who owns the data
        if (url.startsWith("jdbc:oracle:")) {
            return ORACLE;
        }
        return OTHER;
    }

    /** Matches {@code BlobStore.getName()}, which is "local" or "minio". */
    public static StoreType ofBlobStore(String blobStoreName) {
        return "minio".equalsIgnoreCase(StringUtils.trimToEmpty(blobStoreName)) ? MINIO
                : LOCAL_FILE;
    }

    /**
     * True when this store is shared by every node, so a write reaches all of them at once.
     * Exactly the opposite of {@link #replicated()}, spelled out because reading
     * {@code !replicated} at a call site says nothing about why.
     */
    public boolean shared() {
        return !replicated;
    }

    @Override
    public String toString() {
        return name + (replicated ? " (per node, replicated)" : " (shared)");
    }

}
