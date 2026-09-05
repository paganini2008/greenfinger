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

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import com.github.greenfinger.core.WebCrawlerException;
import lombok.extern.slf4j.Slf4j;

/**
 * Fetches model weights once and keeps them.
 *
 * <p>
 * "No configuration" cannot mean "no download": weights are hundreds of megabytes and do not belong
 * in a jar. What it does mean is that nobody has to open an account, install a service or read a
 * model card -- and that nothing is fetched at all until a crawl actually asks for vector output,
 * which the default configuration does not.
 *
 * <p>
 * Downloads land on a temporary name and are moved into place only once complete, so an interrupted
 * download cannot leave a half file that looks cached.
 * 
 * @Description: ModelStore
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class ModelStore {

    private static final String HUGGING_FACE = "https://huggingface.co";

    private final Path root;
    private final boolean offline;
    private final HttpClient httpClient;

    public ModelStore(String modelDirectory, boolean offline) {
        this.root = Path.of(modelDirectory.replaceFirst("^~", System.getProperty("user.home")));
        this.offline = offline;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public Path getRoot() {
        return root;
    }

    /**
     * @param repository a Hugging Face repository, such as {@code Xenova/multilingual-e5-small}
     * @param file the path within it
     * @return where the file now sits on disk
     */
    public Path resolve(String repository, String file) {
        Path target = locate(repository, file);
        if (isCached(target)) {
            return target;
        }
        if (offline) {
            throw new WebCrawlerException("Model file is not cached and offline mode is on: "
                    + target + ". Fetch it with 'models pull', or turn off"
                    + " greenfinger.embedding.offline.");
        }
        download(HUGGING_FACE + "/" + repository + "/resolve/main/" + file, target);
        return target;
    }

    /**
     * The same fetch, named by a {@link ModelFile} so the caller does not have to repeat the
     * repository and the path.
     */
    public Path resolve(ModelFile model) {
        return resolve(model.repository(), model.file());
    }

    /**
     * Where a file would sit, fetched or not. {@code models pull} needs to report on a file it has
     * not downloaded, and reporting requires naming the place.
     */
    public Path locate(ModelFile model) {
        return locate(model.repository(), model.file());
    }

    private Path locate(String repository, String file) {
        return root.resolve(repository.replace('/', '_')).resolve(file);
    }

    /** Already on disk, so a pull can leave it alone rather than fetch it twice. */
    public boolean isCached(ModelFile model) {
        return isCached(locate(model));
    }

    /**
     * A zero length file is not a cached file: an interrupted download leaves one behind, and
     * treating it as present would fail later inside the model loader instead of here.
     */
    private boolean isCached(Path target) {
        return Files.exists(target) && sizeOf(target) > 0;
    }

    private void download(String url, Path target) {
        log.info("Fetching {} ...", url);
        Path partial = target.resolveSibling(target.getFileName() + ".partial");
        try {
            Files.createDirectories(target.getParent());
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(30)).GET().build();
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new WebCrawlerException(
                        "Could not fetch " + url + ": HTTP " + response.statusCode());
            }
            try (InputStream in = response.body()) {
                Files.copy(in, partial, StandardCopyOption.REPLACE_EXISTING);
            }
            // only now does it look cached
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Cached {} ({} MB)", target.getFileName(), sizeOf(target) / (1024 * 1024));
        } catch (WebCrawlerException e) {
            throw e;
        } catch (Exception e) {
            throw new WebCrawlerException("Could not fetch " + url, e);
        } finally {
            try {
                Files.deleteIfExists(partial);
            } catch (Exception ignored) {
                // a leftover partial is harmless; the next attempt overwrites it
            }
        }
    }

    private long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (Exception e) {
            return 0L;
        }
    }

}
