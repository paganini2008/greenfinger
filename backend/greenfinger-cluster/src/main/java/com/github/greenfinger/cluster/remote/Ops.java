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

package com.github.greenfinger.cluster.remote;

import java.util.List;
import com.github.greenfinger.service.DeleteReport;

/**
 * The names the two sides agree on, and the shapes that would not survive the trip. The leader
 * channel carries one request object and decodes one answer class, so two arguments become a
 * record, and a list answer is wrapped -- a bare {@code List<T>} loses its element type.
 *
 * @Description: Ops
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
public final class Ops {

    private Ops() {}

    public static final String OVERVIEW = "ops.overview";
    public static final String CATALOG = "ops.catalog";
    public static final String CATEGORIES = "ops.categories";
    public static final String FORM = "ops.form";
    public static final String SAVE_CATALOG = "ops.saveCatalog";
    public static final String ENSURE_CATALOG = "ops.ensureCatalog";
    public static final String DELETE_CATALOG = "ops.deleteCatalog";
    public static final String VERSIONS = "ops.versions";
    public static final String REPORT = "ops.report";
    public static final String START = "ops.start";
    public static final String INTERRUPT = "ops.interrupt";
    public static final String LIVE = "ops.live";
    public static final String DELETE = "ops.delete";
    public static final String REPLAY = "ops.replay";
    public static final String SEARCH = "ops.search";
    public static final String INDEX_INFO = "ops.indexInfo";
    public static final String VECTOR_INFO = "ops.vectorInfo";

    /** An argument that is only a name, wrapped so the payload is json either way. */
    public record Ref(String idOrName) {
    }

    public record NameAndUrl(String name, String url) {
    }

    public record ReportAsk(String idOrName, Integer version) {
    }

    public record LiveAsk(String catalogId, boolean perNode) {
    }

    public record Names(List<String> values) {
    }

    public record Lines(List<DeleteReport.Line> values) {
    }

    /** A yes or no, and a sentence, which are the only two things some operations answer with. */
    public record Said(boolean value, String text) {
    }

}
