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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import com.github.greenfinger.core.output.BlobStore;

/**
 * The zero-install file store: plain files under one root directory.
 * 
 * @Description: LocalBlobStore
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public class LocalBlobStore implements BlobStore {

    private final Path root;

    public LocalBlobStore(Path root) {
        this.root = root;
    }

    @Override
    public String getName() {
        return "local";
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Files.createDirectories(root);
    }

    @Override
    public void write(String path, byte[] bytes, String contentType) throws Exception {
        Path target = resolve(path);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Override
    public void writeText(String path, String text) throws Exception {
        write(path, text != null ? text.getBytes(StandardCharsets.UTF_8) : new byte[0],
                "text/plain");
    }

    @Override
    public Optional<String> readText(String path) throws Exception {
        Path target = resolve(path);
        return Files.exists(target)
                ? Optional.of(Files.readString(target, StandardCharsets.UTF_8))
                : Optional.empty();
    }

    @Override
    public Optional<byte[]> readBytes(String path) throws Exception {
        Path target = resolve(path);
        return Files.exists(target) ? Optional.of(Files.readAllBytes(target)) : Optional.empty();
    }

    @Override
    public boolean exists(String path) {
        return Files.exists(resolve(path));
    }

    @Override
    public long deletePrefix(String prefix) throws Exception {
        Path target = resolve(prefix);
        if (!Files.exists(target)) {
            return 0L;
        }
        if (Files.isRegularFile(target)) {
            Files.delete(target);
            return 1L;
        }
        long[] removed = {0L};
        try (Stream<Path> walk = Files.walk(target)) {
            // deepest first, so a directory is only removed once it is empty
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                if (Files.isRegularFile(path)) {
                    removed[0]++;
                }
                Files.deleteIfExists(path);
            }
        }
        return removed[0];
    }

    @Override
    public long sizeOfPrefix(String prefix) throws Exception {
        Path target = resolve(prefix);
        if (!Files.exists(target)) {
            return 0L;
        }
        try (Stream<Path> walk = Files.walk(target)) {
            return walk.filter(Files::isRegularFile).mapToLong(this::sizeOf).sum();
        }
    }

    @Override
    public List<String> listPrefix(String prefix) throws Exception {
        Path target = resolve(prefix);
        if (!Files.exists(target)) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(target)) {
            walk.filter(Files::isRegularFile)
                    .forEach(p -> paths.add(root.relativize(p).toString().replace('\\', '/')));
        }
        return paths;
    }

    private long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private Path resolve(String path) {
        return root.resolve(path).normalize();
    }

}
