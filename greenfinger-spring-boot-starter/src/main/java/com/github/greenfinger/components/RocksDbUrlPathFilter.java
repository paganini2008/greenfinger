package com.github.greenfinger.components;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import com.github.doodler.common.context.ManagedBeanLifeCycle;
import com.github.doodler.common.utils.JavaFileUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: RocksdbUrlPathFilter
 * @Author: Fred Feng
 * @Date: 16/01/2025
 * @Version 1.0.0
 */
@Slf4j
public class RocksDbUrlPathFilter implements ExistingUrlPathFilter, ManagedBeanLifeCycle {

    static {
        RocksDB.loadLibrary();
    }

    private static final String DEFAULT_DBFILE_NAME = ".rocksdb.dbf";
    private static final Charset defaultCharset = StandardCharsets.UTF_8;
    private static final byte[] defaultValue = "-".getBytes(defaultCharset);
    private String dbfile;

    public RocksDbUrlPathFilter() {}

    public RocksDbUrlPathFilter(long catalogId, int version) {
        this.dbfile =
                JavaFileUtils
                        .getFile(JavaFileUtils.getUserDir(), DEFAULT_DBFILE_NAME,
                                String.valueOf(catalogId), String.valueOf(version))
                        .getAbsolutePath();
    }

    public RocksDbUrlPathFilter(String dataFilePath) {
        this.dbfile = dataFilePath;
    }

    private RocksDB db;

    @Override
    public void afterPropertiesSet() throws Exception {
        String path = dbfile;
        if (StringUtils.isBlank(path)) {
            path = JavaFileUtils
                    .getFile(JavaFileUtils.getUserDir(), DEFAULT_DBFILE_NAME,
                            LocalDate.now().format(DateTimeFormatter.ofPattern("d.m.yyyy")))
                    .getAbsolutePath();
            this.dbfile = path;
        }
        if (!new File(path).exists()) {
            FileUtils.forceMkdir(new File(path));
        }
        log.info("RocksDB's data file path: {}, size: {}", path,
                JavaFileUtils.byteCountToDisplaySize(new File(path)));
        Options options = new Options();
        options.setCreateIfMissing(true);
        db = RocksDB.open(options, path);
        log.info("Initialized RocksDB: {}", db);
    }

    @Override
    public boolean mightExist(String path) {
        boolean flag = true;
        try {
            byte[] value = db.get(path.getBytes(defaultCharset));
            if (value == null || value.length == 0) {
                flag = false;
                db.put(path.getBytes(defaultCharset), defaultValue);
            }
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error(e.getMessage(), e);
            }
        }
        return flag;
    }

    @Override
    public int export(UrlPathFilterExporter exporter, boolean deleted) throws Exception {
        RocksIterator iterator = db.newIterator();
        iterator.seekToFirst();
        int n = 0;
        byte[] bytes;
        String key;
        while (iterator.isValid()) {
            bytes = iterator.key();
            key = new String(bytes, defaultCharset);
            if (exporter != null) {
                if (exporter.doExport(++n, key)) {
                    if (deleted) {
                        db.delete(bytes);
                    }
                } else {
                    break;
                }
            }
            iterator.next();
        }
        return n;
    }

    @Override
    public void destroy() throws Exception {
        clean();
    }

    @Override
    public void clean() throws IOException {
        try {
            db.close();
        } finally {
            try {
                File dbDir = new File(dbfile);
                if (dbDir.exists()) {
                    FileUtils.deleteDirectory(dbDir);
                    log.warn("Successfully delete rocksdb's dbfile: {}", dbfile);
                }
            } catch (Exception e) {
                if (log.isErrorEnabled()) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public long size() throws RocksDBException {
        String estimateNumKeys = db.getProperty("rocksdb.estimate-num-keys");
        try {
            return Long.parseLong(estimateNumKeys);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    public static void main(String[] args) throws Exception {
        RocksDbUrlPathFilter dbUrlPathFilter = new RocksDbUrlPathFilter(1001, 1);
        dbUrlPathFilter.afterPropertiesSet();
        boolean b;
        for (int i = 0; i < 10000; i++) {
            b = dbUrlPathFilter.mightExist("" + RandomUtils.nextInt(100, 20000));
            System.out.println("Exists: " + b);
        }
        System.in.read();
        System.out.println("Size: " + dbUrlPathFilter.size());

        dbUrlPathFilter.export((n, i) -> {
            System.out.println(n + "\t" + i);
            return true;
        }, false);

        dbUrlPathFilter.clean();
        System.out.println("RocksDbUrlPathFilter.main()");
    }


}
