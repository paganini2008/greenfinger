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

package com.github.greenfinger.output.blob;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import com.github.greenfinger.core.output.BlobStore;
import com.github.greenfinger.output.OutputProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.DeleteRequest;
import io.minio.messages.DeleteResult;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;

/**
 * The MinIO file store. Object keys are exactly the paths the local store would use, so the two
 * layouts are identical and a crawl can be pointed at either without anything being rewritten.
 * 
 * @Description: MinioBlobStore
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class MinioBlobStore implements BlobStore {

    /** S3 accepts a thousand keys per delete request. */
    private static final int DELETE_BATCH = 1000;

    private final OutputProperties.File.Minio config;
    private MinioClient client;

    public MinioBlobStore(OutputProperties.File.Minio config) {
        this.config = config;
    }

    @Override
    public String getName() {
        return "minio";
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        client = MinioClient.builder().endpoint(config.getEndpoint())
                .credentials(config.getAccessKey(), config.getSecretKey()).build();
        boolean exists = client
                .bucketExists(BucketExistsArgs.builder().bucket(config.getBucket()).build());
        if (!exists) {
            if (!config.isCreateBucketIfMissing()) {
                throw new IllegalStateException("No such bucket: " + config.getBucket());
            }
            client.makeBucket(MakeBucketArgs.builder().bucket(config.getBucket()).build());
            log.info("Created MinIO bucket '{}'", config.getBucket());
        }
        log.info("File store: MinIO {} bucket '{}'", config.getEndpoint(), config.getBucket());
    }

    @Override
    public void write(String path, byte[] bytes, String contentType) throws Exception {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            client.putObject(PutObjectArgs.builder().bucket(config.getBucket()).object(path)
                    .stream(in, (long) bytes.length, -1L)
                    .contentType(StringUtils.isNotBlank(contentType) ? contentType
                            : "application/octet-stream")
                    .build());
        }
    }

    @Override
    public void writeText(String path, String text) throws Exception {
        write(path, text != null ? text.getBytes(StandardCharsets.UTF_8) : new byte[0],
                "text/plain; charset=utf-8");
    }

    @Override
    public Optional<String> readText(String path) throws Exception {
        return readBytes(path).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    @Override
    public Optional<byte[]> readBytes(String path) throws Exception {
        try (InputStream in = client.getObject(
                GetObjectArgs.builder().bucket(config.getBucket()).object(path).build())) {
            return Optional.of(in.readAllBytes());
        } catch (ErrorResponseException e) {
            if (isMissing(e)) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public boolean exists(String path) throws Exception {
        try {
            StatObjectResponse stat = client.statObject(
                    StatObjectArgs.builder().bucket(config.getBucket()).object(path).build());
            return stat != null;
        } catch (ErrorResponseException e) {
            if (isMissing(e)) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public long deletePrefix(String prefix) throws Exception {
        List<String> keys = listPrefix(prefix);
        long removed = 0L;
        LinkedList<DeleteRequest.Object> batch = new LinkedList<>();
        for (String key : keys) {
            batch.add(new DeleteRequest.Object(key));
            if (batch.size() >= DELETE_BATCH) {
                removed += removeBatch(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            removed += removeBatch(batch);
        }
        return removed;
    }

    private long removeBatch(List<DeleteRequest.Object> batch) throws Exception {
        Iterable<Result<DeleteResult.Error>> results = client.removeObjects(RemoveObjectsArgs.builder()
                .bucket(config.getBucket()).objects(new ArrayList<>(batch)).build());
        long failed = 0L;
        // the results iterable is lazy: nothing is actually deleted until it is walked
        for (Result<DeleteResult.Error> result : results) {
            DeleteResult.Error error = result.get();
            log.warn("Could not delete '{}': {}", error.objectName(), error.message());
            failed++;
        }
        return batch.size() - failed;
    }

    @Override
    public long sizeOfPrefix(String prefix) throws Exception {
        long total = 0L;
        for (Result<Item> result : list(prefix)) {
            total += result.get().size();
        }
        return total;
    }

    @Override
    public List<String> listPrefix(String prefix) throws Exception {
        List<String> keys = new ArrayList<>();
        for (Result<Item> result : list(prefix)) {
            keys.add(result.get().objectName());
        }
        return keys;
    }

    private Iterable<Result<Item>> list(String prefix) {
        return client.listObjects(ListObjectsArgs.builder().bucket(config.getBucket())
                .prefix(prefix).recursive(true).build());
    }

    private boolean isMissing(ErrorResponseException e) {
        String code = e.errorResponse() != null ? e.errorResponse().code() : null;
        return "NoSuchKey".equals(code) || "NoSuchObject".equals(code)
                || "ResourceNotFound".equals(code);
    }

}
