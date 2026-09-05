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

package com.github.greenfinger.output.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.github.greenfinger.core.WebCrawlerException;

/**
 * 
 * @Description: ModelStoreTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
class ModelStoreTest {

    @Test
    @DisplayName("a cached file is used, never fetched again")
    void returnsWhatIsAlreadyCached(@TempDir Path root) throws Exception {
        Path cached = root.resolve("some_repository").resolve("onnx/model.onnx");
        Files.createDirectories(cached.getParent());
        Files.writeString(cached, "weights");

        // offline, so any attempt to download would fail loudly rather than quietly succeed
        ModelStore store = new ModelStore(root.toString(), true);

        assertThat(store.resolve("some/repository", "onnx/model.onnx")).isEqualTo(cached);
    }

    @Test
    @DisplayName("offline mode refuses to download and says how to fix it")
    void offlineRefusesToFetch(@TempDir Path root) {
        ModelStore store = new ModelStore(root.toString(), true);

        assertThatThrownBy(() -> store.resolve("some/repository", "onnx/model.onnx"))
                .isInstanceOf(WebCrawlerException.class).hasMessageContaining("offline")
                .hasMessageContaining("models pull");
    }

    @Test
    @DisplayName("an empty file is not a cached one; a truncated download must not look complete")
    void treatsAnEmptyFileAsMissing(@TempDir Path root) throws Exception {
        Path cached = root.resolve("some_repository").resolve("model.onnx");
        Files.createDirectories(cached.getParent());
        Files.createFile(cached);

        ModelStore store = new ModelStore(root.toString(), true);

        assertThatThrownBy(() -> store.resolve("some/repository", "model.onnx"))
                .isInstanceOf(WebCrawlerException.class);
    }

    @Test
    void expandsTheHomeDirectoryShorthand() {
        ModelStore store = new ModelStore("~/.greenfinger/models", true);
        assertThat(store.getRoot().toString())
                .startsWith(System.getProperty("user.home"))
                .doesNotContain("~");
    }

    @Test
    @DisplayName("the repository name becomes one directory, not a nested pair")
    void flattensTheRepositoryName(@TempDir Path root) throws Exception {
        Path cached = root.resolve("Xenova_multilingual-e5-small").resolve("tokenizer.json");
        Files.createDirectories(cached.getParent());
        Files.writeString(cached, "{}");

        ModelStore store = new ModelStore(root.toString(), true);

        assertThat(store.resolve("Xenova/multilingual-e5-small", "tokenizer.json"))
                .isEqualTo(cached);
    }


    @Test
    @DisplayName("a file can be reported on without being fetched, which is what a pull needs")
    void locatesAndReportsWithoutFetching(@TempDir Path root) throws Exception {
        ModelStore store = new ModelStore(root.toString(), true);
        ModelFile model = new ModelFile("Xenova/multilingual-e5-small", "onnx/model_quantized.onnx",
                ModelFile.TEXT);

        assertThat(store.isCached(model)).isFalse();
        assertThat(store.locate(model))
                .isEqualTo(root.resolve("Xenova_multilingual-e5-small/onnx/model_quantized.onnx"));

        Files.createDirectories(store.locate(model).getParent());
        Files.writeString(store.locate(model), "weights");

        assertThat(store.isCached(model)).isTrue();
    }

    @Test
    @DisplayName("an interrupted download leaves a zero length file, which is not a cached file")
    void anEmptyFileIsNotCached(@TempDir Path root) throws Exception {
        ModelStore store = new ModelStore(root.toString(), true);
        ModelFile model = new ModelFile("some/repo", "model.onnx", ModelFile.IMAGE);
        Files.createDirectories(store.locate(model).getParent());
        Files.writeString(store.locate(model), "");

        assertThat(store.isCached(model)).isFalse();
    }

    @Test
    void namesItselfForATable() {
        ModelFile model = new ModelFile("some/repo", "onnx/model.onnx", ModelFile.TEXT);

        assertThat(model.describe()).isEqualTo("some/repo/onnx/model.onnx");
        assertThat(model.isText()).isTrue();
        assertThat(model.isImage()).isFalse();
    }

}
