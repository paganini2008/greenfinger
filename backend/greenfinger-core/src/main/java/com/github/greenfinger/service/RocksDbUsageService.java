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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.component.dedup.RocksDbStore;
import com.github.greenfinger.core.engine.CrawlRegistry;
import com.github.greenfinger.core.utils.BeanLifeCycleUtils;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * What the three RocksDB stores under a catalog are holding.
 *
 * <p>
 * The frontier says what is left to fetch; the two dedup filters say what has been seen. They are
 * the half of a crawl's state that no page shows and no log mentions, and they are also the half
 * that quietly keeps a disk full after a catalog has been forgotten about.
 *
 * <h2>Two numbers, measured two different ways</h2>
 * The size on disk is a file walk and always works. The key count means opening the database, and
 * RocksDB allows exactly one process to have it open -- so while a crawl of this catalog is
 * running, the count is not available and this says so rather than guessing or, worse, failing.
 * The size is still reported in that case, because it is the number somebody worried about disk
 * came for.
 *
 * <p>
 * The count is {@code rocksdb.estimate-num-keys}, and estimate is not a hedge: it is what the
 * property is called and what it is. It counts entries not yet compacted away, so a store that
 * has had a lot deleted reads high until compaction catches up.
 *
 * @Description: RocksDbUsageService
 * @Author: Fred Feng
 * @Date: 05/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class RocksDbUsageService {

    private final WebCrawlerProperties webCrawlerProperties;
    private final CrawlRegistry crawlRegistry;

    public RocksDbUsageService(WebCrawlerProperties webCrawlerProperties,
            CrawlRegistry crawlRegistry) {
        this.webCrawlerProperties = webCrawlerProperties;
        this.crawlRegistry = crawlRegistry;
    }

    /**
     * @param catalogId whose stores to measure.
     * @param version one version, or null for every version this catalog still has on disk.
     */
    public RocksDbUsage usage(String catalogId, Integer version) {
        boolean busy = crawlRegistry.isRunning(catalogId);
        List<StoreUsage> stores = new ArrayList<>();
        stores.add(measure("frontier", webCrawlerProperties.getFrontierDirectory(), catalogId,
                version, busy));
        stores.add(measure("dedup/url", webCrawlerProperties.getDedup().getUrl().getDirectory(),
                catalogId, version, busy));
        stores.add(measure("dedup/content",
                webCrawlerProperties.getDedup().getContent().getDirectory(), catalogId, version,
                busy));
        long bytes = stores.stream().mapToLong(StoreUsage::getBytes).sum();
        long keys = stores.stream().filter(s -> s.getKeyCount() >= 0)
                .mapToLong(StoreUsage::getKeyCount).sum();
        return RocksDbUsage.builder().catalogId(catalogId).crawlRunning(busy).bytes(bytes)
                .keyCount(keys).stores(stores).build();
    }

    private StoreUsage measure(String name, String baseDirectory, String catalogId,
            Integer version, boolean busy) {
        String scope =
                version == null ? catalogId : catalogId + File.separator + "v" + version;
        Path directory = Paths.get(baseDirectory, scope);
        long bytes = sizeOf(directory);
        long keys = busy ? -1L : countKeys(directory, version);
        return StoreUsage.builder().name(name).path(directory.toString())
                .exists(Files.isDirectory(directory)).bytes(bytes).keyCount(keys).build();
    }

    private long sizeOf(Path directory) {
        if (!Files.isDirectory(directory)) {
            return 0L;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile).mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (IOException e) {
                    return 0L;
                }
            }).sum();
        } catch (IOException e) {
            log.debug("Could not measure {}: {}", directory, e.getMessage());
            return 0L;
        }
    }

    /**
     * Opens each version's store just long enough to ask how many keys it holds.
     *
     * <p>
     * Read only would be better and RocksDB's java binding does offer it, but the store wrapper
     * here opens for writing and reusing it is worth more than the theoretical safety: nothing
     * else can have the lock, because a crawl holding it is the case that never reaches here.
     */
    private long countKeys(Path directory, Integer version) {
        if (!Files.isDirectory(directory)) {
            return 0L;
        }
        List<Path> stores = version != null ? List.of(directory) : versionDirectories(directory);
        long total = 0L;
        for (Path store : stores) {
            RocksDbStore rocksDb = null;
            try {
                rocksDb = new RocksDbStore(store.toString());
                BeanLifeCycleUtils.afterPropertiesSet(rocksDb);
                long keys = rocksDb.estimatedKeyCount();
                total += Math.max(0L, keys);
            } catch (Exception e) {
                // a store another process has open, or one half written by a crash: neither is
                // worth failing the whole report over
                log.debug("Could not read {}: {}", store, e.getMessage());
            } finally {
                BeanLifeCycleUtils.destroyQuietly(rocksDb);
            }
        }
        return total;
    }

    /** {@code v0}, {@code v1}, ... under a catalog's directory. */
    private List<Path> versionDirectories(Path catalogDirectory) {
        try (Stream<Path> children = Files.list(catalogDirectory)) {
            return children.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("v"))
                    .sorted(Comparator.comparing(Path::toString)).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * 
     * @Description: RocksDbUsage
     * @Author: Fred Feng
     * @Date: 05/09/2026
     * @Version 2.0.0
     */
    @Getter
    @Builder
    public static class RocksDbUsage {

        private final String catalogId;

        /** True when the key counts could not be read because the crawl has the stores open. */
        private final boolean crawlRunning;
        private final long bytes;
        private final long keyCount;
        private final List<StoreUsage> stores;
    }

    /**
     * 
     * @Description: StoreUsage
     * @Author: Fred Feng
     * @Date: 05/09/2026
     * @Version 2.0.0
     */
    @Getter
    @Builder
    public static class StoreUsage {

        private final String name;
        private final String path;
        private final boolean exists;
        private final long bytes;

        /** -1 when it could not be read, which is not the same as a store holding nothing. */
        private final long keyCount;
    }

}
