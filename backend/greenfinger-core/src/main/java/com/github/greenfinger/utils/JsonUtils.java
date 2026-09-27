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

package com.github.greenfinger.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;

/**
 * The one mapper greenfinger's own data is written and read with.
 *
 * <p>
 * There were twenty of these, each {@code new ObjectMapper()} in a field, and every one of them
 * was a separate answer to "what does a date look like on disk". One instance, and it is a shared
 * one rather than a bean on purpose: most of the places that need it -- the channels, the
 * replicated stores, the frontier, the output channels -- are constructed by hand and never see
 * the container.
 *
 * <p>
 * <b>Not the web application's mapper.</b> Spring Boot configures that one to write dates as
 * strings, and a report file on disk carries {@code "startTime": 1790417150650} that something
 * later reads back as a number. Sharing the web's would change the format of every file already
 * written. The api keeps its own for http, which is the customisation this project has, and this
 * is the one for everything that is stored.
 *
 * <p>
 * A mapper is thread safe once configured, which is what makes one instance enough.
 *
 * @Description: JsonUtils
 * @Author: Fred Feng
 * @Date: 27/09/2026
 * @Version 2.0.0
 */
@UtilityClass
public class JsonUtils {

    /**
     * Configured exactly as the twenty it replaces were, so nothing already on disk reads
     * differently: Jackson's defaults, and dates as the numbers they have always been.
     *
     * <p>
     * One addition, and it is the one that keeps the format able to grow: a field that a newer
     * version wrote and this one does not know is skipped rather than refused. Every one of the
     * twenty that read a file needed that, and the ones that forgot it were the bugs.
     */
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

}
