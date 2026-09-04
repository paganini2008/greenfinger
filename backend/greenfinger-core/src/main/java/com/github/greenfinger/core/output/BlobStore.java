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

package com.github.greenfinger.core.output;

import java.util.List;
import com.github.greenfinger.core.ManagedBeanLifeCycle;

/**
 * Where a crawl's bytes live: the local file system, or MinIO. Exactly one is active.
 *
 * <p>
 * Paths are the ones {@link FileLayout} produces, relative to the store's root, and the two
 * implementations use them identically -- the string that is a path on disk is the object key in
 * MinIO. That is what lets the same crawl move between them without anything being rewritten, and
 * what makes deleting a version the removal of one prefix either way.
 * 
 * @Description: BlobStore
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public interface BlobStore extends ManagedBeanLifeCycle, ContentReader {

    String getName();

    /**
     * Writes bytes at a path, replacing whatever was there. Paths are derived from ids that are
     * derived from content, so rewriting the same path means rewriting identical bytes.
     */
    void write(String path, byte[] bytes, String contentType) throws Exception;

    void writeText(String path, String text) throws Exception;

    boolean exists(String path) throws Exception;

    /** Removes everything under a prefix. This is how one version is deleted. */
    long deletePrefix(String prefix) throws Exception;

    /** How many bytes sit under a prefix, for the delete command's dry run. */
    long sizeOfPrefix(String prefix) throws Exception;

    List<String> listPrefix(String prefix) throws Exception;

}
