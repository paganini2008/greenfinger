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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.github.greenfinger.output.OutputProperties;

/**
 * The MinIO store against a real MinIO.
 *
 * <p>
 * Off unless asked for: a plain {@code mvn test} on a build agent has no object storage to talk to,
 * and a test that fails for the absence of a server says nothing about the code. Run it with:
 *
 * <pre>
 * mvn test -Dgreenfinger.minio=true \
 *          -Dgreenfinger.minio.endpoint=http://localhost:19000 \
 *          -Dgreenfinger.minio.access-key=... -Dgreenfinger.minio.secret-key=...
 * </pre>
 *
 * The credentials also come from {@code GF_MINIO_*}, which is what {@code .env} already sets, so on
 * a machine set up for a local crawl only the first flag is needed.
 *
 * <p>
 * What is asserted is deliberately the same list as {@link LocalBlobStoreTest}: the two stores are
 * interchangeable by design -- the same object keys, the same layout -- so the thing worth testing
 * is that they behave the same, particularly where object storage has no native equivalent (there
 * is no append, and no directory to delete).
 *
 * <p>
 * Every case works under a prefix of its own and removes it afterwards, so the test can run against
 * a bucket that has real crawls in it without touching them.
 *
 * @Description: MinioBlobStoreIT
 * @Author: Fred Feng
 * @Date: 01/09/2026
 * @Version 2.0.0
 */
@EnabledIfSystemProperty(named = "greenfinger.minio", matches = "true")
class MinioBlobStoreIT {

    private MinioBlobStore store;

    /** Unique per run: the bucket may be a shared one with real data in it. */
    private String prefix;

    @BeforeEach
    void setUp() throws Exception {
        prefix = "it-" + UUID.randomUUID();
        store = new MinioBlobStore(config());
        store.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (store != null) {
            store.deletePrefix(prefix);
        }
    }

    private static OutputProperties.File.Minio config() {
        OutputProperties.File.Minio config = new OutputProperties.File.Minio();
        config.setEndpoint(setting("endpoint", "GF_MINIO_ENDPOINT", "http://localhost:19000"));
        config.setAccessKey(setting("access-key", "GF_MINIO_ACCESS_KEY", "minioadmin"));
        config.setSecretKey(setting("secret-key", "GF_MINIO_SECRET_KEY", "minioadmin"));
        config.setBucket(setting("bucket", "GF_MINIO_BUCKET", "greenfinger"));
        config.setCreateBucketIfMissing(true);
        return config;
    }

    private static String setting(String property, String variable, String fallback) {
        String value = System.getProperty("greenfinger.minio." + property);
        if (StringUtils.isBlank(value)) {
            value = System.getenv(variable);
        }
        return StringUtils.isNotBlank(value) ? value : fallback;
    }

    private String path(String suffix) {
        return prefix + "/" + suffix;
    }

    @Test
    @DisplayName("the bucket is there, or is made, before anything is written")
    void connectsAndNamesItsTarget() {
        assertThat(store.getName()).isEqualTo("minio");
    }

    @Test
    void writesAndReadsBack() throws Exception {
        store.write(path("example/v0/pages/ab/cd/x.html"),
                "<html/>".getBytes(StandardCharsets.UTF_8), "text/html");

        assertThat(store.exists(path("example/v0/pages/ab/cd/x.html"))).isTrue();
        assertThat(store.readText(path("example/v0/pages/ab/cd/x.html"))).contains("<html/>");
        assertThat(store.readBytes(path("example/v0/pages/ab/cd/x.html"))).isPresent();
    }

    @Test
    @DisplayName("bytes come back exactly as written, which is what /v2/image hands to a browser")
    void keepsBytesIntact() throws Exception {
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0, (byte) 0x80, 0, 0};
        store.write(path("example/v0/images/ab/cd/x.gif"), gif, "image/gif");

        assertThat(store.readBytes(path("example/v0/images/ab/cd/x.gif"))).hasValue(gif);
    }

    @Test
    @DisplayName("a missing key is an answer, not an error: a deleted version leaves dangling paths")
    void missingPathsComeBackEmptyRatherThanThrowing() throws Exception {
        assertThat(store.readText(path("nothing/here.txt"))).isEmpty();
        assertThat(store.readBytes(path("nothing/here.txt"))).isEmpty();
        assertThat(store.exists(path("nothing/here.txt"))).isFalse();
    }

    @Test
    void writingTwiceReplacesRatherThanAppends() throws Exception {
        store.writeText(path("x.txt"), "first");
        store.writeText(path("x.txt"), "second");

        assertThat(store.readText(path("x.txt"))).contains("second");
    }

    @Test
    @DisplayName("deleting a version is deleting one prefix, and stops at that version")
    void deletesEverythingUnderAPrefix() throws Exception {
        store.writeText(path("example/v0/pages/a.html"), "a");
        store.writeText(path("example/v0/images/b.jpg"), "b");
        store.writeText(path("example/v1/pages/c.html"), "c");

        long removed = store.deletePrefix(path("example/v0"));

        assertThat(removed).isEqualTo(2);
        assertThat(store.exists(path("example/v0/pages/a.html"))).isFalse();
        assertThat(store.exists(path("example/v1/pages/c.html"))).isTrue();
    }

    @Test
    void deletingSomethingAbsentIsHarmless() throws Exception {
        assertThat(store.deletePrefix(path("never/existed"))).isZero();
    }

    @Test
    @DisplayName("the dry run reports how much a delete would free")
    void reportsTheSizeOfAPrefix() throws Exception {
        store.writeText(path("example/v0/a.txt"), "12345");
        store.writeText(path("example/v0/b.txt"), "123");

        assertThat(store.sizeOfPrefix(path("example/v0"))).isEqualTo(8L);
        assertThat(store.sizeOfPrefix(path("nothing"))).isZero();
    }

    @Test
    @DisplayName("listing walks into sub prefixes: object keys only look like directories")
    void listsWhatIsUnderAPrefix() throws Exception {
        store.writeText(path("example/v0/a.txt"), "a");
        store.writeText(path("example/v0/sub/b.txt"), "b");

        assertThat(store.listPrefix(path("example/v0"))).containsExactlyInAnyOrder(
                path("example/v0/a.txt"), path("example/v0/sub/b.txt"));
        assertThat(store.listPrefix(path("nothing"))).isEmpty();
    }

}
