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

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import com.chaconneai.spreader.GossipCluster;
import com.github.greenfinger.core.output.BlobStore;
import com.github.greenfinger.output.OutputFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Says at startup what this node actually is, because three of the things that decide how a
 * cluster behaves fail quietly.
 *
 * <ul>
 * <li><b>The transport.</b> Netty is optional inside spreader. Configure it without the jar and
 * the transport silently falls back to the built-in NIO -- correct, slower, and invisible.</li>
 * <li><b>The listener queues.</b> JCTools is optional in the same way, with the same silent
 * fallback to the built-in ring buffer.</li>
 * <li><b>Where the data lives.</b> A file-backed database or a local blob directory means every
 * node holds its own copy, and every write has to be sent to the others. Getting that wrong is
 * not an error at any point: it is a search that answers differently depending on which node was
 * asked.</li>
 * </ul>
 * 
 * @Description: ClusterStartupReport
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class ClusterStartupReport implements SmartInitializingSingleton {

    private final GossipCluster cluster;
    private final OutputFactory outputFactory;
    private final Environment environment;

    @Override
    public void afterSingletonsInstantiated() {
        StoreType database = StoreType.ofJdbcUrl(environment.getProperty("spring.datasource.url"));
        StoreType blobs = blobStoreType();

        log.info("Cluster node {}: database {}, blobs {}", cluster.self().label(), database,
                blobs);

        if (!jcToolsPresent()) {
            log.warn("JCTools is not on the classpath, so the inbound queues fall back to the"
                    + " built-in ring buffer. Add org.jctools:jctools-core.");
        }
        if (blobs.replicated()) {
            log.warn("Pages and images are being written to a local directory, so each node holds"
                    + " only what it fetched and every file has to be copied to the others."
                    + " Point greenfinger.output.file.target at MinIO for a shared store; the"
                    + " layout is identical and nothing has to be rewritten.");
        }
        if (database.replicated()) {
            log.info("The database is a file per node ({}), so rows are replicated as they are"
                    + " written. MySQL, PostgreSQL, SQL Server and Oracle are shared and need"
                    + " none of that.", database.name());
        }
    }

    private StoreType blobStoreType() {
        try {
            // named, not opened: this only asks which kind was configured
            BlobStore blobStore = outputFactory.getBlobStore();
            return StoreType.ofBlobStore(blobStore.getName());
        } catch (RuntimeException e) {
            log.debug("Could not determine the blob store type: {}", e.getMessage());
            return StoreType.LOCAL_FILE;
        }
    }

    private boolean jcToolsPresent() {
        try {
            Class.forName("org.jctools.queues.MpscArrayQueue", false,
                    getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

}
