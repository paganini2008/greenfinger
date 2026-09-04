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

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 
 * @Description: LocalBlobStoreTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
class LocalBlobStoreTest {

    private LocalBlobStore store(Path root) throws Exception {
        LocalBlobStore store = new LocalBlobStore(root);
        store.afterPropertiesSet();
        return store;
    }

    @Test
    void writesAndReadsBack(@TempDir Path root) throws Exception {
        LocalBlobStore store = store(root);
        store.write("example/v0/pages/ab/cd/x.html", "<html/>".getBytes(StandardCharsets.UTF_8),
                "text/html");

        assertThat(store.exists("example/v0/pages/ab/cd/x.html")).isTrue();
        assertThat(store.readText("example/v0/pages/ab/cd/x.html")).contains("<html/>");
        assertThat(store.readBytes("example/v0/pages/ab/cd/x.html")).isPresent();
    }

    @Test
    void createsTheDirectoriesOnTheWay(@TempDir Path root) throws Exception {
        store(root).writeText("a/b/c/d/e.txt", "deep");
        assertThat(root.resolve("a/b/c/d/e.txt")).exists();
    }

    @Test
    void missingPathsComeBackEmptyRatherThanThrowing(@TempDir Path root) throws Exception {
        LocalBlobStore store = store(root);
        assertThat(store.readText("nothing/here.txt")).isEmpty();
        assertThat(store.readBytes("nothing/here.txt")).isEmpty();
        assertThat(store.exists("nothing/here.txt")).isFalse();
    }

    @Test
    void writingTwiceReplacesRatherThanAppends(@TempDir Path root) throws Exception {
        LocalBlobStore store = store(root);
        store.writeText("x.txt", "first");
        store.writeText("x.txt", "second");
        assertThat(store.readText("x.txt")).contains("second");
    }

    @Test
    @DisplayName("deleting a version is deleting one prefix")
    void deletesEverythingUnderAPrefix(@TempDir Path root) throws Exception {
        LocalBlobStore store = store(root);
        store.writeText("example/v0/pages/a.html", "a");
        store.writeText("example/v0/images/b.jpg", "b");
        store.writeText("example/v1/pages/c.html", "c");

        long removed = store.deletePrefix("example/v0");

        assertThat(removed).isEqualTo(2);
        assertThat(store.exists("example/v0/pages/a.html")).isFalse();
        assertThat(store.exists("example/v1/pages/c.html")).isTrue();
    }

    @Test
    void deletingSomethingAbsentIsHarmless(@TempDir Path root) throws Exception {
        assertThat(store(root).deletePrefix("never/existed")).isZero();
    }

    @Test
    void reportsTheSizeOfAPrefixForTheDryRun(@TempDir Path root) throws Exception {
        LocalBlobStore store = store(root);
        store.writeText("example/v0/a.txt", "12345");
        store.writeText("example/v0/b.txt", "123");

        assertThat(store.sizeOfPrefix("example/v0")).isEqualTo(8L);
        assertThat(store.sizeOfPrefix("nothing")).isZero();
    }

    @Test
    void listsWhatIsUnderAPrefix(@TempDir Path root) throws Exception {
        LocalBlobStore store = store(root);
        store.writeText("example/v0/a.txt", "a");
        store.writeText("example/v0/sub/b.txt", "b");

        assertThat(store.listPrefix("example/v0")).containsExactlyInAnyOrder("example/v0/a.txt",
                "example/v0/sub/b.txt");
        assertThat(store.listPrefix("nothing")).isEmpty();
    }

    @Test
    void isNamedForItsTarget(@TempDir Path root) throws Exception {
        assertThat(store(root).getName()).isEqualTo("local");
    }

}
